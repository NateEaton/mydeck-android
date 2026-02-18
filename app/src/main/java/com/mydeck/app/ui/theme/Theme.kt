package com.mydeck.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.ui.theme.sepia.SepiaColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MyDeckTheme(
    theme: Theme = Theme.LIGHT,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    sepiaEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // Sepia takes priority over dynamic color when enabled for light theme
        theme == Theme.LIGHT && sepiaEnabled -> SepiaColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && theme in listOf(
            Theme.DARK,
            Theme.LIGHT
        ) -> {
            if (theme == Theme.DARK) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        theme == Theme.DARK -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}