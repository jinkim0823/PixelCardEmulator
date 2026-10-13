package io.github.realtvop.pixelcardemulator

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

class WirelessAdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private val entry: KeyStore.PrivateKeyEntry

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(10, TimeUnit.SECONDS)
        entry = loadOrCreateKey(context.applicationContext)
    }

    override fun getPrivateKey(): PrivateKey = entry.privateKey

    override fun getCertificate(): Certificate = entry.certificate

    override fun getDeviceName(): String = "PixelCardEmulator@${Build.MODEL}"

    private fun loadOrCreateKey(context: Context): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.PrivateKeyEntry)?.let { return it }

        val now = System.currentTimeMillis()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, AndroidKeyStore).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                    )
                    .setCertificateSubject(X500Principal("CN=${context.packageName}"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(now - TimeUnit.DAYS.toMillis(1)))
                    .setCertificateNotAfter(Date(now + TimeUnit.DAYS.toMillis(3650)))
                    .build(),
            )
            generateKeyPair()
        }
        return keyStore.getEntry(KeyAlias, null) as KeyStore.PrivateKeyEntry
    }

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val KeyAlias = "wireless_adb_identity_v1"

        @Volatile
        private var instance: WirelessAdbConnectionManager? = null

        fun getInstance(context: Context): WirelessAdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: WirelessAdbConnectionManager(context).also { instance = it }
            }
    }
}
