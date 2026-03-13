package com.mydeck.app.io.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.mydeck.app.domain.model.TypographySettings
import com.mydeck.app.domain.model.Theme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreImplTest {

    private lateinit var context: Context
    private val userPreferences
        get() = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        userPreferences.edit().clear().commit()

        val encryptedSharedPreferences = mockk<EncryptedSharedPreferences>(relaxed = true)
        every { encryptedSharedPreferences.contains(any()) } returns false

        mockkObject(EncryptionHelper)
        every { EncryptionHelper.getEncryptedSharedPreferences(context) } returns encryptedSharedPreferences
    }

    @After
    fun tearDown() {
        userPreferences.edit().clear().commit()
        unmockkObject(EncryptionHelper)
    }

    @Test
    fun `themeFlow updates when theme is saved`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        dataStore.saveTheme(Theme.DARK)

        assertEquals(Theme.DARK.name, dataStore.themeFlow.value)
    }

    @Test
    fun `retains strong references for preference listeners`() {
        val dataStore = SettingsDataStoreImpl(context)

        val field = SettingsDataStoreImpl::class.java.getDeclaredField("preferenceChangeListeners")
        field.isAccessible = true

        val listeners = field.get(dataStore) as List<*>

        assertFalse(listeners.isEmpty())
    }

    @Test
    fun `typographySettingsFlow clamps legacy font size when read`() = runTest {
        userPreferences.edit().putInt("typography_font_size_percent", 75).commit()

        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(TypographySettings.MIN_FONT_SIZE, dataStore.typographySettingsFlow.value.fontSizePercent)
    }

    @Test
    fun `saveTypographySettings clamps font size before persisting`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        dataStore.saveTypographySettings(TypographySettings(fontSizePercent = 200))

        assertEquals(TypographySettings.MAX_FONT_SIZE, dataStore.typographySettingsFlow.value.fontSizePercent)
        assertEquals(TypographySettings.MAX_FONT_SIZE, userPreferences.getInt("typography_font_size_percent", -1))
    }

    @Test
    fun `typographySettingsFlow migrates legacy loose line spacing to max stepped value`() = runTest {
        userPreferences.edit().putString("typography_line_spacing", "LOOSE").commit()

        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(TypographySettings.MAX_LINE_SPACING_PERCENT, dataStore.typographySettingsFlow.value.lineSpacingPercent)
    }

    @Test
    fun `saveTypographySettings stores clamped stepped line spacing and removes legacy value`() = runTest {
        userPreferences.edit().putString("typography_line_spacing", "TIGHT").commit()
        val dataStore = SettingsDataStoreImpl(context)

        dataStore.saveTypographySettings(
            TypographySettings(lineSpacingPercent = 130)
        )

        assertEquals(
            TypographySettings.MAX_LINE_SPACING_PERCENT,
            dataStore.typographySettingsFlow.value.lineSpacingPercent
        )
        assertEquals(
            TypographySettings.MAX_LINE_SPACING_PERCENT,
            userPreferences.getInt("typography_line_spacing_percent", -1)
        )
        assertFalse(userPreferences.contains("typography_line_spacing"))
    }
}
