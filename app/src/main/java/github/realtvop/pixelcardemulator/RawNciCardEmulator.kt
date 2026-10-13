package github.realtvop.pixelcardemulator

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Build
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val RawPreferences = "raw_nfc_state"
private const val RawRestorePendingKey = "restore_pending"
private const val RawSnapshotKey = "original_configuration"
private const val ResponseTimeoutSeconds = 4L
private const val PollingPauseMillis = 40_000L

private const val CoreGroup = 0
private const val MessageTypeCommand = 1
private const val CoreSetConfig = 0x02
private const val CoreGetConfig = 0x03

private const val NfccConfigControl = 0x85
private const val LaBitFrameSdd = 0x30
private const val LaPlatformConfig = 0x31
private const val LaSelInfo = 0x32
private const val LaNfcid1 = 0x33
private const val LiARatsTb1 = 0x58
private const val LiAHistoricalBytes = 0x59

private val SnapshotParameters = listOf(
    NfccConfigControl,
    LaBitFrameSdd,
    LaPlatformConfig,
    LaSelInfo,
    LaNfcid1,
    LiARatsTb1,
    LiAHistoricalBytes,
)
private val RequiredSnapshotParameters = SnapshotParameters - LiAHistoricalBytes

internal object RawNfcDeviceCatalog {
    private val manufacturer = Build.MANUFACTURER.lowercase()
    private val model = Build.MODEL.uppercase()

    val isAllowlisted: Boolean
        get() = manufacturer in setOf("google", "samsung", "oneplus") ||
            (manufacturer == "sony" && model == "XQ-EC72")

    val profile: RawProfile
        get() = if (manufacturer == "google") RawProfile.Pixel else RawProfile.CtsType4A

    val prefersDiscoveryTechnology: Boolean
        get() = Build.VERSION.SDK_INT >= 35 && manufacturer == "oneplus" && model == "CPH2747"

    val prefersUidOnly: Boolean
        get() = manufacturer == "oneplus" && model == "CPH2747"

    val allowsDiscoveryTechnologyFallback: Boolean
        get() = when (manufacturer) {
            "samsung" -> Build.VERSION.SDK_INT == 35
            "oneplus" -> Build.VERSION.SDK_INT >= 35
            "sony" -> model == "XQ-EC72" && Build.VERSION.SDK_INT >= 35
            else -> false
        }
}

internal enum class RawProfile(
    val bitFrameSdd: Byte,
    val platformConfig: Byte,
    val selInfo: Byte,
    val rats: Byte,
) {
    Pixel(0x04, 0x00, 0x00, 0x00),
    CtsType4A(0x06, 0x0C, 0x20, 0x40),
}

internal class RawNciCardEmulator(context: Context) : NfcCardEmulationBackend {
    private val preferences = context.getSharedPreferences(RawPreferences, Context.MODE_PRIVATE)
    private val adapter = context.getSystemService(NfcManager::class.java)?.defaultAdapter
    private val transport = RawNciTransport(adapter)
    private val pollingControl = PollingControl(adapter)

    override val isSupported: Boolean
        get() = transport.isSupported

    val hasPendingRestore: Boolean
        get() = preferences.getBoolean(RawRestorePendingKey, false)

    fun setActivity(activity: Activity?) {
        pollingControl.setActivity(activity)
    }

    override fun activate(uid: ByteArray) {
        require(uid.size in SupportedUidSizes) { "Unsupported UID length" }
        ensureNfcEnabled()

        val original = if (hasPendingRestore) {
            loadSnapshot()
        } else {
            pollingControl.withPaused { readCompleteSnapshot() }.also(::savePendingSnapshot)
        }
        check(original.keys.containsAll(RequiredSnapshotParameters)) { "The saved NFC snapshot is incomplete" }

        val profile = RawNfcDeviceCatalog.profile
        pollingControl.withPaused {
            setPreferredConfiguration(uid, profile)
            requireUid(uid, "The NFC controller did not retain the UID")
        }

        if (!uidMatches(uid)) {
            pollingControl.withPaused {
                setPreferredConfiguration(uid, profile)
                Thread.sleep(120)
                requireUid(uid, "The NFC controller overwrote the UID after polling resumed")
            }
            requireUid(uid, "The NFC controller overwrote the UID after replay")
        }
    }

