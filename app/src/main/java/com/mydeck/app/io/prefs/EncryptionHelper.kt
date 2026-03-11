package com.mydeck.app.io.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import timber.log.Timber

object EncryptionHelper {

    private const val PREF_FILE_NAME = "encrypted_prefs"

    fun getEncryptedSharedPreferences(context: Context): EncryptedSharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        return try {
            EncryptedSharedPreferences.create(
                PREF_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        } catch (e: Exception) {
            Timber.e(e, "EncryptedSharedPreferences corrupted, clearing and recreating")
            // Delete the corrupted preferences file and try again
            context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
            val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$PREF_FILE_NAME.xml")
            if (prefsFile.exists()) prefsFile.delete()

            EncryptedSharedPreferences.create(
                PREF_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        }
    }
}