package com.mydeck.app.ui.detail

object WebViewActionsInjector {

    // Material outlined favorite_border (24dp) and filled favorite (24dp).
    // Embedded as inline SVG strings so we can drop them straight into the
    // injected HTML and the JS state-update path. Single quotes are used
    // throughout so they are safe to embed inside JS double-quoted strings
    // and Kotlin triple-quoted raw strings.
    const val FAV_ICON_OUTLINE: String =
        "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' fill='currentColor'><path d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35zm-.55-2.84l.55.5.55-.5C17.6 14.24 20 11.39 20 8.5 20 6.5 18.5 5 16.5 5c-1.54 0-3.04.99-3.57 2.36h-1.87C10.54 5.99 9.04 5 7.5 5 5.5 5 4 6.5 4 8.5c0 2.89 2.4 5.74 7.45 10.01z'/></svg>"

    const val FAV_ICON_FILLED: String =
        "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' fill='currentColor'><path d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z'/></svg>"

    // Material outlined inventory_2 (24dp) and filled inventory_2 (24dp).
    const val ARC_ICON_OUTLINE: String =
        "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' fill='currentColor'><path d='M20 2H4C3 2 2 2.9 2 4v3.01C2 7.73 2.43 8.35 3 8.7V20c0 1.1 1.1 2 2 2h14c.9 0 2-.9 2-2V8.7c.57-.35 1-.97 1-1.69V4c0-1.1-1-2-2-2zm-1 18H5V9h14v11zm1-13H4V4l16-.02V7zM9 12h6v2H9z'/></svg>"

    const val ARC_ICON_FILLED: String =
        "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' fill='currentColor'><path d='M20 2H4C3 2 2 2.9 2 4v3.01C2 7.73 2.43 8.35 3 8.7V20c0 1.1 1.1 2 2 2h14c.9 0 2-.9 2-2V8.7c.57-.35 1-.97 1-1.69V4c0-1.1-1-2-2-2zM15 14H9v-2h6v2z'/></svg>"

    fun injectActions(): String = """
        (function() {
            if (window.MyDeckActions) return;
            var FAV_ICON_OUTLINE = "$FAV_ICON_OUTLINE";
            var FAV_ICON_FILLED = "$FAV_ICON_FILLED";
            var ARC_ICON_OUTLINE = "$ARC_ICON_OUTLINE";
            var ARC_ICON_FILLED = "$ARC_ICON_FILLED";
            window.MyDeckActions = {
                toggleFavorite: function() {
                    if (window.${WebViewActionsBridge.BRIDGE_NAME}) {
                        window.${WebViewActionsBridge.BRIDGE_NAME}.toggleFavorite();
                    }
                },
                toggleArchive: function() {
                    if (window.${WebViewActionsBridge.BRIDGE_NAME}) {
                        window.${WebViewActionsBridge.BRIDGE_NAME}.toggleArchive();
                    }
                },
                setFavoriteState: function(active, label) {
                    var btn = document.getElementById('mydeck-favorite-btn');
                    if (!btn) return;
                    btn.setAttribute('data-active', active ? 'true' : 'false');
                    var labelEl = btn.querySelector('.mydeck-action-label');
                    if (labelEl) labelEl.textContent = label;
                    var iconEl = btn.querySelector('.mydeck-action-icon');
                    if (iconEl) iconEl.innerHTML = active ? FAV_ICON_FILLED : FAV_ICON_OUTLINE;
                },
                setArchiveState: function(active, label) {
                    var btn = document.getElementById('mydeck-archive-btn');
                    if (!btn) return;
                    btn.setAttribute('data-active', active ? 'true' : 'false');
                    var labelEl = btn.querySelector('.mydeck-action-label');
                    if (labelEl) labelEl.textContent = label;
                    var iconEl = btn.querySelector('.mydeck-action-icon');
                    if (iconEl) iconEl.innerHTML = active ? ARC_ICON_FILLED : ARC_ICON_OUTLINE;
                }
            };
        })();
    """.trimIndent()

    fun setFavoriteStateScript(isFavorite: Boolean, label: String): String {
        val safeLabel = escapeJsString(label)
        return "if (window.MyDeckActions) window.MyDeckActions.setFavoriteState($isFavorite, '$safeLabel');"
    }

    fun setArchiveStateScript(isArchived: Boolean, label: String): String {
        val safeLabel = escapeJsString(label)
        return "if (window.MyDeckActions) window.MyDeckActions.setArchiveState($isArchived, '$safeLabel');"
    }

    private fun escapeJsString(s: String): String =
        s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", " ")
}
