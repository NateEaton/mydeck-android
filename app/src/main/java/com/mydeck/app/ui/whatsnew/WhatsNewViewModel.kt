package com.mydeck.app.ui.whatsnew

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.util.AppVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: WhatsNewAssetLoader,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    var uiState by mutableStateOf(WhatsNewUiState())
        private set

    private var evaluated = false

    /**
     * Runs once per ViewModel instance (i.e. once per cold start of the
     * authenticated shell). Idempotent across recomposition/config changes.
     */
    fun evaluateIfNeeded() {
        if (evaluated) return
        evaluated = true
        viewModelScope.launch {
            val current = WhatsNewAssetLoader.normalizeVersion(AppVersion.versionName(context))
            val lastSeen = settingsDataStore.getLastSeenWhatsNewVersion()
            if (lastSeen == null) {
                settingsDataStore.saveLastSeenWhatsNewVersion(current)
                if (settingsDataStore.isInitialSyncPerformed()) {
                    // No marker, but this account has already completed its first
                    // sync — this is an upgrade from a build that predates this
                    // feature, not a fresh install. Treat it like any other
                    // version change instead of silently suppressing.
                    showNotesIfAvailable(current)
                } else {
                    // Genuinely fresh install: nothing to show yet, offer the
                    // guide nudge instead.
                    if (!settingsDataStore.isWelcomeGuidePromptShown()) {
                        uiState = uiState.copy(showGuideNudge = true)
                    }
                }
                return@launch
            }
            if (current != lastSeen) {
                settingsDataStore.saveLastSeenWhatsNewVersion(current)
                showNotesIfAvailable(current)
            }
        }
    }

    private fun showNotesIfAvailable(version: String) {
        val notes = loader.loadNotesForVersion(version)
        if (notes != null) {
            uiState = uiState.copy(whatsNewContent = notes, whatsNewVersion = version)
        }
    }

    fun onWhatsNewDismissed() {
        uiState = uiState.copy(whatsNewContent = null)
    }

    fun onGuideNudgeDismissed() {
        uiState = uiState.copy(showGuideNudge = false)
        viewModelScope.launch { settingsDataStore.saveWelcomeGuidePromptShown(true) }
    }

    fun onGuideNudgeOpenGuide() {
        // Marking the prompt as shown is the same regardless of which action the
        // user took.
        onGuideNudgeDismissed()
    }
}

data class WhatsNewUiState(
    val whatsNewContent: String? = null,
    val whatsNewVersion: String = "",
    val showGuideNudge: Boolean = false,
)
