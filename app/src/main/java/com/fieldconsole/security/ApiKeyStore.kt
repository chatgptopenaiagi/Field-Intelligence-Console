package com.fieldconsole.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ApiKeyStore — secure storage for optional API keys.
 *
 * Keys are encrypted with AES-256-GCM using a master key held in the
 * Android Keystore system (hardware-backed where available). Encrypted
 * ciphertext and IV are stored in a private SharedPreferences file.
 *
 * No key material ever leaves the Keystore; only ciphertext is persisted.
 *
 * Usage:
 * ```kotlin
 * store.saveKey(ApiProvider.OPENAI, "sk-...")
 * val key: String? = store.getKey(ApiProvider.OPENAI)
 * store.deleteKey(ApiProvider.OPENAI)
 * store.hasKey(ApiProvider.OPENAI)   // → Boolean
 * ```
 */
@Singleton
class ApiKeyStore @Inject constructor(private val context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS  = "FieldConsoleApiKeyAlias"
        private const val ANDROID_KS      = "AndroidKeyStore"
        private const val TRANSFORMATION  = "AES/GCM/NoPadding"
        private const val PREF_FILE       = "fic_secure_prefs"
        private const val IV_SUFFIX       = "_iv"
        private const val GCM_TAG_LENGTH  = 128
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    // ── Key management ────────────────────────────────────────────────────────

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return (ks.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Encrypt and persist [rawKey] for the given [provider]. */
    fun saveKey(provider: ApiProvider, rawKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv        = cipher.iv
        val encrypted = cipher.doFinal(rawKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(provider.prefKey,            Base64.encodeToString(encrypted, Base64.DEFAULT))
            .putString(provider.prefKey + IV_SUFFIX, Base64.encodeToString(iv,        Base64.DEFAULT))
            .apply()
    }

    enum class Status { MISSING, AVAILABLE, UNREADABLE }

    /** Unreadable credentials remain stored for deliberate replacement/removal. */
    fun status(provider: ApiProvider): Status = if (!hasKey(provider)) Status.MISSING
        else if (getKey(provider) == null) Status.UNREADABLE else Status.AVAILABLE

    /** Never regenerate a missing decryption key or expose cryptographic diagnostics. */
    fun getKey(provider: ApiProvider): String? = try { decrypt(provider) } catch (_: Exception) { null }

    private fun decrypt(provider: ApiProvider): String? {
        val encB64 = prefs.getString(provider.prefKey, null) ?: return null
        val ivB64 = prefs.getString(provider.prefKey + IV_SUFFIX, null) ?: return null
        val enc    = Base64.decode(encB64, Base64.DEFAULT)
        val iv     = Base64.decode(ivB64,  Base64.DEFAULT)
        require(iv.size == 12 && enc.size >= 16)
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        val key = (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(enc), Charsets.UTF_8)
    }

    /** Remove the stored key for [provider]. */
    fun deleteKey(provider: ApiProvider) {
        prefs.edit()
            .remove(provider.prefKey)
            .remove(provider.prefKey + IV_SUFFIX)
            .apply()
    }

    /** Returns true if a key is stored for [provider]. */
    fun hasKey(provider: ApiProvider): Boolean = prefs.contains(provider.prefKey) || prefs.contains(provider.prefKey + IV_SUFFIX)

    /** Returns the set of providers that currently have a stored key. */
    fun configuredProviders(): Set<ApiProvider> =
        ApiProvider.entries.filter { hasKey(it) }.toSet()
}
