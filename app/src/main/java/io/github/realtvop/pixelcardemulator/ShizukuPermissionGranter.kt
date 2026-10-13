package io.github.realtvop.pixelcardemulator

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class ShizukuPermissionGranter(
    context: Context,
    private val onProgress: () -> Unit,
    private val onGranted: () -> Unit,
    private val onError: (Int) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private var waitingForPermission = false
    private var binding = false

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(appContext.packageName, PrivilegeUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("permission")
        .tag("secure-settings-grant")
        .version(1)

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != RequestCode || !waitingForPermission) return@OnRequestPermissionResultListener
            waitingForPermission = false
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindAndGrant()
            } else {
                onError(R.string.shizuku_permission_denied)
            }
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            if (binder?.pingBinder() != true) {
                binding = false
                onError(R.string.shizuku_grant_failed)
                return
            }
            executor.execute {
                val result = runCatching {
                    IPrivilegeService.Stub.asInterface(binder).grantSecureSettings(appContext.packageName)
                }
                binding = false
                runCatching { Shizuku.unbindUserService(serviceArgs, this, true) }
                appContext.mainExecutor.execute {
                    if (result.getOrNull() == 0) {
                        onGranted()
                    } else {
                        onError(R.string.shizuku_grant_failed)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (binding) {
                binding = false
                onError(R.string.shizuku_grant_failed)
            }
        }
    }

    init {
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun grant() {
        if (waitingForPermission || binding) return
        onProgress()
        if (!Shizuku.pingBinder()) {
            onError(R.string.shizuku_not_running)
            return
        }
        if (Shizuku.isPreV11()) {
            onError(R.string.shizuku_too_old)
            return
        }
        try {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bindAndGrant()
                Shizuku.shouldShowRequestPermissionRationale() ->
                    onError(R.string.shizuku_permission_denied)
                else -> {
                    waitingForPermission = true
                    Shizuku.requestPermission(RequestCode)
                }
            }
        } catch (_: RuntimeException) {
            waitingForPermission = false
            onError(R.string.shizuku_not_running)
        }
    }

    override fun close() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        if (binding) runCatching { Shizuku.unbindUserService(serviceArgs, serviceConnection, true) }
        executor.shutdownNow()
    }

    private fun bindAndGrant() {
        try {
            if (Shizuku.checkRemotePermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                onError(R.string.shizuku_missing_privilege)
                return
            }
            binding = true
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        } catch (_: RemoteException) {
            binding = false
            onError(R.string.shizuku_grant_failed)
        } catch (_: RuntimeException) {
            binding = false
            onError(R.string.shizuku_grant_failed)
        }
    }

    private companion object {
        const val RequestCode = 40_100
    }
}
