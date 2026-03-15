package com.mydeck.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mydeck.app.domain.model.EffectiveAppearance

val LocalEffectiveAppearance = compositionLocalOf { EffectiveAppearance.PAPER }

@Composable
fun MyDeckTheme(
    appearance: EffectiveAppearance = EffectiveAppearance.PAPER,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val colorScheme = resolveAppColorScheme(
        context = view.context,
        appearance = appearance
    )

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val backgroundColor = colorScheme.background.toArgb()
            window.statusBarColor = backgroundColor
            window.navigationBarColor = backgroundColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            val controller = WindowCompat.getInsetsController(window, view)
            val useDarkIcons = !appearance.isDark
            controller.isAppearanceLightStatusBars = useDarkIcons
            controller.isAppearanceLightNavigationBars = useDarkIcons
        }
    }

    CompositionLocalProvider(LocalEffectiveAppearance provides appearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
