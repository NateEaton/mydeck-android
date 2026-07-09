package com.mydeck.app.ui.whatsnew

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsNewAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val ASSETS_BASE_PATH = "whatsnew"
        private const val DEFAULT_LOCALE = "en"
        private val SUPPORTED_LOCALES =
            listOf("en", "de", "es", "fr", "gl", "pl", "pt", "ru", "uk", "zh")
        private const val SNAPSHOT_SUFFIX = "-snapshot"

        /**
         * Snapshot builds append "-snapshot" to the release versionName (see
         * `SNAPSHOT_VERSION_NAME` handling in app/build.gradle.kts). What's New
         * content is authored against the release version string, so strip that
         * suffix before comparing/looking up notes — this lets a snapshot build
         * preview the upcoming release's notes rather than never matching.
         */
        fun normalizeVersion(versionName: String): String = versionName.removeSuffix(SNAPSHOT_SUFFIX)
    }

    private fun getLocalePath(): String {
        val currentLocale = Locale.getDefault().language
        val locale = if (currentLocale in SUPPORTED_LOCALES) currentLocale else DEFAULT_LOCALE
        return "$ASSETS_BASE_PATH/$locale"
    }

    /**
     * Loads the notes for [version], or null if no notes exist for that exact
     * version — the expected outcome for rc/snapshot builds, not an error.
     */
    fun loadNotesForVersion(version: String): String? {
        val localePath = getLocalePath()
        return try {
            val raw = context.assets.open("$localePath/$version.md").use { inputStream ->
                inputStream.bufferedReader().use { reader -> reader.readText() }
            }
            raw
                .replace(Regex("^---[\\s\\S]*?---\\n*"), "")
                .replace(Regex("^# [^\n]+\\n+"), "")
        } catch (e: IOException) {
            null
        }
    }
}