    override fun deactivate() {
        ensureNfcEnabled()
        val snapshot = loadSnapshot().takeIf { hasPendingRestore }
        pollingControl.withPaused {
            if (snapshot != null) {
                setConfig(snapshot.entries.map { it.key to it.value })
                requireUid(snapshot.getValue(LaNfcid1), "The NFC controller did not restore its original UID")
            } else {
                setConfig(
                    listOf(
                        NfccConfigControl to byteArrayOf(0x01),
                        LaNfcid1 to ByteArray(0),
                        LiARatsTb1 to byteArrayOf(0x00),
                        LiAHistoricalBytes to ByteArray(0),
                    ),
                )
            }
        }
        if (snapshot != null && !uidMatches(snapshot.getValue(LaNfcid1))) {
            pollingControl.withPaused {
                setConfig(snapshot.entries.map { it.key to it.value })
                Thread.sleep(120)
                requireUid(snapshot.getValue(LaNfcid1), "The original UID was lost after polling resumed")
            }
            requireUid(snapshot.getValue(LaNfcid1), "The original UID was not retained after restore replay")
        }
        clearPendingSnapshot()
    }

    private fun setProfile(uid: ByteArray, profile: RawProfile) {
        setConfig(
            listOf(
                NfccConfigControl to byteArrayOf(0x00),
                LaBitFrameSdd to byteArrayOf(profile.bitFrameSdd),
                LaPlatformConfig to byteArrayOf(profile.platformConfig),
                LaSelInfo to byteArrayOf(profile.selInfo),
                LaNfcid1 to uid,
                LiARatsTb1 to byteArrayOf(profile.rats),
            ),
        )
    }

    private fun setPreferredConfiguration(uid: ByteArray, profile: RawProfile) {
        if (RawNfcDeviceCatalog.prefersUidOnly) {
            val uidOnlyWorked = runCatching {
                setConfig(listOf(LaNfcid1 to uid))
                requireUid(uid, "UID-only configuration was not retained")
            }.isSuccess
            if (uidOnlyWorked) return
        }
        setProfile(uid, profile)
    }

    private fun readCompleteSnapshot(): Map<Int, ByteArray> {
        val values = linkedMapOf<Int, ByteArray>()
        runCatching { values.putAll(getConfig(SnapshotParameters)) }
        for (parameter in SnapshotParameters) {
            if (parameter !in values) {
                runCatching { values.putAll(getConfig(listOf(parameter))) }
            }
        }
        val missing = RequiredSnapshotParameters.filterNot(values::containsKey)
        if (missing.isNotEmpty()) {
            throw NfcEmulationException(
                "Could not snapshot NFC parameters: ${missing.joinToString { "0x%02X".format(it) }}",
            )
        }
        return values
    }

    private fun setConfig(parameters: List<Pair<Int, ByteArray>>) {
        val size = 1 + parameters.sumOf { 2 + it.second.size }
        val payload = ByteArray(size)
        payload[0] = parameters.size.toByte()
        var offset = 1
        parameters.forEach { (id, value) ->
            payload[offset++] = id.toByte()
            payload[offset++] = value.size.toByte()
            value.copyInto(payload, offset)
            offset += value.size
        }
        val response = transport.command(CoreSetConfig, payload)
        val status = response.firstOrNull()?.toInt()?.and(0xFF)
            ?: throw NfcEmulationException("The NFC controller returned an empty SET_CONFIG response")
        if (status != 0) {
            val rejected = if (response.size >= 2) {
                response.drop(2).joinToString { "0x%02X".format(it.toInt() and 0xFF) }
            } else {
                "unknown parameters"
            }
            throw NfcEmulationException("NFC SET_CONFIG failed with 0x%02X ($rejected)".format(status))
        }
    }

