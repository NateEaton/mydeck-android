package com.mydeck.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.mydeck.app.domain.model.EffectiveAppearance
import com.mydeck.app.ui.theme.sepia.SepiaColorScheme

val PaperColorScheme = lightColorScheme(
    primary = Color(0xFF1D7484),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7EEF2),
    onPrimaryContainer = Color(0xFF123B43),
    secondary = Color(0xFF5F6B73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE4E8),
    onSecondaryContainer = Color(0xFF243138),
    tertiary = Color(0xFF7B4B59),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4D9E1),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF3B3A36),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF3B3A36),
    surfaceVariant = Color(0xFFE9E7E0),
    onSurfaceVariant = Color(0xFF595750),
    outline = Color(0xFF8A877E),
    outlineVariant = Color(0xFFD4D0C7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4EF),
    surfaceContainer = Color(0xFFF1EFEA),
    surfaceContainerHigh = Color(0xFFECEAE4),
    surfaceContainerHighest = Color(0xFFE7E4DD)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9BC4CE),
    onPrimary = Color(0xFF17353B),
    primaryContainer = Color(0xFF24454D),
    onPrimaryContainer = Color(0xFFCDE7ED),
    secondary = Color(0xFFB5C7CC),
    onSecondary = Color(0xFF203237),
    secondaryContainer = Color(0xFF36484D),
    onSecondaryContainer = Color(0xFFD1E3E8),
    tertiary = Color(0xFFD3B7C0),
    onTertiary = Color(0xFF3B242C),
    tertiaryContainer = Color(0xFF523841),
    onTertiaryContainer = Color(0xFFF0D6DE),
    background = Color(0xFF222222),
    onBackground = Color(0xFFD0CCC4),
    surface = Color(0xFF222222),
    onSurface = Color(0xFFD0CCC4),
    surfaceVariant = Color(0xFF363636),
    onSurfaceVariant = Color(0xFFA8A39A),
    outline = Color(0xFF7A766F),
    outlineVariant = Color(0xFF44413B),
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF262626),
    surfaceContainer = Color(0xFF2C2C2C),
    surfaceContainerHigh = Color(0xFF313131),
    surfaceContainerHighest = Color(0xFF383838)
)

val BlackColorScheme = darkColorScheme(
    primary = Color(0xFFB5D8FF),
    onPrimary = Color(0xFF002844),
    primaryContainer = Color(0xFF103A60),
    onPrimaryContainer = Color(0xFFD7E9FF),
    secondary = Color(0xFFD0D5DD),
    onSecondary = Color(0xFF1E2933),
    secondaryContainer = Color(0xFF313A44),
    onSecondaryContainer = Color(0xFFE6EBF2),
    tertiary = Color(0xFFE4D3D8),
    onTertiary = Color(0xFF38272D),
    tertiaryContainer = Color(0xFF4C3940),
    onTertiaryContainer = Color(0xFFFFECF0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = Color(0xFFC9C9C9),
    outline = Color(0xFF8D8D8D),
    outlineVariant = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1D1D1D)
)

fun resolveAppColorScheme(
    context: Context,
    appearance: EffectiveAppearance
): ColorScheme {
    val curatedScheme = when (appearance) {
        EffectiveAppearance.PAPER -> PaperColorScheme
        EffectiveAppearance.SEPIA -> SepiaColorScheme
        EffectiveAppearance.DARK -> DarkColorScheme
        EffectiveAppearance.BLACK -> BlackColorScheme
    }

    if (appearance == EffectiveAppearance.SEPIA || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return curatedScheme
    }

    val dynamicScheme = if (appearance.isDark) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    return dynamicScheme.withCuratedSurfaces(curatedScheme)
}

private fun ColorScheme.withCuratedSurfaces(curatedScheme: ColorScheme): ColorScheme {
    return copy(
        background = curatedScheme.background,
        onBackground = curatedScheme.onBackground,
        surface = curatedScheme.surface,
        onSurface = curatedScheme.onSurface,
        surfaceVariant = curatedScheme.surfaceVariant,
        onSurfaceVariant = curatedScheme.onSurfaceVariant,
        outline = curatedScheme.outline,
        outlineVariant = curatedScheme.outlineVariant,
        scrim = curatedScheme.scrim,
        inverseSurface = curatedScheme.inverseSurface,
        inverseOnSurface = curatedScheme.inverseOnSurface,
        surfaceDim = curatedScheme.surfaceDim,
        surfaceBright = curatedScheme.surfaceBright,
        surfaceContainerLowest = curatedScheme.surfaceContainerLowest,
        surfaceContainerLow = curatedScheme.surfaceContainerLow,
        surfaceContainer = curatedScheme.surfaceContainer,
        surfaceContainerHigh = curatedScheme.surfaceContainerHigh,
        surfaceContainerHighest = curatedScheme.surfaceContainerHighest
    )
}
