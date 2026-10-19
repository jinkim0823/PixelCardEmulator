package io.github.realtvop.pixelcardemulator

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Build
import java.lang.reflect.Method

open class NfcEmulationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ExperimentalRawConsentRequiredException(message: String) : NfcEmulationException(message)

private const val CoordinatorPreferences = "nfc_emulation_state"
private const val ActiveBackendKey = "active_backend"
private const val ExperimentalFingerprintKey = "experimental_raw_fingerprint"
private const val ForceRawKey = "force_raw"
private const val RestartDiscoveryAfterSwitchKey = "restart_discovery_after_switch"
internal const val NfcFieldResetDelayMillis = 250L

internal interface NfcCardEmulationBackend {
    val isSupported: Boolean
    fun activate(uid: ByteArray, cardProfile: CardProfile)
    fun deactivate()
}

/** Android 15+'s system-service implementation of NFC-A identity emulation. */
private class OemNfcCardEmulator(
    private val context: Context,
    private val shouldRestartDiscovery: () -> Boolean,
) : NfcCardEmulationBackend {
    private val adapter: NfcAdapter? = context.getSystemService(NfcManager::class.java)?.defaultAdapter
    private val pollingControl = PollingControl(context, adapter)

    override val isSupported: Boolean
        get() = runCatching { resolveApi(requireEnabled = false) }.isSuccess

    override fun activate(uid: ByteArray, cardProfile: CardProfile) {
        require(uid.size in SupportedUidSizes) { "Unsupported UID length" }
        val api = resolveApi()
        val profile = cardProfile.oemProfile()
        withOptionalDiscoveryRestart {
            invoke(
                api,
                enabled = true,
                bitFrameSdd = profile.bitFrameSdd.toInt(),
                platformConfig = profile.platformConfig.toInt(),
                selInfo = profile.selInfo.toInt(),
                nfcid1 = uid,
                rats = profile.rats.toInt(),
            )
        }
    }

    override fun deactivate() {
        val api = resolveApi()
        withOptionalDiscoveryRestart {
            invoke(
                api,
                enabled = false,
                bitFrameSdd = 0x00,
                platformConfig = 0x00,
                selInfo = 0x00,
                nfcid1 = ByteArray(0),
                rats = 0x00,
            )
        }
    }

    fun setActivity(activity: android.app.Activity?) {
        pollingControl.setActivity(activity)
    }

    private fun <T> withOptionalDiscoveryRestart(block: () -> T): T =
        if (shouldRestartDiscovery()) {
            pollingControl.withBestEffortPause(NfcFieldResetDelayMillis, block)
        } else {
            block()
        }

    private fun resolveApi(requireEnabled: Boolean = true): Api {
        val nfcAdapter = adapter ?: throw NfcEmulationException(context.getString(R.string.nfc_unavailable))
        if (requireEnabled && !nfcAdapter.isEnabled) {
            throw NfcEmulationException(context.getString(R.string.nfc_turn_on_to_activate))
        }

        try {
            val getExtension = findMethod(NfcAdapter::class.java, "getNfcOemExtension")
            val extension = getExtension.invoke(nfcAdapter)
                ?: throw NfcEmulationException(context.getString(R.string.oem_extension_unavailable))
            val emulate = findMethod(
                extension.javaClass,
                "emulateNfcTechnologyATag",
                Boolean::class.javaPrimitiveType!!,
                Byte::class.javaPrimitiveType!!,
                Byte::class.javaPrimitiveType!!,
                Byte::class.javaPrimitiveType!!,
                ByteArray::class.java,
                Byte::class.javaPrimitiveType!!,
                ByteArray::class.java,
            )
            return Api(extension, emulate)
        } catch (error: NfcEmulationException) {
            throw error
        } catch (error: Throwable) {
            throw NfcEmulationException(context.getString(R.string.controller_no_uid_emulation), error)
        }
    }

    private fun invoke(
        api: Api,
        enabled: Boolean,
        bitFrameSdd: Int,
        platformConfig: Int,
        selInfo: Int,
        nfcid1: ByteArray,
        rats: Int,
    ) {
        val status = try {
            api.emulate.invoke(
                api.extension,
                enabled,
                bitFrameSdd.toByte(),
                platformConfig.toByte(),
                selInfo.toByte(),
                nfcid1.copyOf(),
                rats.toByte(),
                null,
            ) as? Int
        } catch (error: Throwable) {
            throw NfcEmulationException(context.getString(R.string.oem_service_rejected), error)
        }

        when (status) {
            0 -> Unit
            1 -> throw NfcEmulationException(context.getString(R.string.nfc_turn_on_to_activate))
            2 -> throw NfcEmulationException(context.getString(R.string.oem_internal_failure))
            null -> throw NfcEmulationException(context.getString(R.string.oem_service_no_status))
            else -> throw NfcEmulationException(context.getString(R.string.oem_unknown_status, status))
        }
    }

    private data class Api(val extension: Any, val emulate: Method)
}

/** Chooses the official system route first and retains the chosen backend until restore. */
class NfcEmulationCoordinator(private val context: Context) {
    private val preferences = context.getSharedPreferences(CoordinatorPreferences, Context.MODE_PRIVATE)
    private val oem = OemNfcCardEmulator(context) { restartDiscoveryAfterSwitch }
    private val raw = RawNciCardEmulator(context) { restartDiscoveryAfterSwitch }

