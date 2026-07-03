package com.mydeck.app.ui.userguide

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class GuideSection(
    val title: String,
    val fileName: String,
    val order: Int
)

/**
 * A single guide page parsed for offline search: its section metadata, the list
 * of headings it contains, and its body as plain (markdown-stripped) text.
 */
data class GuideSearchDoc(
    val section: GuideSection,
    val headings: List<String>,
    val body: String
)

@Singleton
class MarkdownAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Cached, lazily-built search index (one entry per section). Cleared per
    // process; guide content is bundled, so it never changes at runtime.
    @Volatile
    private var searchDocsCache: List<GuideSearchDoc>? = null

    companion object {
        private const val ASSETS_BASE_PATH = "guide"
        private const val DEFAULT_LOCALE = "en"
        // Single source of truth for the in-app table of contents.
        // index.md (used by the website) is kept in sync with this list by hand.
        val DEFAULT_SECTIONS = listOf(
            GuideSection("Getting Started", "getting-started.md", 0),
            GuideSection("Your Bookmarks",  "your-bookmarks.md",  1),
            GuideSection("Reading",         "reading.md",         2),
            GuideSection("Organizing",      "organizing.md",      3),
            GuideSection("Offline Reading", "offline-reading.md", 4),
            GuideSection("Labels",          "labels.md",          5),
            GuideSection("Highlights",      "highlights.md",      6),
            GuideSection("Collections",     "collections.md",     7),
            GuideSection("Settings",        "settings.md",        8)
        )
    }

    private fun getLocalePath(): String {
        val currentLocale = Locale.getDefault().language
        val supportedLocales = listOf("en", "de", "es", "fr", "gl", "pl", "pt", "ru", "uk", "zh")
        val locale = if (currentLocale in supportedLocales) currentLocale else DEFAULT_LOCALE
        return "$ASSETS_BASE_PATH/$locale"
    }
    
    fun loadSections(): List<GuideSection> {
        return DEFAULT_SECTIONS
    }
    
    fun loadMarkdown(fileName: String): String {
        val localePath = getLocalePath()
        return try {
            val raw = context.assets.open("$localePath/$fileName").use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
            raw
                .replace(Regex("^---[\\s\\S]*?---\\n*"), "")
                .replace(Regex("^# [^\n]+\\n+"), "")
                .replace("(./img/", "(file:///android_asset/$localePath/img/")
                .replace(Regex("""\[([^\]]+)\]\(readeck-instance://[^)]+\)"""), "$1")
        } catch (e: IOException) {
            "# Error Loading Content\n\nUnable to load: $fileName\n\nPath: $localePath/$fileName\n\nError: ${e.message}"
        }
    }
    
    /**
     * Build (and cache) a plain-text search index over every section. Headings
     * are kept separately so callers can rank heading matches above body matches.
     */
    fun loadSearchDocs(): List<GuideSearchDoc> {
        searchDocsCache?.let { return it }
        val localePath = getLocalePath()
        val docs = loadSections().map { section ->
            val raw = try {
                context.assets.open("$localePath/${section.fileName}").use { input ->
                    input.bufferedReader().use { it.readText() }
                }
            } catch (e: IOException) {
                ""
            }
            val withoutFrontmatter = raw.replace(Regex("^---[\\s\\S]*?---\\n*"), "")
            val headings = Regex("(?m)^#{1,6}\\s+(.+)$")
                .findAll(withoutFrontmatter)
                .map { it.groupValues[1].trim() }
                .toList()
            GuideSearchDoc(
                section = section,
                headings = headings,
                body = toPlainText(withoutFrontmatter)
            )
        }
        searchDocsCache = docs
        return docs
    }

    /** Strip markdown syntax to plain text suitable for substring search. */
    private fun toPlainText(markdown: String): String {
        return markdown
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")        // images
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")       // links -> text
            .replace(Regex("(?m)^#{1,6}\\s+"), "")                    // heading markers
            .replace(Regex("[*_`>|]"), " ")                          // emphasis/quote/table
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }

    fun loadImage(imagePath: String): ByteArray? {
        val localePath = getLocalePath()
        return try {
            // Handle relative paths like "../img/image.webp"
            val fullPath = if (imagePath.startsWith("../")) {
                "$localePath/${imagePath.substring(3)}"
            } else if (imagePath.startsWith("./")) {
                "$localePath/${imagePath.substring(2)}"
            } else {
                "$localePath/$imagePath"
            }
            
            context.assets.open(fullPath).use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: IOException) {
            null
        }
    }
}
