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

data class ReaderAppearanceSelection(
    val themeMode: Theme,
    val lightAppearance: LightAppearance,
    val darkAppearance: DarkAppearance
) {
    fun effectiveAppearance(isSystemDark: Boolean): EffectiveAppearance {
        return resolveEffectiveAppearance(
            themeMode = themeMode,
            isSystemDark = isSystemDark,
            lightAppearance = lightAppearance,
            darkAppearance = darkAppearance
        )
    }
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

fun selectReaderAppearance(
    selectedAppearance: EffectiveAppearance,
    currentSelection: ReaderAppearanceSelection,
    isSystemDark: Boolean
): ReaderAppearanceSelection {
    val updatedSelection = when (selectedAppearance) {
        EffectiveAppearance.PAPER -> currentSelection.copy(lightAppearance = LightAppearance.PAPER)
        EffectiveAppearance.SEPIA -> currentSelection.copy(lightAppearance = LightAppearance.SEPIA)
        EffectiveAppearance.DARK -> currentSelection.copy(darkAppearance = DarkAppearance.DARK)
        EffectiveAppearance.BLACK -> currentSelection.copy(darkAppearance = DarkAppearance.BLACK)
    }
    val currentEffectiveAppearance = currentSelection.effectiveAppearance(isSystemDark)
    val targetThemeMode = if (selectedAppearance.isDark == currentEffectiveAppearance.isDark) {
        currentSelection.themeMode
    } else if (selectedAppearance.isDark) {
        Theme.DARK
    } else {
        Theme.LIGHT
    }

    return updatedSelection.copy(themeMode = targetThemeMode)
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
