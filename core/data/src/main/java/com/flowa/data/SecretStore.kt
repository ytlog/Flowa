package com.flowa.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts tokens for the atomic snapshot; reads the old preferences only during migration. */
internal class SecretStore(context: Context, namespace: String) {
    private val legacyPreferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private var cachedPlaintext: String? = null
    private var cachedCiphertext: String? = null

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("flowa.api", null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder("flowa.api", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
    }

    fun readLegacy(): String = legacyPreferences.getString("token", null)?.let(::decrypt).orEmpty()

    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 12) { "密钥数据损坏，请保留应用数据后重试。" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8).also {
            cachedPlaintext = it
            cachedCiphertext = encoded
        }
    }

    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        if (value == cachedPlaintext) cachedCiphertext?.let { return it }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP).also {
            cachedPlaintext = value
            cachedCiphertext = it
        }
    }
}