    private fun getConfig(ids: List<Int>): Map<Int, ByteArray> {
        val request = byteArrayOf(ids.size.toByte(), *ids.map(Int::toByte).toByteArray())
        val response = transport.command(CoreGetConfig, request)
        if (response.size < 2) throw NfcEmulationException("Malformed NFC GET_CONFIG response")
        val status = response[0].toInt() and 0xFF
        if (status != 0) throw NfcEmulationException("NFC GET_CONFIG failed with 0x%02X".format(status))

        val count = response[1].toInt() and 0xFF
        var offset = 2
        return buildMap {
            repeat(count) {
                if (offset + 2 > response.size) throw NfcEmulationException("Truncated NFC configuration")
                val id = response[offset++].toInt() and 0xFF
                val length = response[offset++].toInt() and 0xFF
                if (offset + length > response.size) throw NfcEmulationException("Truncated NFC parameter 0x%02X".format(id))
                put(id, response.copyOfRange(offset, offset + length))
                offset += length
            }
        }
    }

    private fun uidMatches(uid: ByteArray): Boolean =
        runCatching { getConfig(listOf(LaNfcid1))[LaNfcid1]?.contentEquals(uid) == true }.getOrDefault(false)

    private fun requireUid(uid: ByteArray, message: String) {
        if (!uidMatches(uid)) throw NfcEmulationException(message)
    }

    private fun savePendingSnapshot(snapshot: Map<Int, ByteArray>) {
        val serialized = snapshot.entries.joinToString(";") { (id, value) ->
            "%02X=%s".format(id, value.toHex())
        }
        check(
            preferences.edit()
                .putString(RawSnapshotKey, serialized)
                .putBoolean(RawRestorePendingKey, true)
                .commit(),
        ) { "Could not save the NFC restore snapshot" }
    }

    private fun loadSnapshot(): Map<Int, ByteArray> {
        val serialized = preferences.getString(RawSnapshotKey, null) ?: return emptyMap()
        return serialized.split(';').filter(String::isNotBlank).associate { item ->
            val (id, value) = item.split('=', limit = 2)
            id.toInt(16) to value.hexToBytes()
        }
    }

    private fun clearPendingSnapshot() {
        check(
            preferences.edit()
                .remove(RawSnapshotKey)
                .putBoolean(RawRestorePendingKey, false)
                .commit(),
        ) { "Could not clear the NFC restore snapshot" }
    }

    private fun ensureNfcEnabled() {
        if (adapter == null) throw NfcEmulationException("This device does not have NFC")
        if (!adapter.isEnabled) throw NfcEmulationException("Turn on NFC before activating a card")
    }
}

private class RawNciTransport(private val adapter: NfcAdapter?) {
    private val callbackType by lazy { Class.forName("android.nfc.NfcAdapter\$NfcVendorNciCallback") }

    val isSupported: Boolean
        get() = adapter != null && runCatching {
            callbackType
            findMethod(NfcAdapter::class.java, "sendVendorNciMessage",
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, ByteArray::class.java)
            findMethod(NfcAdapter::class.java, "registerNfcVendorNciCallback", Executor::class.java, callbackType)
            findMethod(NfcAdapter::class.java, "unregisterNfcVendorNciCallback", callbackType)
        }.isSuccess

