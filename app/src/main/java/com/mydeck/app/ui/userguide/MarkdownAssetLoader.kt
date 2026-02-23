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

@Singleton
class MarkdownAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val ASSETS_BASE_PATH = "guide"
        private const val DEFAULT_LOCALE = "en"
        val DEFAULT_SECTIONS = listOf(
            GuideSection("Getting Started", "getting-started.md", 0),
            GuideSection("Your Bookmarks",  "your-bookmarks.md",  1),
            GuideSection("Reading",         "reading.md",         2),
            GuideSection("Organising",      "organising.md",      3),
            GuideSection("Settings",        "settings.md",        4)
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
