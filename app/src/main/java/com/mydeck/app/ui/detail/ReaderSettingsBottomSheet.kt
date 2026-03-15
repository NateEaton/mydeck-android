package com.mydeck.app.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.EffectiveAppearance
import com.mydeck.app.domain.model.ReaderFontFamily
import com.mydeck.app.domain.model.ReaderAppearanceSelection
import com.mydeck.app.domain.model.TextWidth
import com.mydeck.app.domain.model.TypographySettings
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.domain.model.selectReaderAppearance

private enum class ReaderSettingsTab(@StringRes val titleRes: Int) {
    TEXT(R.string.typography_tab_text),
    LAYOUT(R.string.layout),
    THEME(R.string.typography_tab_theme),
}

private enum class ReaderSettingsSheetSlot {
    HEADER,
    FOOTER,
    ACTIVE_BODY,
}

private data class ReaderSettingsSheetMeasureSlot(val tab: ReaderSettingsTab)

private val ReaderSettingsSectionSpacing = 16.dp
private val ReaderSettingsSheetHorizontalPadding = 24.dp
private val ReaderSettingsSheetBottomPadding = 40.dp
private const val ReaderSettingsMaxHeightFraction = 0.85f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsBottomSheet(
    currentSettings: TypographySettings,
    currentAppearanceSelection: ReaderAppearanceSelection,
    onSettingsChanged: (TypographySettings) -> Unit,
    onThemeSelectionChanged: (ReaderAppearanceSelection) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Local mutable state for instant UI feedback
    var settings by remember { mutableStateOf(currentSettings) }
    var appearanceSelection by remember {
        mutableStateOf(currentAppearanceSelection)
    }
    var selectedTab by remember { mutableStateOf(ReaderSettingsTab.TEXT) }
    val isSystemDark = isSystemInDarkTheme()
    val activeAppearance = appearanceSelection.effectiveAppearance(isSystemDark)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * ReaderSettingsMaxHeightFraction
    val defaultSettings = remember { TypographySettings() }
    val sectionLabelStyle = MaterialTheme.typography.titleMedium
    val sectionLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val widthOptions = listOf(TextWidth.NARROW, TextWidth.MEDIUM, TextWidth.WIDE)

    // Helper to update local state AND propagate to ViewModel
    fun update(newSettings: TypographySettings) {
        settings = newSettings
        onSettingsChanged(newSettings)
    }

    fun updateAppearanceSelection(newSelection: ReaderAppearanceSelection) {
        appearanceSelection = newSelection
        onThemeSelectionChanged(newSelection)
    }

    fun resetCurrentTab() {
        when (selectedTab) {
            ReaderSettingsTab.TEXT -> {
                update(
                    settings.copy(
                        fontSizePercent = defaultSettings.fontSizePercent,
                        fontFamily = defaultSettings.fontFamily,
                        lineSpacingPercent = defaultSettings.lineSpacingPercent,
                    )
                )
            }

            ReaderSettingsTab.LAYOUT -> {
                update(
                    settings.copy(
                        textWidth = defaultSettings.textWidth,
                        justified = defaultSettings.justified,
                        hyphenation = defaultSettings.hyphenation,
                    )
                )
            }

            ReaderSettingsTab.THEME -> {
                updateAppearanceSelection(
                    appearanceSelection.copy(themeMode = Theme.SYSTEM)
                )
            }
        }
    }

    val resetLabelRes = when (selectedTab) {
        ReaderSettingsTab.TEXT -> R.string.typography_reset_text
        ReaderSettingsTab.LAYOUT -> R.string.typography_reset_layout
        ReaderSettingsTab.THEME -> R.string.typography_reset_theme
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ReaderSettingsSheetContent(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            resetLabelRes = resetLabelRes,
            onReset = ::resetCurrentTab,
            maxSheetHeight = maxSheetHeight,
            settings = settings,
            onSettingsChanged = ::update,
            appearanceSelection = appearanceSelection,
            activeAppearance = activeAppearance,
            onAppearanceSelectionChanged = ::updateAppearanceSelection,
            isSystemDark = isSystemDark,
            sectionLabelColor = sectionLabelColor,
            sectionLabelStyle = sectionLabelStyle,
            widthOptions = widthOptions,
        )
    }
}

