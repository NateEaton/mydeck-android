package com.mydeck.app.domain.sync

import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContentSyncPolicyEvaluatorTest {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var evaluator: ContentSyncPolicyEvaluator

    @Before
    fun setup() {
        settingsDataStore = mockk()
        connectivityMonitor = mockk()
        evaluator = ContentSyncPolicyEvaluator(settingsDataStore, connectivityMonitor)
    }

    @Test
    fun `canFetchContent returns Decision(allowed=false) when wifiOnly is true but not on WiFi`() = runTest {
        // Arrange
        val constraints = ContentSyncConstraints(wifiOnly = true, allowOnBatterySaver = true)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns false
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns false

        // Act
        val decision = evaluator.canFetchContent()

        // Assert
        assertFalse(decision.allowed)
        assertEquals("Wi-Fi required", decision.blockedReason)
    }

    @Test
    fun `canFetchContent returns Decision(allowed=false) when battery saver is on and not allowed`() = runTest {
        // Arrange
        val constraints = ContentSyncConstraints(wifiOnly = false, allowOnBatterySaver = false)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns true

        // Act
        val decision = evaluator.canFetchContent()

        // Assert
        assertFalse(decision.allowed)
        assertEquals("Battery saver active", decision.blockedReason)
    }

    @Test
    fun `canFetchContent returns Decision(allowed=false) when no network available`() = runTest {
        // Arrange
        val constraints = ContentSyncConstraints(wifiOnly = false, allowOnBatterySaver = true)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isNetworkAvailable() } returns false
        every { connectivityMonitor.isBatterySaverOn() } returns false

        // Act
        val decision = evaluator.canFetchContent()

        // Assert
        assertFalse(decision.allowed)
        assertEquals("No network", decision.blockedReason)
    }

    @Test
    fun `canFetchContent returns Decision(allowed=true) when all constraints satisfied`() = runTest {
        // Arrange
        val constraints = ContentSyncConstraints(wifiOnly = true, allowOnBatterySaver = false)
        coEvery { settingsDataStore.getContentSyncConstraints() } returns constraints
        every { connectivityMonitor.isOnWifi() } returns true
        every { connectivityMonitor.isNetworkAvailable() } returns true
        every { connectivityMonitor.isBatterySaverOn() } returns false

        // Act
        val decision = evaluator.canFetchContent()

        // Assert
        assertTrue(decision.allowed)
        assertEquals(null, decision.blockedReason)
    }

    @Test
    fun `shouldAutoFetchContent returns false when offline reading is disabled`() = runTest {
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns false

        // Act
        val result = evaluator.shouldAutoFetchContent()

        // Assert
        assertFalse(result)
    }

    @Test
    fun `shouldAutoFetchContent returns true when offline reading is enabled`() = runTest {
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true

        // Act
        val result = evaluator.shouldAutoFetchContent()

        // Assert
        assertTrue(result)
    }

    @Test
    fun `shouldAutoFetchContent ignores connectivity constraints when offline reading is enabled`() = runTest {
        // Constraints and connectivity are evaluated elsewhere; this helper only checks whether
        // managed offline reading is enabled.
        coEvery { settingsDataStore.isOfflineReadingEnabled() } returns true

        // Act
        val result = evaluator.shouldAutoFetchContent()

        // Assert
        assertTrue(result)
    }
}
