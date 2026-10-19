package io.github.realtvop.pixelcardemulator

/** Identity requests only: none of these profiles implements Classic authentication or memory. */
enum class CardProfile(val storageId: String) {
    Default("default"),
    Sak08("sak08");

    internal fun oemProfile(): RawProfile = if (this == Sak08) RawProfile.Sak08 else RawProfile.Pixel

    internal fun rawProfile(isGoogle: Boolean): RawProfile = when {
        this == Sak08 -> RawProfile.Sak08
        isGoogle -> RawProfile.Pixel
        else -> RawProfile.CtsType4A
    }

    companion object {
        // Older cards have no profile field. Unknown future values keep the upstream default.
        fun fromStorage(value: String?): CardProfile = entries.find { it.storageId == value } ?: Default
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
    Sak08(0x04, 0x00, 0x08, 0x00);

    fun requestDescription(): String = "SDD=%02X  Platform=%02X  SAK=%02X  TB1=%02X".format(
        bitFrameSdd.toInt() and 0xff, platformConfig.toInt() and 0xff,
        selInfo.toInt() and 0xff, rats.toInt() and 0xff,
    )
}

internal fun hasRandomUidPrefix(uid: ByteArray): Boolean = uid.size == 4 && uid[0] == 0x08.toByte()

internal fun prependDiagnostic(entry: String, history: String): String =
    (listOf(entry) + history.split("\n\n").filter(String::isNotBlank)).take(3).joinToString("\n\n")