@Composable
private fun ReaderSettingsSheetContent(
    selectedTab: ReaderSettingsTab,
    onTabSelected: (ReaderSettingsTab) -> Unit,
    @StringRes resetLabelRes: Int,
    onReset: () -> Unit,
    maxSheetHeight: Dp,
    settings: TypographySettings,
    onSettingsChanged: (TypographySettings) -> Unit,
    appearanceSelection: ReaderAppearanceSelection,
    activeAppearance: EffectiveAppearance,
    onAppearanceSelectionChanged: (ReaderAppearanceSelection) -> Unit,
    isSystemDark: Boolean,
    sectionLabelStyle: androidx.compose.ui.text.TextStyle,
    sectionLabelColor: Color,
    widthOptions: List<TextWidth>,
) {
    val sectionSpacingPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        ReaderSettingsSectionSpacing.roundToPx()
    }
    val maxSheetHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        maxSheetHeight.roundToPx()
    }

    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderSettingsSheetHorizontalPadding)
            .padding(bottom = ReaderSettingsSheetBottomPadding)
    ) { constraints ->
        val fullWidthConstraints = constraints.copy(
            minWidth = constraints.maxWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
        )

        val headerPlaceables = subcompose(ReaderSettingsSheetSlot.HEADER) {
            ReaderSettingsTabSwitcher(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        }.map { it.measure(fullWidthConstraints) }
        val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0

        val footerPlaceables = subcompose(ReaderSettingsSheetSlot.FOOTER) {
            ReaderSettingsSheetFooter(
                resetLabelRes = resetLabelRes,
                onReset = onReset,
            )
        }.map { it.measure(fullWidthConstraints) }
        val footerHeight = footerPlaceables.maxOfOrNull { it.height } ?: 0

        val naturalBodyHeights = ReaderSettingsTab.entries.associateWith { tab ->
            subcompose(ReaderSettingsSheetMeasureSlot(tab)) {
                ReaderSettingsTabBody(
                    tab = tab,
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                    appearanceSelection = appearanceSelection,
                    activeAppearance = activeAppearance,
                    onAppearanceSelectionChanged = onAppearanceSelectionChanged,
                    isSystemDark = isSystemDark,
                    sectionLabelStyle = sectionLabelStyle,
                    sectionLabelColor = sectionLabelColor,
                    widthOptions = widthOptions,
                    scrollable = false,
                )
            }.map { measurable ->
                measurable.measure(
                    fullWidthConstraints.copy(maxHeight = Constraints.Infinity)
                )
            }.maxOfOrNull { it.height } ?: 0
        }

        val tallestBodyHeight = naturalBodyHeights.values.maxOrNull() ?: 0
        val chromeHeight = headerHeight + footerHeight + (sectionSpacingPx * 2)
        val maxBodyHeight = (maxSheetHeightPx - chromeHeight).coerceAtLeast(0)
        val bodyViewportHeight = tallestBodyHeight.coerceAtMost(maxBodyHeight)

        val bodyPlaceables = subcompose(ReaderSettingsSheetSlot.ACTIVE_BODY) {
            ReaderSettingsTabBody(
                tab = selectedTab,
                settings = settings,
                onSettingsChanged = onSettingsChanged,
                appearanceSelection = appearanceSelection,
                activeAppearance = activeAppearance,
                onAppearanceSelectionChanged = onAppearanceSelectionChanged,
                isSystemDark = isSystemDark,
                sectionLabelStyle = sectionLabelStyle,
                sectionLabelColor = sectionLabelColor,
                widthOptions = widthOptions,
                scrollable = true,
            )
        }.map { measurable ->
            measurable.measure(
                fullWidthConstraints.copy(
                    minHeight = bodyViewportHeight,
                    maxHeight = bodyViewportHeight,
                )
            )
        }

        val layoutHeight = headerHeight + bodyViewportHeight + footerHeight + (sectionSpacingPx * 2)

        layout(width = constraints.maxWidth, height = layoutHeight) {
            var yPosition = 0
            headerPlaceables.forEach { placeable ->
                placeable.placeRelative(x = 0, y = yPosition)
            }
            yPosition += headerHeight + sectionSpacingPx

            bodyPlaceables.forEach { placeable ->
                placeable.placeRelative(x = 0, y = yPosition)
            }
            yPosition += bodyViewportHeight + sectionSpacingPx

            footerPlaceables.forEach { placeable ->
                placeable.placeRelative(x = 0, y = yPosition)
            }
        }
    }
}