    val isSupported: Boolean
        get() = oem.isSupported || raw.isSupported

    val hasPendingRestore: Boolean
        get() = preferences.contains(ActiveBackendKey) || raw.hasPendingRestore

    var forceRaw: Boolean
        get() = preferences.getBoolean(ForceRawKey, false)
        set(value) {
            check(preferences.edit().putBoolean(ForceRawKey, value).commit()) {
                "Could not save raw NFC preference"
            }
        }

    var restartDiscoveryAfterSwitch: Boolean
        get() = preferences.getBoolean(RestartDiscoveryAfterSwitchKey, true)
        set(value) {
            check(preferences.edit().putBoolean(RestartDiscoveryAfterSwitchKey, value).commit()) {
                "Could not save NFC discovery restart preference"
            }
        }

    val requiresExperimentalRawConsent: Boolean
        get() = raw.isSupported && (!oem.isSupported || forceRaw) && !RawNfcDeviceCatalog.isAllowlisted &&
            preferences.getString(ExperimentalFingerprintKey, null) != Build.FINGERPRINT

    fun setActivity(activity: android.app.Activity?) {
        oem.setActivity(activity)
        raw.setActivity(activity)
    }

    fun allowExperimentalRawForCurrentFirmware() {
        check(
            preferences.edit()
                .putString(ExperimentalFingerprintKey, Build.FINGERPRINT)
                .commit(),
        ) { "Could not save raw NFC consent" }
    }

    val diagnostics: String
        get() = preferences.getString("diagnostic_history", "").orEmpty()

    private fun recordDiagnostic(message: String) {
        val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
        val entry = "$time • $message"
        preferences.edit().putString("diagnostic_history", prependDiagnostic(entry, diagnostics)).apply()
    }

    fun readControllerConfiguration(expectedUid: ByteArray?) {
        try {
            recordDiagnostic(raw.readControllerIdentity(expectedUid))
        } catch (error: Throwable) {
            recordDiagnostic("NCI READ ONLY failed: ${error.message ?: error.javaClass.simpleName}\nNo settings changed; RF not measured")
        }
    }

    fun activate(uid: ByteArray, cardProfile: CardProfile = CardProfile.Default) {
        val backendName = preferences.getString(ActiveBackendKey, null)
        val (name, backend) = when (backendName) {
            "oem" -> "oem" to oem
            "raw" -> "raw" to raw
            else -> selectBackend()
        }
        check(preferences.edit().putString(ActiveBackendKey, name).commit()) {
            context.getString(R.string.recovery_route_save_failed)
        }
        val profile = if (name == "oem") cardProfile.oemProfile()
            else cardProfile.rawProfile(android.os.Build.MANUFACTURER.equals("google", ignoreCase = true))
        val request = "${name.uppercase()} • ${cardProfile.storageId} • UID ${uid.size} bytes\n${profile.requestDescription()}"
        try {
            backend.activate(uid, cardProfile)
            val result = if (name == "oem") "API accepted (status 0); no controller readback"
                else if (cardProfile == CardProfile.Sak08) "NCI accepted; profile + UID readback matched"
                else "NCI accepted; UID readback matched (other fields unverified)"
            // OnePlus may keep its existing profile when its UID-only fast path succeeds.
            val detail = if (name == "raw" && cardProfile == CardProfile.Default && RawNfcDeviceCatalog.prefersUidOnly)
                "\nDefault raw may use UID-only; profile values above are fallback requests." else ""
            recordDiagnostic("$request\n$result$detail\nRF not measured")
        } catch (error: Throwable) {
            recordDiagnostic("$request\nFAILED: ${error.message ?: error.javaClass.simpleName}\nRestore may be required; RF not measured")
            throw error
        }
    }

    fun deactivate() {
        val backend = when (preferences.getString(ActiveBackendKey, null)) {
            "raw" -> raw
            "oem" -> oem
            else -> if (raw.hasPendingRestore) raw else oem
        }
        val name = preferences.getString(ActiveBackendKey, null) ?: if (raw.hasPendingRestore) "raw" else "oem"
        try {
            backend.deactivate()
            check(preferences.edit().remove(ActiveBackendKey).commit()) {
                context.getString(R.string.recovery_route_clear_failed)
            }
            recordDiagnostic("${name.uppercase()} • OFF request completed\nRF / full restoration not measured")
        } catch (error: Throwable) {
            recordDiagnostic("${name.uppercase()} • OFF FAILED: ${error.message ?: error.javaClass.simpleName}\nRestore pending; RF not measured")
            throw error
        }
    }

    private fun selectBackend(): Pair<String, NfcCardEmulationBackend> {
        if (raw.isSupported && RawNfcDeviceCatalog.isAllowlisted && (!oem.isSupported || forceRaw)) {
            return "raw" to raw
        }
        if (oem.isSupported && !forceRaw) return "oem" to oem
        if (!raw.isSupported) throw NfcEmulationException(context.getString(R.string.uid_emulation_unsupported))
        if (!RawNfcDeviceCatalog.isAllowlisted && requiresExperimentalRawConsent) {
            throw ExperimentalRawConsentRequiredException(context.getString(R.string.raw_consent_required))
        }
        return "raw" to raw
    }
}

internal fun findMethod(owner: Class<*>, name: String, vararg parameters: Class<*>): Method {
    return try {
        owner.getMethod(name, *parameters)
    } catch (_: NoSuchMethodException) {
        owner.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
    }
}
