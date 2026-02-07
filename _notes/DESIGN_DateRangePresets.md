# Design Proposal: Date Range Content Sync Enhancement

## Overview
Refactor the Content Sync date range feature to provide predefined time period options alongside a custom date range picker. This improves UX by reducing friction for common use cases while maintaining flexibility for custom ranges.

---

## Current State
**Problem:**
- Users must always manually select both start and end dates
- No quick options for common ranges (last week, last month, etc.)
- Date picker controls take up significant UI space even when not needed

**Current Flow:**
```
Content Sync Mode Selection
    ↓
[Date Range selected]
    ↓
Show date from/to pickers
    ↓
Manual date entry required
```

---

## Proposed Solution

### 1. Data Model Changes

#### New Enum: `DateRangePreset`
```kotlin
enum class DateRangePreset {
    LAST_DAY,        // Last 24 hours
    LAST_WEEK,       // Last 7 days
    LAST_MONTH,      // Last 30 days
    LAST_YEAR,       // Last 365 days
    CUSTOM           // User-defined range
}
```

#### Updated `DateRangeParams`
```kotlin
data class DateRangeParams(
    val preset: DateRangePreset = DateRangePreset.LAST_MONTH,  // Default to last month
    val from: LocalDate? = null,      // Only populated if preset == CUSTOM
    val to: LocalDate? = null,        // Only populated if preset == CUSTOM
    val downloading: Boolean = false
)
```

#### Preset → Date Range Resolution Logic
```kotlin
fun DateRangePreset.toDateRange(today: LocalDate): Pair<LocalDate, LocalDate> {
    return when (this) {
        LAST_DAY -> Pair(today.minusDays(1), today)
        LAST_WEEK -> Pair(today.minusDays(7), today)
        LAST_MONTH -> Pair(today.minusDays(30), today)
        LAST_YEAR -> Pair(today.minusDays(365), today)
        CUSTOM -> throw IllegalArgumentException("CUSTOM requires explicit dates")
    }
}
```

---

### 2. UI Flow

#### Compact UI with Dropdown
```
Content Sync Section
├─ [Radio] Automatic
├─ [Radio] Manual
└─ [Radio] Date Range
    │
    └─ [Dropdown ▼ Last Month]  ← Space-efficient preset selector
        │
        └─ When "Custom" selected:
           ├─ From: [Button with date]
           └─ To:   [Button with date]
       └─ [Download] button
```

#### Visual Mockup Description

**State 1: Preset Selected (e.g., "Last Month")**
```
☑ Date Range
  ┌─────────────────────────────┐
  │ Last Month          [▼]     │  ← Compact dropdown, no date pickers
  └─────────────────────────────┘
  [Download] button
```

**State 2: Custom Selected**
```
☑ Date Range
  ┌─────────────────────────────┐
  │ Custom              [▼]     │
  └─────────────────────────────┘
  From: [2024-01-01] [Pick]     ← Date picker controls appear
  To:   [2024-12-31] [Pick]
  [Download] button
```

---

### 3. Default Values

When user first selects "Date Range" mode:
- **Preset:** `LAST_MONTH` (safe, common use case)
- **If preset is CUSTOM:**
  - **From:** First day of current month
  - **To:** Current date

This provides sensible defaults without requiring user action.

---

### 4. Implementation Plan

#### Phase 1: Data Layer
1. Add `DateRangePreset` enum to domain layer
2. Update `DateRangeParams` to include preset field
3. Update `SettingsDataStore` to persist preset selection
4. Create utility function: `DateRangePreset.toDateRange(today: LocalDate)`

#### Phase 2: ViewModel
1. Add `selectedPreset: StateFlow<DateRangePreset>` to `SyncSettingsViewModel`
2. Update date range validation logic:
   - If preset != CUSTOM: validate preset is supported
   - If preset == CUSTOM: validate from/to dates are set and valid
3. Update worker invocation:
   - Resolve preset to actual dates before passing to `DateRangeContentSyncWorker`
   - OR: Pass preset to worker and resolve there

#### Phase 3: UI
1. Create `DateRangePresetDropdown()` composable:
   - Show dropdown with options: Last Day, Last Week, Last Month, Last Year, Custom
   - Handle selection change
