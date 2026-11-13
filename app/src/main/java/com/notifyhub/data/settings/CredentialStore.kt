package com.notifyhub.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the device credential (e.g. an API token) encrypted with an AES-256-GCM key
 * that lives in the Android Keystore. Nothing is hardcoded; the secret never appears in logs.
 */
class CredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("notifyhub_credential", Context.MODE_PRIVATE)

    fun hasCredential(): Boolean = prefs.contains(CT)

    fun save(secret: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? = runCatching {
        val iv = Base64.decode(prefs.getString(IV, null) ?: return null, Base64.NO_WRAP)
        val ct = Base64.decode(prefs.getString(CT, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "notifyhub_credential_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = "iv"
        const val CT = "ct"
    }
}
