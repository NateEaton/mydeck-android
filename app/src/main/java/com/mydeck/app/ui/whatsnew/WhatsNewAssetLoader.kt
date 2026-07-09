package com.mydeck.app.ui.whatsnew

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.LocalDate

/** A version with notes, plus the release date parsed from its frontmatter (if any). */
data class WhatsNewHistoryEntry(val version: String, val date: LocalDate?)

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

        /**
         * Compares two `X.Y.Z[-preRelease]` version strings, newest-first when
         * used with [sortedWith] directly (returns > 0 when [a] is newer than
         * [b]). Numeric-component comparison so "1.0.10" sorts after "1.0.9"
         * (a plain string sort would get this backwards); a version with no
         * pre-release suffix is newer than one with (a release supersedes its
         * own rc's), and two pre-release suffixes compare by their trailing
         * digits (so "rc10" sorts after "rc9").
         */
        fun compareVersions(a: String, b: String): Int {
            val (aMain, aPre) = splitVersion(a)
            val (bMain, bPre) = splitVersion(b)
            val maxParts = maxOf(aMain.size, bMain.size)
            for (i in 0 until maxParts) {
                val cmp = aMain.getOrElse(i) { 0 }.compareTo(bMain.getOrElse(i) { 0 })
                if (cmp != 0) return cmp
            }
            if (aPre == null && bPre != null) return 1
            if (aPre != null && bPre == null) return -1
            if (aPre == null && bPre == null) return 0
            val aPreNum = aPre.orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0
            val bPreNum = bPre.orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0
            return aPreNum.compareTo(bPreNum)
        }

        private fun splitVersion(version: String): Pair<List<Int>, String?> {
            val parts = version.split("-", limit = 2)
            val main = parts[0].split(".").map { it.toIntOrNull() ?: 0 }
            return main to parts.getOrNull(1)
        }

        /**
         * Parses an optional `date: YYYY-MM-DD` field from a leading YAML
         * frontmatter block (`--- ... ---`). Returns null if there's no
         * frontmatter, no date field, or the date fails to parse — a
         * missing/malformed date just means the history list shows no date for
         * that entry, not an error.
         */
        fun parseFrontmatterDate(raw: String): LocalDate? {
            val frontmatter = Regex("^---([\\s\\S]*?)---").find(raw)?.groupValues?.get(1)
                ?: return null
            val dateStr = Regex("""date:\s*(\S+)""").find(frontmatter)?.groupValues?.get(1)
                ?: return null
            return try {
                LocalDate.parse(dateStr)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    private fun getLocalePath(): String {
        val currentLocale = Locale.getDefault().language
        val locale = if (currentLocale in SUPPORTED_LOCALES) currentLocale else DEFAULT_LOCALE
        return "$ASSETS_BASE_PATH/$locale"
    }

    private fun readRawFile(version: String): String? {
        val localePath = getLocalePath()
        return try {
            context.assets.open("$localePath/$version.md").use { inputStream ->
                inputStream.bufferedReader().use { reader -> reader.readText() }
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Loads the notes for [version], or null if no notes exist for that exact
     * version — the expected outcome for rc/snapshot builds, not an error.
     */
    fun loadNotesForVersion(version: String): String? {
        val raw = readRawFile(version) ?: return null
        return raw
            .replace(Regex("^---[\\s\\S]*?---\\n*"), "")
            .replace(Regex("^# [^\n]+\\n+"), "")
    }

    /** All versions with notes for the resolved locale, newest first. */
    fun listAvailableVersions(): List<WhatsNewHistoryEntry> {
        val localePath = getLocalePath()
        val versions = try {
            context.assets.list(localePath)
                ?.filter { it.endsWith(".md") }
                ?.map { it.removeSuffix(".md") }
                ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
        return versions
            .sortedWith { a, b -> compareVersions(b, a) }
            .map { version -> WhatsNewHistoryEntry(version, readRawFile(version)?.let(::parseFrontmatterDate)) }
    }
}
