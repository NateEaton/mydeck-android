package com.mydeck.app.ui.detail

import com.mydeck.app.ui.theme.ReaderThemePalette
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewThemeBridgeTest {

    @Test
    fun `applyTheme generates javascript to update css variables`() {
        val palette = ReaderThemePalette(
            accentColor = "#111111",
            accentContainerColor = "#222222",
            accentUnderlineColor = "rgba(17, 17, 17, 0.55)",
            onAccentColor = "#FFFFFF",
            onAccentContainerColor = "#EEEEEE",
            bodyColor = "#333333",
            bodyBackgroundColor = "#444444",
            blockquoteBackgroundColor = "#555555",
            codeBackgroundColor = "#666666",
            tableBorderColor = "#777777",
            inputColor = "#888888",
            inputBackgroundColor = "#999999",
            inputBorderColor = "#AAAAAA",
            linkColor = "#BBBBBB",
            linkVisitedColor = "#CCCCCC",
            linkHoverColor = "#DDDDDD",
            buttonBorderColor = "#EEEEEE",
            annotationActiveOutlineColor = "rgba(1, 2, 3, 0.7)",
            annotationActiveShadowColor = "rgba(4, 5, 6, 0.2)",
            annotationYellowColor = "rgba(7, 8, 9, 0.3)",
            annotationRedColor = "rgba(10, 11, 12, 0.3)",
            annotationBlueColor = "rgba(13, 14, 15, 0.3)",
            annotationGreenColor = "rgba(16, 17, 18, 0.3)",
            annotationNoneUnderlineColor = "rgba(19, 20, 21, 0.4)"
        )

        val js = WebViewThemeBridge.applyTheme(palette)

        assertTrue(js.contains("(function() {"))
        assertTrue(js.contains("document.documentElement.style.setProperty('--body-color', '#333333');"))
        assertTrue(js.contains("document.documentElement.style.setProperty('--body-bg', '#444444');"))
        assertTrue(js.contains("document.documentElement.style.setProperty('--accent-color', '#111111');"))
        assertTrue(js.contains("document.documentElement.style.setProperty('--annotation-active-outline-color', 'rgba(1, 2, 3, 0.7)');"))
        assertTrue(js.contains("})();"))
    }
}
