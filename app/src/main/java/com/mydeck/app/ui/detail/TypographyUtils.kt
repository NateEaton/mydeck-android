package com.mydeck.app.ui.detail

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mydeck.app.domain.model.ReaderFontFamily

object TypographyUtils {
    private const val BOOKMARK_TITLE_SCALE = 1.125f

    fun getFontFamily(fontFamily: ReaderFontFamily): FontFamily {
        return when (fontFamily) {
            ReaderFontFamily.JETBRAINS_MONO -> FontFamily.Monospace
            ReaderFontFamily.NOTO_SERIF,
            ReaderFontFamily.LITERATA,
            ReaderFontFamily.SOURCE_SERIF -> FontFamily.Serif
            ReaderFontFamily.SYSTEM_DEFAULT,
            ReaderFontFamily.NOTO_SANS -> FontFamily.SansSerif
        }
    }

    fun bookmarkTitleTextStyle(typography: Typography, fontFamily: FontFamily): TextStyle {
        val baseStyle = typography.headlineSmall
        return baseStyle.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = baseStyle.fontSize * BOOKMARK_TITLE_SCALE,
            lineHeight = baseStyle.lineHeight * BOOKMARK_TITLE_SCALE
        )
    }
}