    fun command(oid: Int, payload: ByteArray): ByteArray {
        val nfcAdapter = adapter ?: throw NfcEmulationException("This device does not have NFC")
        try {
            val register = findMethod(
                NfcAdapter::class.java,
                "registerNfcVendorNciCallback",
                Executor::class.java,
                callbackType,
            )
            val unregister = findMethod(NfcAdapter::class.java, "unregisterNfcVendorNciCallback", callbackType)
            val send = findMethod(
                NfcAdapter::class.java,
                "sendVendorNciMessage",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                ByteArray::class.java,
            )
            val response = AtomicReference<ByteArray>()
            val latch = CountDownLatch(1)
            val callback = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { proxy, method, args ->
                when (method.name) {
                    "onVendorNciResponse" -> {
                        val gid = args?.getOrNull(0) as? Int
                        val responseOid = args?.getOrNull(1) as? Int
                        val bytes = args?.getOrNull(2) as? ByteArray
                        if (gid == CoreGroup && responseOid == oid && bytes != null) {
                            response.set(bytes.copyOf())
                            latch.countDown()
                        }
                        null
                    }
                    "onVendorNciNotification" -> null
                    "toString" -> "PixelCardEmulatorRawNciCallback"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }

            register.invoke(nfcAdapter, Executor(Runnable::run), callback)
            try {
                val frameworkStatus = send.invoke(
                    nfcAdapter,
                    MessageTypeCommand,
                    CoreGroup,
                    oid,
                    payload,
                ) as? Int
                if (frameworkStatus != 0) {
                    throw NfcEmulationException("NFC framework rejected the command with status $frameworkStatus")
                }
                if (!latch.await(ResponseTimeoutSeconds, TimeUnit.SECONDS)) {
                    throw NfcEmulationException("Timed out waiting for the NFC controller")
                }
                return response.get() ?: throw NfcEmulationException("The NFC controller returned no response")
            } finally {
                runCatching { unregister.invoke(nfcAdapter, callback) }
            }
        } catch (error: NfcEmulationException) {
            throw error
        } catch (error: Throwable) {
            throw NfcEmulationException("Raw NFC communication failed", error)
        }
    }
}

private class PollingControl(private val adapter: NfcAdapter?) {
    private var activity = WeakReference<Activity>(null)

    fun setActivity(value: Activity?) {
        activity = WeakReference(value)
    }

    fun <T> withPaused(block: () -> T): T {
        val resume = pause()
        return try {
            block()
        } finally {
            resume()
        }
    }

    private fun pause(): () -> Unit {
        val nfcAdapter = adapter ?: throw NfcEmulationException("This device does not have NFC")
        if (RawNfcDeviceCatalog.prefersDiscoveryTechnology) return pauseWithDiscoveryTechnology(nfcAdapter)

        runCatching {
            val extension = findMethod(NfcAdapter::class.java, "getNfcOemExtension").invoke(nfcAdapter)
                ?: error("No NFC OEM extension")
            val pause = findMethod(extension.javaClass, "pausePolling", Long::class.javaPrimitiveType!!)
            val resume = findMethod(extension.javaClass, "resumePolling")
            checkPauseStatus(pause.invoke(extension, PollingPauseMillis))
            return { checkPauseStatus(resume.invoke(extension)) }
        }

        if (RawNfcDeviceCatalog.allowsDiscoveryTechnologyFallback) {
            return pauseWithDiscoveryTechnology(nfcAdapter)
        }
        throw NfcEmulationException("No safe polling pause API is available on this device")
    }

    private fun pauseWithDiscoveryTechnology(nfcAdapter: NfcAdapter): () -> Unit {
        val foregroundActivity = activity.get()
            ?: throw NfcEmulationException("A foreground activity is required for raw NFC access")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!foregroundActivity.hasWindowFocus() && System.nanoTime() < deadline) Thread.sleep(50)
        if (!foregroundActivity.hasWindowFocus()) {
            throw NfcEmulationException("The app must be in the foreground for raw NFC access")
        }
        val set = findMethod(
            NfcAdapter::class.java,
            "setDiscoveryTechnology",
            Activity::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        )
        val reset = findMethod(NfcAdapter::class.java, "resetDiscoveryTechnology", Activity::class.java)
        set.invoke(nfcAdapter, foregroundActivity, 0, 0)
        return { reset.invoke(nfcAdapter, foregroundActivity) }
    }

    private fun checkPauseStatus(value: Any?) {
        if (value is Int && value !in setOf(1, 2)) {
            throw NfcEmulationException("NFC polling state change failed with status $value")
        }
    }
}
