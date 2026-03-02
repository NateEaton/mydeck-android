package com.mydeck.app.ui.detail

import androidx.compose.ui.text.font.FontFamily
import com.mydeck.app.domain.model.ReaderFontFamily

object TypographyUtils {
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
}
