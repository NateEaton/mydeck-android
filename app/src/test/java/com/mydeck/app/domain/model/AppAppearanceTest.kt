package com.mydeck.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppAppearanceTest {

    @Test
    fun `selectReaderAppearance keeps system theme when selecting a matching light appearance`() {
        val currentSelection = ReaderAppearanceSelection(
            themeMode = Theme.SYSTEM,
            lightAppearance = LightAppearance.PAPER,
            darkAppearance = DarkAppearance.BLACK
        )

        val updatedSelection = selectReaderAppearance(
            selectedAppearance = EffectiveAppearance.SEPIA,
            currentSelection = currentSelection,
            isSystemDark = false
        )

        assertEquals(Theme.SYSTEM, updatedSelection.themeMode)
        assertEquals(LightAppearance.SEPIA, updatedSelection.lightAppearance)
        assertEquals(DarkAppearance.BLACK, updatedSelection.darkAppearance)
    }

    @Test
    fun `selectReaderAppearance switches to dark theme when picking a dark appearance from light mode`() {
        val currentSelection = ReaderAppearanceSelection(
            themeMode = Theme.LIGHT,
            lightAppearance = LightAppearance.SEPIA,
            darkAppearance = DarkAppearance.DARK
        )

        val updatedSelection = selectReaderAppearance(
            selectedAppearance = EffectiveAppearance.BLACK,
            currentSelection = currentSelection,
            isSystemDark = false
        )

        assertEquals(Theme.DARK, updatedSelection.themeMode)
        assertEquals(LightAppearance.SEPIA, updatedSelection.lightAppearance)
        assertEquals(DarkAppearance.BLACK, updatedSelection.darkAppearance)
    }

    @Test
    fun `selectReaderAppearance switches to light theme when picking a light appearance from dark system mode`() {
        val currentSelection = ReaderAppearanceSelection(
            themeMode = Theme.SYSTEM,
            lightAppearance = LightAppearance.PAPER,
            darkAppearance = DarkAppearance.BLACK
        )

        val updatedSelection = selectReaderAppearance(
            selectedAppearance = EffectiveAppearance.SEPIA,
            currentSelection = currentSelection,
            isSystemDark = true
        )

        assertEquals(Theme.LIGHT, updatedSelection.themeMode)
        assertEquals(LightAppearance.SEPIA, updatedSelection.lightAppearance)
        assertEquals(DarkAppearance.BLACK, updatedSelection.darkAppearance)
    }
}
