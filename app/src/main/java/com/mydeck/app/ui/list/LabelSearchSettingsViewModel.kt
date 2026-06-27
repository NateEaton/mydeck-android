package com.mydeck.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.domain.model.LabelSearchMatching
import com.mydeck.app.domain.model.LabelSearchSort
import com.mydeck.app.io.prefs.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the two label-search ranking toggles in [LabelPickerBottomSheet]. The choice is persisted
 * globally (via [SettingsDataStore]) so it sticks across sessions and every label picker stays
 * consistent. The persisted flows are reactive, so toggling re-emits and the sheet re-ranks.
 */
@HiltViewModel
class LabelSearchSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    val matching: StateFlow<LabelSearchMatching> = settingsDataStore.labelSearchMatchingFlow
    val sort: StateFlow<LabelSearchSort> = settingsDataStore.labelSearchSortFlow

    fun toggleMatching() {
        val next = when (matching.value) {
            LabelSearchMatching.CONTAINS -> LabelSearchMatching.STARTS_WITH
            LabelSearchMatching.STARTS_WITH -> LabelSearchMatching.CONTAINS
        }
        viewModelScope.launch { settingsDataStore.saveLabelSearchMatching(next) }
    }

    fun toggleSort() {
        val next = when (sort.value) {
            LabelSearchSort.ALPHABETICAL -> LabelSearchSort.BY_FREQUENCY
            LabelSearchSort.BY_FREQUENCY -> LabelSearchSort.ALPHABETICAL
        }
        viewModelScope.launch { settingsDataStore.saveLabelSearchSort(next) }
    }
}
