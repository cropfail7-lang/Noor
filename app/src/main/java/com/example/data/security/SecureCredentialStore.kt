package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class VaultSecretEntry(
    val label: String,
    val maskedPreview: String,
    val updatedAt: Long
)

/**
 * Hardware-backed Android Keystore AES-256-GCM local credential vault.
 * Secrets never leave the device and are never transmitted to AI models.
 */
class SecureCredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveSecret(label: String, rawValue: String): Boolean {
        val cleanLabel = label.trim()
        if (cleanLabel.isBlank() || rawValue.isBlank()) return false
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(rawValue.toByteArray(StandardCharsets.UTF_8))
            val payload = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP) + ":" +
                System.currentTimeMillis()
            prefs.edit().putString(cleanLabel, payload).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt local credential", e)
            false
        }
    }

    fun readDecryptedSecret(label: String): String? {
        val payload = prefs.getString(label, null) ?: return null
        return try {
            val parts = payload.split(":")
            if (parts.size < 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt local credential", e)
            null
        }
    }

    fun listEntries(): List<VaultSecretEntry> {
        return prefs.all.mapNotNull { (key, value) ->
            val raw = value as? String ?: return@mapNotNull null
            val parts = raw.split(":")
            val ts = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            VaultSecretEntry(
                label = key,
                maskedPreview = "•••••••••••• (AES-256-GCM)",
                updatedAt = ts
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun deleteSecret(label: String) {
        prefs.edit().remove(label).apply()
    }

    companion object {
        private const val TAG = "SecureCredentialStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "noor_ai_vault_aes_key_v1"
        private const val PREFS_NAME = "noor_encrypted_vault"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
