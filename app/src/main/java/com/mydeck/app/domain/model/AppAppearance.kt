package com.mydeck.app.domain.model

enum class LightAppearance {
    PAPER,
    SEPIA
}

enum class DarkAppearance {
    DARK,
    BLACK
}

enum class EffectiveAppearance(val isDark: Boolean) {
    PAPER(isDark = false),
    SEPIA(isDark = false),
    DARK(isDark = true),
    BLACK(isDark = true)
}

fun resolveEffectiveAppearance(
    themeMode: Theme,
    isSystemDark: Boolean,
    lightAppearance: LightAppearance,
    darkAppearance: DarkAppearance
): EffectiveAppearance {
    return when (themeMode) {
        Theme.LIGHT -> lightAppearance.toEffectiveAppearance()
        Theme.DARK -> darkAppearance.toEffectiveAppearance()
        Theme.SYSTEM -> {
            if (isSystemDark) darkAppearance.toEffectiveAppearance()
            else lightAppearance.toEffectiveAppearance()
        }
    }
}

fun LightAppearance.toEffectiveAppearance(): EffectiveAppearance {
    return when (this) {
        LightAppearance.PAPER -> EffectiveAppearance.PAPER
        LightAppearance.SEPIA -> EffectiveAppearance.SEPIA
    }
}

fun DarkAppearance.toEffectiveAppearance(): EffectiveAppearance {
    return when (this) {
        DarkAppearance.DARK -> EffectiveAppearance.DARK
        DarkAppearance.BLACK -> EffectiveAppearance.BLACK
    }
}