2. Update `ContentSyncSection()`:
   - Add dropdown below "Date Range" radio button
   - Show date pickers only when preset == CUSTOM
3. Update date picker logic:
   - Initialize from/to with current month defaults

#### Phase 4: Workers & Integration
1. Update `DateRangeContentSyncWorker.kt`:
   - Accept preset parameter (alternative: compute in ViewModel)
   - Log which preset was used
2. Update `SyncSettingsViewModel.onClickDateRangeDownload()`:
   - Resolve preset to actual dates
   - Pass resolved dates to worker

---

### 5. Code Organization

**New Files:**
- `app/src/main/java/com/mydeck/app/domain/sync/DateRangePreset.kt`
- `app/src/main/java/com/mydeck/app/ui/settings/composables/DateRangePresetDropdown.kt`

**Modified Files:**
- `SettingsDataStore.kt` - add preset get/save methods
- `SettingsDataStoreImpl.kt` - implement preset persistence
- `DateRangeParams.kt` - add preset field
- `SyncSettingsViewModel.kt` - add preset state and logic
- `SyncSettingsScreen.kt` - update UI layout

---

### 6. Edge Cases & Considerations

#### Backward Compatibility
- Existing saved date ranges (before presets):
  - Auto-migrate to `CUSTOM` preset with preserved dates
  - OR: Create migration in `SettingsDataStore` to detect old ranges

#### Timezone Handling
- All date calculations use `LocalDate` (no time component)
- "Today" is always in device's timezone
- Epoch timestamps still used for database queries (no change)

#### Download Button Logic
```kotlin
fun canDownloadWithDateRange(preset: DateRangePreset, from: LocalDate?, to: LocalDate?): Boolean {
    return when (preset) {
        CUSTOM -> from != null && to != null && from <= to
        else -> true  // Presets are always valid
    }
}
```

---

### 7. String Resources

**New strings to add to all locale files:**
```xml
<string name="sync_content_date_range_preset">Download period</string>
<string name="sync_date_preset_last_day">Last day</string>
<string name="sync_date_preset_last_week">Last week</string>
<string name="sync_date_preset_last_month">Last month</string>
<string name="sync_date_preset_last_year">Last year</string>
<string name="sync_date_preset_custom">Custom date range</string>
```

---

### 8. Alternative: Segmented Button Control

**Alternative to dropdown** if space allows:
```
Date Range: [Last Day] [Last Week] [Last Month] [Last Year] [Custom▼]
```

**Pros:**
- More discoverable (all options visible)
- Faster selection (no dropdown tap needed)

**Cons:**
- Takes more horizontal space
- May not fit on smaller screens
- More visual clutter

**Recommendation:** Start with dropdown for compatibility, can evolve to segmented buttons if desired.

---

### 9. Testing Considerations

**Unit Tests:**
- `DateRangePreset.toDateRange()` calculation correctness
- Date validation logic for CUSTOM ranges
- Backward compatibility migration

**UI Tests:**
- Dropdown interactions (open/close, selection)
- Date picker appears/disappears based on preset
- Download button enabled/disabled states
- Validation messages for invalid custom ranges

---

### 10. Future Enhancements

- Add "Quick sync from last download" option (uses last sync timestamp)
- Add "Relative ranges" (e.g., "last 2 weeks" not tied to calendar)
- Save favorite custom ranges as quick presets
- Analytics: track which preset is most frequently used

---

## Summary of Benefits

| Aspect | Before | After |
|--------|--------|-------|
| **Common Use Cases** | Manual entry every time | One tap to select |
| **Space Efficiency** | Always shows date pickers | Compact dropdown, pickers on-demand |
| **Discoverability** | Users must know to enter dates | Presets suggest available options |
| **Default Experience** | Requires manual entry | Smart defaults (last month) |
| **Flexibility** | Custom dates always available | Custom dates still available |

---

## Rollout Plan

1. **PR#1:** Implement data model + ViewModel changes (no UI changes yet)
2. **PR#2:** Add UI composables and connect to ViewModel
3. **PR#3:** Update strings and add to all locale files
4. **Testing:** Full integration testing with preset → worker flow
5. **Release:** Include in next version with release notes

