package dev.kevin.trail.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Keystore-backed preferences. The webhook URL, authorization header, optional
 * certificate pin, and the SQLCipher passphrase live here and nowhere else.
 * Values are never written to logcat.
 */
class SecretStore internal constructor(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(openEncrypted(context.applicationContext))

    fun getString(key: String): String? = preferences.getString(key, null)

    fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).commit()
    }

    fun getInt(key: String, default: Int): Int = preferences.getInt(key, default)

    fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).commit()
    }

    fun getBoolean(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).commit()
    }

    fun getLong(key: String, default: Long): Long = preferences.getLong(key, default)

    fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).commit()
    }

    companion object {
        const val FILE_NAME = "trail_secrets"

        private fun openEncrypted(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

class DatabasePassphrase(private val secrets: SecretStore) {
    fun getOrCreate(): ByteArray {
        val existing = secrets.getString(KEY)
        if (!existing.isNullOrBlank()) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        secrets.putString(KEY, Base64.encodeToString(fresh, Base64.NO_WRAP))
        return fresh
    }

    companion object {
        const val KEY = "db_passphrase"
        const val PASSPHRASE_BYTES = 32
    }
}
