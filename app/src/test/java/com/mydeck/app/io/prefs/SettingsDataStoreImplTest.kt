package com.mydeck.app.io.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ApplicationProvider
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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val encryptedSharedPreferences = mockk<EncryptedSharedPreferences>(relaxed = true)
        every { encryptedSharedPreferences.contains(any()) } returns false

        mockkObject(EncryptionHelper)
        every { EncryptionHelper.getEncryptedSharedPreferences(context) } returns encryptedSharedPreferences
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE).edit().clear().commit()
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
}
