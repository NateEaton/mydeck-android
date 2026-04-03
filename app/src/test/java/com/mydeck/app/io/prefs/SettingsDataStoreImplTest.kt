package com.mydeck.app.io.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.TypographySettings
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.sync.OfflineContentScope
import com.mydeck.app.domain.sync.OfflinePolicy
import com.mydeck.app.domain.sync.OfflinePolicyDefaults
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreImplTest {

    private lateinit var context: Context
    private lateinit var encryptedSharedPreferences: EncryptedSharedPreferences
    private val userPreferences
        get() = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        userPreferences.edit().clear().commit()

        encryptedSharedPreferences = mockk(relaxed = true)
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
    fun `migrates legacy sepia preference to curated appearances`() = runTest {
        userPreferences.edit()
            .putBoolean("sepia_enabled", true)
            .commit()

        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(LightAppearance.SEPIA, dataStore.getLightAppearance())
        assertEquals(DarkAppearance.DARK, dataStore.getDarkAppearance())
        assertEquals(LightAppearance.SEPIA, dataStore.lightAppearanceFlow.value)
        assertEquals(DarkAppearance.DARK, dataStore.darkAppearanceFlow.value)
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

    @Test
    fun `migrates stepped line spacing percent from encrypted preferences`() = runTest {
        every { encryptedSharedPreferences.contains("typography_line_spacing_percent") } returns true
        every { encryptedSharedPreferences.getInt("typography_line_spacing_percent", 0) } returns 115

        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(115, dataStore.typographySettingsFlow.value.lineSpacingPercent)
        assertTrue(userPreferences.contains("typography_line_spacing_percent"))
        assertEquals(115, userPreferences.getInt("typography_line_spacing_percent", -1))
    }

    @Test
    fun `fullscreenWhileReading defaults to false`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        assertFalse(dataStore.isFullscreenWhileReading())
        assertFalse(dataStore.fullscreenWhileReadingFlow.value)
    }

    @Test
    fun `fullscreenWhileReadingFlow updates when preference is saved`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        dataStore.saveFullscreenWhileReading(true)

        assertEquals(true, dataStore.fullscreenWhileReadingFlow.value)
    }

    @Test
    fun `bookmarkShareFormat defaults to url only`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(BookmarkShareFormat.URL_ONLY, dataStore.getBookmarkShareFormat())
        assertEquals(BookmarkShareFormat.URL_ONLY, dataStore.bookmarkShareFormatFlow.value)
    }

    @Test
    fun `bookmarkShareFormatFlow updates when preference is saved`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        dataStore.saveBookmarkShareFormat(BookmarkShareFormat.TITLE_AND_URL_MULTILINE)

        assertEquals(
            BookmarkShareFormat.TITLE_AND_URL_MULTILINE,
            dataStore.bookmarkShareFormatFlow.value
        )
    }

    @Test
    fun `clearContentOnArchive defaults to false`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        assertFalse(dataStore.isClearContentOnArchiveEnabled())
    }

    @Test
    fun `clearContentOnArchive round-trips saved value`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        dataStore.saveClearContentOnArchiveEnabled(true)
        assertTrue(dataStore.isClearContentOnArchiveEnabled())

        dataStore.saveClearContentOnArchiveEnabled(false)
        assertFalse(dataStore.isClearContentOnArchiveEnabled())
    }

    @Test
    fun `offline policy defaults to storage limit`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(OfflinePolicy.STORAGE_LIMIT, dataStore.getOfflinePolicy())
        assertEquals(OfflinePolicyDefaults.STORAGE_LIMIT_BYTES, dataStore.getOfflinePolicyStorageLimit())
    }

    @Test
    fun `offline content scope falls back to legacy include archived key`() = runTest {
        userPreferences.edit()
            .putBoolean("include_archived_content_in_sync", true)
            .commit()

        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(OfflineContentScope.MY_LIST_AND_ARCHIVED, dataStore.getOfflineContentScope())
    }

    @Test
    fun `offline content scope round-trips saved value`() = runTest {
        val dataStore = SettingsDataStoreImpl(context)

        dataStore.saveOfflineContentScope(OfflineContentScope.MY_LIST_AND_ARCHIVED)
        assertEquals(OfflineContentScope.MY_LIST_AND_ARCHIVED, dataStore.getOfflineContentScope())

        dataStore.saveOfflineContentScope(OfflineContentScope.MY_LIST)
        assertEquals(OfflineContentScope.MY_LIST, dataStore.getOfflineContentScope())
    }

    @Test
    fun `offline reading enabled falls back to legacy content sync mode`() = runTest {
        userPreferences.edit()
            .putString("content_sync_mode", "AUTOMATIC")
            .commit()

        val dataStore = SettingsDataStoreImpl(context)

        assertTrue(dataStore.isOfflineReadingEnabled())
    }

    @Test
    fun `offline policy storage limit falls back to legacy image limit`() = runTest {
        userPreferences.edit()
            .putString("offline_image_storage_limit", "MB_250")
            .commit()

        val dataStore = SettingsDataStoreImpl(context)

        assertEquals(250L * 1024 * 1024, dataStore.getOfflinePolicyStorageLimit())
    }
}
