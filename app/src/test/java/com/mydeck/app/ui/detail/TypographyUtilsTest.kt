package com.mydeck.app.ui.detail

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mydeck.app.ui.theme.Typography as AppTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyUtilsTest {

    @Test
    fun `bookmark title style stays above default headline small hierarchy`() {
        val style = TypographyUtils.bookmarkTitleTextStyle(
            typography = AppTypography,
            fontFamily = FontFamily.Serif
        )

        assertEquals(FontFamily.Serif, style.fontFamily)
        assertEquals(FontWeight.Medium, style.fontWeight)
        assertEquals(AppTypography.headlineSmall.fontSize * 1.125f, style.fontSize)
        assertEquals(AppTypography.headlineSmall.lineHeight * 1.125f, style.lineHeight)
        assertTrue(style.fontSize > AppTypography.headlineSmall.fontSize)
    }
}
