package com.mydeck.app.ui.detail

import com.mydeck.app.ui.theme.ReaderThemePalette
import com.mydeck.app.ui.theme.toCssVariables

object WebViewThemeBridge {

    fun applyTheme(palette: ReaderThemePalette): String {
        val cssUpdates = palette.toCssVariables().entries.joinToString("\n") { (name, value) ->
            """document.documentElement.style.setProperty('$name', '$value');"""
        }

        return """
            (function() {
                if (!document.documentElement) return;
                $cssUpdates
            })();
        """.trimIndent()
    }
}
