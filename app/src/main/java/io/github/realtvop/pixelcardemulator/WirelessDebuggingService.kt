package io.github.realtvop.pixelcardemulator

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.provider.Settings
import io.github.muntashirakon.adb.android.AdbMdns
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class WirelessDebuggingService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val working = AtomicBoolean(false)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                getString(R.string.wireless_debugging_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.wireless_debugging_channel_description)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationId,
            waitingNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        if (intent?.action == ActionPair) {
            val code = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(PairingCodeKey)
                ?.toString()
                ?.trim()
            if (code?.matches(Regex("\\d{6}")) != true) {
                finish(false, getString(R.string.wireless_pairing_code_invalid))
            } else if (working.compareAndSet(false, true)) {
                notificationManager.notify(NotificationId, progressNotification())
                executor.execute { pairAndGrant(code) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun pairAndGrant(code: String) {
        var manager: WirelessAdbConnectionManager? = null
        var multicastLock: WifiManager.MulticastLock? = null
        var succeeded = false
        var result = getString(R.string.wireless_grant_failed)
        try {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
            multicastLock = wifiManager.createMulticastLock("pixelcard:wireless-adb").apply {
                setReferenceCounted(false)
                acquire()
            }
            manager = WirelessAdbConnectionManager.getInstance(this)
            val pairingEndpoint = discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 45_000)
            val pairingHost = pairingEndpoint.host.hostAddress
                ?: error(getString(R.string.wireless_service_not_found))
            manager.pair(pairingHost, pairingEndpoint.port, code)
            manager.disconnect()
            if (!manager.connectTls(this, 45_000)) {
                error(getString(R.string.wireless_connect_failed))
            }
            val command = "shell:pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}"
            manager.openStream(command).use { stream ->
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!stream.isClosed && System.nanoTime() < deadline) Thread.sleep(25)
                if (!stream.isClosed) error(getString(R.string.wireless_command_timeout))
            }
            if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                error(getString(R.string.wireless_grant_failed))
            }
            succeeded = true
            result = getString(R.string.wireless_grant_succeeded)
        } catch (error: Throwable) {
            result = error.message ?: getString(R.string.wireless_grant_failed)
        } finally {
            runCatching { manager?.disconnect() }
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
        val finalResult = result
        mainExecutor.execute { finish(succeeded, finalResult) }
    }

    private fun discover(type: String, timeoutMillis: Long): Endpoint {
        val endpoint = AtomicReference<Endpoint?>()
        val found = CountDownLatch(1)
        val discovery = AdbMdns(this, type) { host, port ->
            if (host != null && port > 0 && endpoint.compareAndSet(null, Endpoint(host, port))) {
                found.countDown()
            }
        }
        discovery.start()
        try {
            if (!found.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                error(getString(R.string.wireless_service_not_found))
            }
            return endpoint.get() ?: error(getString(R.string.wireless_service_not_found))
        } finally {
            discovery.stop()
        }
    }

    private fun waitingNotification(): Notification {
        val remoteInput = RemoteInput.Builder(PairingCodeKey)
            .setLabel(getString(R.string.wireless_pairing_code_hint))
            .build()
        val replyIntent = Intent(this, WirelessDebuggingService::class.java).setAction(ActionPair)
        val replyPendingIntent = PendingIntent.getService(
            this,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = Notification.Action.Builder(
            null,
            getString(R.string.wireless_enter_pairing_code),
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()
        return baseNotification()
            .setContentTitle(getString(R.string.wireless_notification_title))
            .setContentText(getString(R.string.wireless_notification_instructions))
            .setStyle(
                Notification.BigTextStyle()
                    .bigText(getString(R.string.wireless_notification_instructions)),
            )
            .setContentIntent(developerSettingsPendingIntent())
            .addAction(replyAction)
            .setOngoing(true)
            .build()
    }

    private fun progressNotification(): Notification = baseNotification()
        .setContentTitle(getString(R.string.wireless_notification_title))
        .setContentText(getString(R.string.wireless_pairing_in_progress))
        .setProgress(0, 0, true)
        .setOngoing(true)
        .build()

    private fun resultNotification(success: Boolean, message: String): Notification = baseNotification()
        .setContentTitle(
            getString(
                if (success) R.string.wireless_grant_succeeded_title
                else R.string.wireless_grant_failed_title,
            ),
        )
        .setContentText(message)
        .setStyle(Notification.BigTextStyle().bigText(message))
        .setContentIntent(appPendingIntent())
        .setAutoCancel(true)
        .build()

    private fun baseNotification(): Notification.Builder = Notification.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_contactless)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setOnlyAlertOnce(true)

    private fun developerSettingsPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun appPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun finish(success: Boolean, message: String) {
        working.set(false)
        stopForeground(STOP_FOREGROUND_DETACH)
        notificationManager.notify(NotificationId, resultNotification(success, message))
        stopSelf()
    }

    private data class Endpoint(val host: InetAddress, val port: Int)

    companion object {
        private const val ChannelId = "wireless_debugging_setup"
        private const val NotificationId = 41_001
        private const val PairingCodeKey = "pairing_code"
        private const val ActionBegin = "io.github.realtvop.pixelcardemulator.WIRELESS_BEGIN"
        private const val ActionPair = "io.github.realtvop.pixelcardemulator.WIRELESS_PAIR"

        fun begin(context: Context) {
            context.startForegroundService(
                Intent(context, WirelessDebuggingService::class.java).setAction(ActionBegin),
            )
        }
    }
}
