package com.mydeck.app.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.LineSpacing
import com.mydeck.app.domain.model.ReaderFontFamily
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.domain.model.TypographySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsBottomSheet(
    currentSettings: TypographySettings,
    onSettingsChanged: (TypographySettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Local mutable state for instant UI feedback
    var settings by remember { mutableStateOf(currentSettings) }

    // Helper to update local state AND propagate to ViewModel
    fun update(newSettings: TypographySettings) {
        settings = newSettings
        onSettingsChanged(newSettings)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Font Family — label and scrollable chips on same row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.typography_section_font),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderFontFamily.entries.forEach { font ->
                        FilterChip(
                            selected = font == settings.fontFamily,
                            onClick = { update(settings.copy(fontFamily = font)) },
                            label = {
                                Text(
                                    text = getFontDisplayName(font),
                                    fontFamily = getFontFamilyForPreview(font)
                                )
                            }
                        )
                    }
                }
            }

            // Font Size — label with +/- buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.typography_section_size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newSize = (settings.fontSizePercent - TypographySettings.FONT_SIZE_STEP)
                                .coerceAtLeast(TypographySettings.MIN_FONT_SIZE)
                            update(settings.copy(fontSizePercent = newSize))
                        },
                        enabled = settings.fontSizePercent > TypographySettings.MIN_FONT_SIZE,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease_text_size))
                    }
                    Text(
                        text = "${settings.fontSizePercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            val newSize = (settings.fontSizePercent + TypographySettings.FONT_SIZE_STEP)
                                .coerceAtMost(TypographySettings.MAX_FONT_SIZE)
                            update(settings.copy(fontSizePercent = newSize))
                        },
                        enabled = settings.fontSizePercent < TypographySettings.MAX_FONT_SIZE,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase_text_size))
                    }
                }
            }

            // Line Spacing — segmented button: Tight / Loose
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.typography_section_line_spacing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow {
                    LineSpacing.entries.forEachIndexed { index, spacing ->
                        SegmentedButton(
                            selected = spacing == settings.lineSpacing,
                            onClick = { update(settings.copy(lineSpacing = spacing)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = LineSpacing.entries.size
                            )
                        ) {
                            Text(
                                when (spacing) {
                                    LineSpacing.TIGHT -> stringResource(R.string.line_spacing_tight)
                                    LineSpacing.LOOSE -> stringResource(R.string.line_spacing_loose)
                                }
                            )
                        }
                    }
                }
            }

            // Text Width — segmented button: Wide / Narrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.typography_section_width),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow {
                    TextWidth.entries.forEachIndexed { index, width ->
                        SegmentedButton(
                            selected = width == settings.textWidth,
                            onClick = { update(settings.copy(textWidth = width)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TextWidth.entries.size
                            )
                        ) {
                            Text(
                                when (width) {
                                    TextWidth.WIDE -> stringResource(R.string.width_wide)
                                    TextWidth.NARROW -> stringResource(R.string.width_narrow)
                                }
                            )
                        }
                    }
                }
            }

            // Justify — toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.justify_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = settings.justified,
                    onCheckedChange = { update(settings.copy(justified = it)) }
                )
            }

            // Hyphenation — toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hyphenation_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = settings.hyphenation,
                    onCheckedChange = { update(settings.copy(hyphenation = it)) }
                )
            }

            // Reset — label and icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.typography_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { update(TypographySettings()) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = stringResource(R.string.typography_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun getFontDisplayName(font: ReaderFontFamily): String {
    return when (font) {
        ReaderFontFamily.SYSTEM_DEFAULT -> stringResource(R.string.font_system_default)
        ReaderFontFamily.NOTO_SERIF -> stringResource(R.string.font_noto_serif)
        ReaderFontFamily.LITERATA -> stringResource(R.string.font_literata)
        ReaderFontFamily.SOURCE_SERIF -> stringResource(R.string.font_source_serif)
        ReaderFontFamily.NOTO_SANS -> stringResource(R.string.font_noto_sans)
        ReaderFontFamily.JETBRAINS_MONO -> stringResource(R.string.font_jetbrains_mono)
    }
}

@Composable
private fun getFontFamilyForPreview(font: ReaderFontFamily): FontFamily {
    return when (font) {
        ReaderFontFamily.JETBRAINS_MONO -> FontFamily.Monospace
        ReaderFontFamily.NOTO_SERIF,
        ReaderFontFamily.LITERATA,
        ReaderFontFamily.SOURCE_SERIF -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }
}
