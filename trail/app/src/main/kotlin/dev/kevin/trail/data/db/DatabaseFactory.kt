package dev.kevin.trail.data.db

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the on-device diary with SQLCipher. The passphrase is created on this
 * device and stored in Keystore-backed encrypted preferences. Robolectric
 * cannot load the native SQLCipher library, so DAO tests open an in-memory
 * Room database without this factory. Production always uses it.
 */
class DatabaseFactory(
    private val context: Context,
    private val passphrase: ByteArray,
) {
    fun create(): TrailDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, TrailDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(factory)
            .build()
    }

    companion object {
        const val DATABASE_NAME = "trail.db"
    }
}
