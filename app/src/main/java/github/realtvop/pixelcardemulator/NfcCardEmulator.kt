package github.realtvop.pixelcardemulator

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import java.lang.reflect.Method

class NfcEmulationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Android 15+'s system-service implementation of NFC-A identity emulation. */
class OemNfcCardEmulator(context: Context) {
    private val adapter: NfcAdapter? = context.getSystemService(NfcManager::class.java)?.defaultAdapter

    val isSupported: Boolean
        get() = runCatching { resolveApi(requireEnabled = false) }.isSuccess

    fun activate(uid: ByteArray) {
        require(uid.size in SupportedUidSizes) { "Unsupported UID length" }
        val api = resolveApi()
        invoke(
            api,
            enabled = true,
            bitFrameSdd = 0x04,
            platformConfig = 0x00,
            selInfo = 0x00,
            nfcid1 = uid,
            rats = 0x00,
        )
    }

    fun deactivate() {
        invoke(
            resolveApi(),
            enabled = false,
            bitFrameSdd = 0x00,
            platformConfig = 0x00,
            selInfo = 0x00,
            nfcid1 = ByteArray(0),
            rats = 0x00,
        )
    }

    private fun resolveApi(requireEnabled: Boolean = true): Api {
        val nfcAdapter = adapter ?: throw NfcEmulationException("This device does not have NFC")
        if (requireEnabled && !nfcAdapter.isEnabled) {
            throw NfcEmulationException("Turn on NFC before activating a card")
        }

        try {
            val getExtension = findMethod(NfcAdapter::class.java, "getNfcOemExtension")
            val extension = getExtension.invoke(nfcAdapter)
                ?: throw NfcEmulationException("The NFC OEM extension is unavailable")
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
            throw NfcEmulationException("This NFC controller does not expose UID emulation", error)
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
            throw NfcEmulationException("The NFC system service rejected the request", error)
        }

        when (status) {
            0 -> Unit
            1 -> throw NfcEmulationException("Turn on NFC before activating a card")
            2 -> throw NfcEmulationException("The NFC controller rejected this card profile")
            null -> throw NfcEmulationException("The NFC system service returned no status")
            else -> throw NfcEmulationException("Unknown NFC OEM status $status")
        }
    }

    private data class Api(val extension: Any, val emulate: Method)
}

private fun findMethod(owner: Class<*>, name: String, vararg parameters: Class<*>): Method {
    return try {
        owner.getMethod(name, *parameters)
    } catch (_: NoSuchMethodException) {
        owner.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
    }
}
