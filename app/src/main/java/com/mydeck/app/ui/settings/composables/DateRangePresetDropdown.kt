package com.mydeck.app.ui.settings.composables

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mydeck.app.R
import com.mydeck.app.domain.sync.DateRangePreset

@Composable
fun DateRangePresetDropdown(
    selectedPreset: DateRangePreset,
    onPresetSelected: (DateRangePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { expanded.value = true },
        modifier = modifier
    ) {
        Text(text = getPresetLabel(selectedPreset))
        Text(text = " ▼")
    }

    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sync_date_preset_past_day)) },
            onClick = {
                onPresetSelected(DateRangePreset.PAST_DAY)
                expanded.value = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sync_date_preset_past_week)) },
            onClick = {
                onPresetSelected(DateRangePreset.PAST_WEEK)
                expanded.value = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sync_date_preset_past_month)) },
            onClick = {
                onPresetSelected(DateRangePreset.PAST_MONTH)
                expanded.value = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sync_date_preset_past_year)) },
            onClick = {
                onPresetSelected(DateRangePreset.PAST_YEAR)
                expanded.value = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sync_date_preset_custom)) },
            onClick = {
                onPresetSelected(DateRangePreset.CUSTOM)
                expanded.value = false
            }
        )
    }
}

@Composable
private fun getPresetLabel(preset: DateRangePreset): String {
    return when (preset) {
        DateRangePreset.PAST_DAY -> stringResource(R.string.sync_date_preset_past_day)
        DateRangePreset.PAST_WEEK -> stringResource(R.string.sync_date_preset_past_week)
        DateRangePreset.PAST_MONTH -> stringResource(R.string.sync_date_preset_past_month)
        DateRangePreset.PAST_YEAR -> stringResource(R.string.sync_date_preset_past_year)
        DateRangePreset.CUSTOM -> stringResource(R.string.sync_date_preset_custom)
    }
}