@Composable
private fun ReaderSettingsTabSwitcher(
    selectedTab: ReaderSettingsTab,
    onTabSelected: (ReaderSettingsTab) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingsTab.entries.forEachIndexed { index, tab ->
            val isSelected = selectedTab == tab
            SegmentedButton(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f),
                icon = { SegmentedButtonDefaults.Icon(active = isSelected) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ReaderSettingsTab.entries.size
                )
            ) {
                Text(text = stringResource(tab.titleRes))
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheetFooter(
    @StringRes resetLabelRes: Int,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        TextButton(
            onClick = onReset,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(resetLabelRes))
        }
    }
}

@Composable
private fun ReaderSettingsTabBody(
    tab: ReaderSettingsTab,
    settings: TypographySettings,
    onSettingsChanged: (TypographySettings) -> Unit,
    appearanceSelection: ReaderAppearanceSelection,
    activeAppearance: EffectiveAppearance,
    onAppearanceSelectionChanged: (ReaderAppearanceSelection) -> Unit,
    isSystemDark: Boolean,
    sectionLabelStyle: androidx.compose.ui.text.TextStyle,
    sectionLabelColor: Color,
    widthOptions: List<TextWidth>,
    scrollable: Boolean,
) {
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    when (tab) {
        ReaderSettingsTab.TEXT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(scrollModifier),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.typography_section_font),
                        style = sectionLabelStyle,
                        color = sectionLabelColor,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderFontFamily.entries.forEach { font ->
                            val isSelected = font == settings.fontFamily
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onSettingsChanged(settings.copy(fontFamily = font))
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                label = {
                                    Text(
                                        text = getFontDisplayName(font),
                                        fontFamily = TypographyUtils.getFontFamily(font),
                                    )
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.typography_section_size),
                        style = sectionLabelStyle,
                        color = sectionLabelColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val newSize = (
                                    settings.fontSizePercent - TypographySettings.FONT_SIZE_STEP
                                ).coerceAtLeast(TypographySettings.MIN_FONT_SIZE)
                                onSettingsChanged(settings.copy(fontSizePercent = newSize))
                            },
                            enabled = settings.fontSizePercent > TypographySettings.MIN_FONT_SIZE,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Remove,
                                contentDescription = stringResource(R.string.action_decrease_text_size)
                            )
                        }
                        Text(
                            text = "${settings.fontSizePercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                val newSize = (
                                    settings.fontSizePercent + TypographySettings.FONT_SIZE_STEP
                                ).coerceAtMost(TypographySettings.MAX_FONT_SIZE)
                                onSettingsChanged(settings.copy(fontSizePercent = newSize))
                            },
                            enabled = settings.fontSizePercent < TypographySettings.MAX_FONT_SIZE,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.action_increase_text_size)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.typography_section_line_spacing),
                        style = sectionLabelStyle,
                        color = sectionLabelColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val newSpacing = (
                                    settings.lineSpacingPercent - TypographySettings.LINE_SPACING_STEP
                                ).coerceAtLeast(TypographySettings.MIN_LINE_SPACING_PERCENT)
                                onSettingsChanged(settings.copy(lineSpacingPercent = newSpacing))
                            },
                            enabled = settings.lineSpacingPercent > TypographySettings.MIN_LINE_SPACING_PERCENT,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Remove,
                                contentDescription = stringResource(R.string.action_decrease_line_spacing)
                            )
                        }
                        Text(
                            text = "${settings.lineSpacingPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                val newSpacing = (
                                    settings.lineSpacingPercent + TypographySettings.LINE_SPACING_STEP
                                ).coerceAtMost(TypographySettings.MAX_LINE_SPACING_PERCENT)
                                onSettingsChanged(settings.copy(lineSpacingPercent = newSpacing))
                            },
                            enabled = settings.lineSpacingPercent < TypographySettings.MAX_LINE_SPACING_PERCENT,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.action_increase_line_spacing)
                            )
                        }
                    }
                }
            }
        }

        ReaderSettingsTab.LAYOUT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(scrollModifier),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.typography_section_width),
                            style = sectionLabelStyle,
                            color = sectionLabelColor
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            widthOptions.forEachIndexed { index, width ->
                                val widthContentDescription = getWidthContentDescription(width)
                                val isSelected = width == settings.textWidth
                                SegmentedButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSettingsChanged(settings.copy(textWidth = width))
                                    },
                                    modifier = Modifier
                                        .semantics {
                                            contentDescription = widthContentDescription
                                        }
                                        .weight(1f),
                                    icon = { SegmentedButtonDefaults.Icon(active = isSelected) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = widthOptions.size
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        WidthOptionIcon(width = width)
                                        Text(text = width.pillLabel)
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.justify_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = settings.justified,
                            onCheckedChange = {
                                onSettingsChanged(settings.copy(justified = it))
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hyphenation_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = settings.hyphenation,
                            onCheckedChange = {
                                onSettingsChanged(settings.copy(hyphenation = it))
                            }
                        )
                    }
                }
            }
        }

        ReaderSettingsTab.THEME -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(scrollModifier),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.typography_section_reading_theme),
                        style = sectionLabelStyle,
                        color = sectionLabelColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionCard(
                            appearance = EffectiveAppearance.PAPER,
                            selectedAppearance = activeAppearance,
                            onClick = {
                                onAppearanceSelectionChanged(
                                    selectReaderAppearance(
                                        selectedAppearance = EffectiveAppearance.PAPER,
                                        currentSelection = appearanceSelection,
                                        isSystemDark = isSystemDark
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            appearance = EffectiveAppearance.SEPIA,
                            selectedAppearance = activeAppearance,
                            onClick = {
                                onAppearanceSelectionChanged(
                                    selectReaderAppearance(
                                        selectedAppearance = EffectiveAppearance.SEPIA,
                                        currentSelection = appearanceSelection,
                                        isSystemDark = isSystemDark
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            appearance = EffectiveAppearance.DARK,
                            selectedAppearance = activeAppearance,
                            onClick = {
                                onAppearanceSelectionChanged(
                                    selectReaderAppearance(
                                        selectedAppearance = EffectiveAppearance.DARK,
                                        currentSelection = appearanceSelection,
                                        isSystemDark = isSystemDark
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            appearance = EffectiveAppearance.BLACK,
                            selectedAppearance = activeAppearance,
                            onClick = {
                                onAppearanceSelectionChanged(
                                    selectReaderAppearance(
                                        selectedAppearance = EffectiveAppearance.BLACK,
                                        currentSelection = appearanceSelection,
                                        isSystemDark = isSystemDark
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
private fun getWidthContentDescription(width: TextWidth): String {
    return when (width) {
        TextWidth.WIDE -> stringResource(R.string.width_wide)
        TextWidth.MEDIUM -> stringResource(R.string.width_medium)
        TextWidth.NARROW -> stringResource(R.string.width_narrow)
    }
}

@Composable
private fun WidthOptionIcon(width: TextWidth) {
    val lineWidth = when (width) {
        TextWidth.NARROW -> 12.dp
        TextWidth.MEDIUM -> 18.dp
        TextWidth.WIDE -> 24.dp
    }
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(lineWidth)
                    .height(2.dp)
                    .background(
                        color = lineColor,
                        shape = RoundedCornerShape(percent = 50)
                    )
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(
    appearance: EffectiveAppearance,
    selectedAppearance: EffectiveAppearance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 92.dp,
) {
    val isSelected = appearance == selectedAppearance
    val previewBackground = when (appearance) {
        EffectiveAppearance.PAPER -> Color(0xFFF9F9F9)
        EffectiveAppearance.SEPIA -> Color(0xFFF4ECD8)
        EffectiveAppearance.DARK -> Color(0xFF222222)
        EffectiveAppearance.BLACK -> Color(0xFF000000)
    }
    val previewForeground = when (appearance) {
        EffectiveAppearance.PAPER -> Color(0xFF3B3A36)
        EffectiveAppearance.SEPIA -> Color(0xFF4A3B2B)
        EffectiveAppearance.DARK -> Color(0xFFD0CCC4)
        EffectiveAppearance.BLACK -> Color(0xFFF5F5F5)
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = containerColor,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(
                        color = previewBackground,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(3.dp)
                            .background(
                                color = previewForeground.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(3.dp)
                            .background(
                                color = previewForeground.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(18.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Text(
                text = getAppearanceDisplayName(appearance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun getAppearanceDisplayName(appearance: EffectiveAppearance): String {
    return when (appearance) {
        EffectiveAppearance.PAPER -> stringResource(R.string.appearance_paper)
        EffectiveAppearance.SEPIA -> stringResource(R.string.appearance_sepia)
        EffectiveAppearance.DARK -> stringResource(R.string.appearance_dark)
        EffectiveAppearance.BLACK -> stringResource(R.string.appearance_black)
    }
}
