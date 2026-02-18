package com.mydeck.app.ui.userguide

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
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
        private const val ASSETS_PATH = "guide/en"
        private val DEFAULT_SECTIONS = listOf(
            GuideSection("Getting Started", "index.md", 0),
            GuideSection("Bookmarks", "bookmark.md", 1),
            GuideSection("Bookmark List", "bookmark-list.md", 2),
            GuideSection("Labels", "labels.md", 3)
        )
    }
    
    fun loadSections(): List<GuideSection> {
        return DEFAULT_SECTIONS
    }
    
    fun loadMarkdown(fileName: String): String {
        return try {
            val raw = context.assets.open("$ASSETS_PATH/$fileName").use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
            raw
                .replace(Regex("^---[\\s\\S]*?---\\n*"), "")
                .replace("(./img/", "(file:///android_asset/$ASSETS_PATH/img/")
                .replace(Regex("""\[([^\]]+)\]\(readeck-instance://[^)]+\)"""), "$1")
        } catch (e: IOException) {
            "# Error Loading Content\n\nUnable to load: $fileName\n\nPath: $ASSETS_PATH/$fileName\n\nError: ${e.message}"
        }
    }
    
    fun loadImage(imagePath: String): ByteArray? {
        return try {
            // Handle relative paths like "../img/image.webp"
            val fullPath = if (imagePath.startsWith("../")) {
                "$ASSETS_PATH/${imagePath.substring(3)}"
            } else if (imagePath.startsWith("./")) {
                "$ASSETS_PATH/${imagePath.substring(2)}"
            } else {
                "$ASSETS_PATH/$imagePath"
            }
            
            context.assets.open(fullPath).use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: IOException) {
            null
        }
    }
}
