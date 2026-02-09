# Mini Spec: Configurable Log Retention with Enhanced Viewer

## Overview

Implement user-configurable log retention periods with automatic cleanup, enhanced log file viewing capabilities, and comprehensive log sharing. This addresses unlimited log growth while maintaining utility for mobile debugging.

---

## Problem Statement

**Current Issues**:
1. Logs grow indefinitely with no automatic cleanup
2. Only most recent log file (`.0.log`) is viewable in app
3. Older log files (`.1.log`, `.2.log`) are inaccessible to users
4. Share feature only shares single file, missing historical context
5. No user control over log retention duration

**Impact**:
- Storage concerns over time
- Incomplete diagnostic information when troubleshooting
- Users can't review or share complete log history

---

## Solution: Three-Part Enhancement

### 1. Configurable Log Retention
User-selectable retention periods with automatic cleanup on app start

### 2. Multi-File Viewer
Dropdown to switch between log files (when multiple exist)

### 3. Comprehensive Sharing
Share all log files as single ZIP archive

---

## Detailed Requirements

### Part 1: Log Retention Settings

#### Retention Options
| Option | Duration | Availability |
|--------|----------|--------------|
| 1 Day | 24 hours | Debug + Production |
| 1 Week | 7 days | Debug + Production |
| 1 Month | 30 days | Debug + Production |
| 3 Months | 90 days | Debug + Production |

#### Default Values
- **Debug builds**: 7 days (1 week)
- **Production builds**: 30 days (1 month)

#### Cleanup Behavior
- **Trigger**: App startup (`MyDeckApplication.onCreate()`)
- **Logic**: Delete log files where `file.lastModified()` < (current time - retention period)
- **Scope**: All files in log directory older than retention period

#### Configuration Storage
- **Location**: SettingsDataStore (DataStore preferences)
- **Key**: `log_retention_days` (Int)
- **Persistence**: Survives app updates, build type changes

---

### Part 2: Enhanced Log File Limits

#### Updated Treessence Configuration

**Debug Build**:
```kotlin
fileTree {
    level = 3              // Log.DEBUG
    fileName = LOGFILE
    dir = it.absolutePath
    fileLimit = 3          // Up from 2
    sizeLimit = 128        // 128KB per file (384KB total max)
    appendToFile = true
}
```

**Production Build**:
```kotlin
fileTree {
    level = 5              // Log.WARN (only warnings/errors)
    fileName = LOGFILE
    dir = it.absolutePath
    fileLimit = 3          // Up from 2
    sizeLimit = 128        // 128KB per file (384KB total max)
    appendToFile = true
}
```

#### Rationale
- **fileLimit = 3**: Better rotation granularity with time-based cleanup
- **sizeLimit = 128KB**: Balances rotation frequency with performance
  - ~1000-2000 log entries per file
  - 384KB total maximum storage
  - Won't rotate excessively during active debugging
- **level = 5 (WARN) in production**: Reduces noise, captures errors only

---

### Part 3: Multi-File Viewer UI

#### Behavior

**Single File Exists** (most common):
- No UI changes
- Shows `MyDeckAppLog.0.log` content as current behavior
- Clean, simple interface

**Multiple Files Exist**:
- Dropdown appears above log content
- Shows list of available log files
- User can switch between files
- Selected file content displayed below

#### UI Specification

**Dropdown (only visible when >1 file)**:
```
┌───────────────────────────────────────┐
│ File: MyDeckAppLog.0.log ▼ (Current)  │
└───────────────────────────────────────┘
```

**Dropdown expanded**:
```
┌───────────────────────────────────────┐
│ ✓ MyDeckAppLog.0.log (Current) 45 KB  │ ← Selected
│   MyDeckAppLog.1.log (Previous) 128 KB│
│   MyDeckAppLog.2.log (Oldest) 128 KB  │
└───────────────────────────────────────┘
```

**Complete screen layout**:
```
┌─────────────────────────────────────────┐
│ [← Back]  Logs    [Retention] [Share] [Clear] │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ File: MyDeckAppLog.0.log ▼ (45 KB) │ │ ← Only if >1 file
│ └─────────────────────────────────────┘ │
│                                         │
│ 2024-02-09 10:23:45 I/Sync: Fetched... │
│ 2024-02-09 10:23:46 D/DB: Inserted...  │
│ 2024-02-09 10:23:47 W/API: Timeout...  │
│ ...                                     │
└─────────────────────────────────────────┘
```

#### File Info Display
- **File name**: `MyDeckAppLog.N.log`
- **Label**:
  - `.0.log` → "(Current)"
  - `.1.log` → "(Previous)"
  - `.2.log` → "(Oldest)"
- **Size**: Human-readable (KB)

---

### Part 4: Retention Settings UI

#### Location
Log View Screen header (new icon button)

#### Icon
Calendar icon (Material Icons: `Icons.Default.CalendarMonth`)

#### Dialog Content
```
┌─────────────────────────────────┐
│     Keep Logs For               │
├─────────────────────────────────┤
│ ○ 1 Day                         │
│ ○ 1 Week                        │
│ ● 1 Month                       │ ← Currently selected
│ ○ 3 Months                      │
├─────────────────────────────────┤
│ Logs older than the selected    │
│ period are automatically        │
│ deleted when the app starts.    │
├─────────────────────────────────┤
│          [Cancel]  [Apply]      │
└─────────────────────────────────┘
```

---

### Part 5: Enhanced Sharing (ZIP All Files)

#### Share Button Behavior
- **Always creates ZIP** of all available log files
- **File name**: `mydeck-logs-YYYY-MM-DD-HHmmss.zip`
- **Location**: App cache directory (auto-cleaned by system)
- **Contents**: All log files sorted by name
  - `MyDeckAppLog.0.log` (most recent)
  - `MyDeckAppLog.1.log`
  - `MyDeckAppLog.2.log` (oldest)

#### Share Intent
- **Action**: `Intent.ACTION_SEND`
- **Type**: `application/zip`
- **Title**: "Share MyDeck Logs"
- **File provider**: Use existing FileProvider configuration

#### ZIP Creation
```kotlin
fun createLogFilesZip(context: Context): File? {
    val tree = Timber.forest().firstOrNull { it is FileLoggerTree } as FileLoggerTree?
    val logFiles = tree?.files ?: return null

    if (logFiles.isEmpty()) return null

    val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
    val zipFile = File(context.cacheDir, "mydeck-logs-$timestamp.zip")

    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
        logFiles.sortedBy { it.name }.forEach { file ->
            try {
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            } catch (e: Exception) {
                Timber.e(e, "Failed to add ${file.name} to zip")
            }
        }
    }

    return zipFile
}
```

---

## Implementation Plan

### Phase 1: Core Infrastructure (Foundation)

#### 1.1 Update SettingsDataStore
**File**: `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt`

**Add**:
```kotlin
private val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")

suspend fun saveLogRetentionDays(days: Int) {
    dataStore.edit { preferences ->
        preferences[LOG_RETENTION_DAYS] = days
    }
}

fun getLogRetentionDays(): Flow<Int> = dataStore.data.map { preferences ->
    preferences[LOG_RETENTION_DAYS] ?: getDefaultLogRetentionDays()
}

private fun getDefaultLogRetentionDays(): Int {
    return if (BuildConfig.DEBUG) 7 else 30
}
```

**Lines**: ~15

---

#### 1.2 Add Cleanup Logic
**File**: `app/src/main/java/com/mydeck/app/MyDeckApplication.kt`

**Modify `onCreate()`**:
```kotlin
override fun onCreate() {
    super.onCreate()
    cleanupOldLogs()  // NEW - before initTimberLog
    initTimberLog()
    Thread.setDefaultUncaughtExceptionHandler(CustomExceptionHandler(this))
}
```

**Add method**:
```kotlin
private fun cleanupOldLogs() {
    try {
        // Get retention setting (blocking is OK in onCreate before UI)
        val retentionDays = runBlocking {
            // Need SettingsDataStore injection - see below
            val dataStore = (this@MyDeckApplication as? HiltApp)?.settingsDataStore
            dataStore?.getLogRetentionDays()?.first() ?: getDefaultLogRetentionDays()
        }

        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)

        val logDir = File(filesDir, LOGDIR)
        if (logDir.exists() && logDir.isDirectory) {
            var deletedCount = 0
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                        Timber.tag("LOGDIR").i("Deleted old log file: ${file.name}")
                    }
                }
            }
            if (deletedCount > 0) {
                Timber.tag("LOGDIR").i("Cleanup complete: deleted $deletedCount old log file(s)")
            }
        }
    } catch (e: Exception) {
        // Silent failure - don't crash app due to log cleanup
        // Use printStackTrace since Timber may not be initialized yet
        e.printStackTrace()
    }
}

private fun getDefaultLogRetentionDays(): Int {
    return if (BuildConfig.DEBUG) 7 else 30
}
```

**Note**: MyDeckApplication needs SettingsDataStore injection. Add:
```kotlin
@Inject
lateinit var settingsDataStore: SettingsDataStore
```

**Lines**: ~30

---

#### 1.3 Update Treessence Configuration
**File**: `app/src/main/java/com/mydeck/app/MyDeckApplication.kt`

**Modify `initTimberLog()`**:
```kotlin
private fun initTimberLog() {
    val logDir = createLogDir(filesDir)
    startTimber {
        if (BuildConfig.DEBUG) {
            debugTree()
            logDir?.let {
                fileTree {
                    level = 3              // Log.DEBUG
                    fileName = LOGFILE
                    dir = it.absolutePath
                    fileLimit = 3          // Changed from 2
                    sizeLimit = 128        // NEW: 128KB per file
                    appendToFile = true
                }
            }
        } else {
            logDir?.let {
                fileTree {
                    level = 5              // Changed from 4 (INFO) to 5 (WARN)
                    fileName = LOGFILE
                    dir = it.absolutePath
                    fileLimit = 3          // Changed from 2
                    sizeLimit = 128        // NEW: 128KB per file
                    appendToFile = true
                }
            }
        }
    }
}
```

**Lines**: ~5 changes

---

### Phase 2: Enhanced Utilities

#### 2.1 Add Multi-File Support
**File**: `app/src/main/java/com/mydeck/app/util/LoggerUtil.kt`

**Add**:
```kotlin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LogFileInfo(
    val file: File,
    val name: String,
    val sizeKb: Int,
    val label: String  // "Current", "Previous", "Oldest"
)

fun getAllLogFiles(): List<LogFileInfo> {
    val tree = Timber.forest().firstOrNull { it is FileLoggerTree } as FileLoggerTree?
    val files = tree?.files?.sortedBy { it.name } ?: emptyList()

    return files.mapIndexed { index, file ->
        val label = when (index) {
            0 -> "Current"
            files.size - 1 -> "Oldest"
            else -> "Previous"
        }

        LogFileInfo(
            file = file,
            name = file.name,
            sizeKb = (file.length() / 1024).toInt(),
            label = label
        )
    }
}

fun createLogFilesZip(context: Context): File? {
    val tree = Timber.forest().firstOrNull { it is FileLoggerTree } as FileLoggerTree?
    val logFiles = tree?.files ?: return null

    if (logFiles.isEmpty()) return null

    val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
    val zipFile = File(context.cacheDir, "mydeck-logs-$timestamp.zip")

    try {
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            logFiles.sortedBy { it.name }.forEach { file ->
                try {
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(zip)
                    }
                    zip.closeEntry()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add ${file.name} to zip")
                }
            }
        }
        return zipFile
    } catch (e: Exception) {
        Timber.e(e, "Failed to create log zip file")
        zipFile.delete()  // Clean up partial file
        return null
    }
}
```

**Lines**: ~60

---

### Phase 3: ViewModel Updates

#### 3.1 Update LogViewViewModel
**File**: `app/src/main/java/com/mydeck/app/ui/settings/LogViewViewModel.kt`

**Add imports**:
```kotlin
import com.mydeck.app.util.LogFileInfo
import com.mydeck.app.util.getAllLogFiles
import com.mydeck.app.util.createLogFilesZip
import com.mydeck.app.io.prefs.SettingsDataStore
```

**Add to constructor**:
```kotlin
@HiltViewModel
class LogViewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore  // NEW
) : ViewModel() {
```

**Add state flows**:
```kotlin
private val _availableLogFiles = MutableStateFlow<List<LogFileInfo>>(emptyList())
val availableLogFiles: StateFlow<List<LogFileInfo>> = _availableLogFiles.asStateFlow()

private val _selectedLogFile = MutableStateFlow<File?>(null)
val selectedLogFile: StateFlow<File?> = _selectedLogFile.asStateFlow()

private val _logRetentionDays = settingsDataStore.getLogRetentionDays()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)
val logRetentionDays: StateFlow<Int> = _logRetentionDays

private val _showRetentionDialog = MutableStateFlow(false)
val showRetentionDialog: StateFlow<Boolean> = _showRetentionDialog.asStateFlow()
```

**Update init**:
```kotlin
init {
    loadAvailableLogFiles()
    onRefresh()
}

private fun loadAvailableLogFiles() {
    val files = getAllLogFiles()
    _availableLogFiles.value = files

    // Default to most recent file if not already selected
    if (_selectedLogFile.value == null && files.isNotEmpty()) {
        _selectedLogFile.value = files.first().file
    }
}
```

**Update onRefresh()**:
```kotlin
fun onRefresh() {
    Timber.d("refresh")
    viewModelScope.launch {
        loadAvailableLogFiles()  // Reload file list

        val file = _selectedLogFile.value ?: getLatestLogFile()

        _uiState.value = file?.let {
            Timber.d("file=$it")
            UiState.Success(
                logContent = it.readText(),
                shareIntentUri = Uri.EMPTY  // Not used anymore - we'll share ZIP
            )
        } ?: UiState.Error(R.string.log_view_no_log_file_found)
    }
}
```

**Add file selection**:
```kotlin
fun onSelectLogFile(file: File) {
    _selectedLogFile.value = file
    onRefresh()
}
```

**Update onShareLogs()**:
```kotlin
fun onShareLogs() {
    logAppInfo()
    viewModelScope.launch {
        val zipFile = createLogFilesZip(context)
        if (zipFile != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                zipFile
            )
            _navigationEvent.update {
                NavigationEvent.ShowShareDialog(uri, isZip = true)
            }
        } else {
            _navigationEvent.update { NavigationEvent.ShareError }
        }
    }
}
```

**Add retention dialog methods**:
```kotlin
fun onClickLogRetention() {
    _showRetentionDialog.value = true
}

fun onDismissRetentionDialog() {
    _showRetentionDialog.value = false
}

fun onSelectRetentionDays(days: Int) {
    viewModelScope.launch {
        settingsDataStore.saveLogRetentionDays(days)
        _showRetentionDialog.value = false
    }
}
```

**Update NavigationEvent**:
```kotlin
sealed class NavigationEvent {
    data object NavigateBack : NavigationEvent()
    data class ShowShareDialog(val uri: Uri, val isZip: Boolean = false) : NavigationEvent()
    data object ShareError : NavigationEvent()
    data object LogsCleared : NavigationEvent()
}
```

**Update UiState** (remove shareIntentUri):
```kotlin
sealed class UiState {
    data object Loading : UiState()
    data class Success(
        val logContent: String
        // Removed: shareIntentUri
    ) : UiState()
    data class Error(@StringRes val message: Int) : UiState()
}
```

**Lines**: ~80

---

### Phase 4: UI Implementation

#### 4.1 Add Retention Dialog
**File**: `app/src/main/java/com/mydeck/app/ui/settings/LogViewScreen.kt`

**Add composable**:
```kotlin
@Composable
private fun LogRetentionDialog(
    currentRetentionDays: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_retention_title)) },
        text = {
            Column {
                LogRetentionOption(1, R.string.log_retention_1_day, currentRetentionDays == 1, onSelect)
                LogRetentionOption(7, R.string.log_retention_1_week, currentRetentionDays == 7, onSelect)
                LogRetentionOption(30, R.string.log_retention_1_month, currentRetentionDays == 30, onSelect)
                LogRetentionOption(90, R.string.log_retention_3_months, currentRetentionDays == 90, onSelect)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.log_retention_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LogRetentionOption(
    days: Int,
    @StringRes labelRes: Int,
    isSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(days) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { onSelect(days) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(labelRes))
    }
}
```

**Lines**: ~50

---

#### 4.2 Add File Selector Dropdown
**File**: `app/src/main/java/com/mydeck/app/ui/settings/LogViewScreen.kt`

**Add composable**:
```kotlin
@Composable
private fun LogFileSelector(
    availableFiles: List<LogFileInfo>,
    selectedFile: File?,
    onSelectFile: (File) -> Unit
) {
    // Only show if multiple files exist
    if (availableFiles.size <= 1) return

    var expanded by remember { mutableStateOf(false) }

    val selectedFileInfo = availableFiles.find { it.file == selectedFile } ?: availableFiles.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFileInfo?.name ?: "Select file",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    selectedFileInfo?.let {
                        Text(
                            text = "${it.label} • ${it.sizeKb} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            availableFiles.forEach { fileInfo ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = fileInfo.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${fileInfo.label} • ${fileInfo.sizeKb} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelectFile(fileInfo.file)
                        expanded = false
                    },
                    leadingIcon = if (fileInfo.file == selectedFile) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}
```

**Lines**: ~80

---

#### 4.3 Update LogViewScreen Main Composable
**File**: `app/src/main/java/com/mydeck/app/ui/settings/LogViewScreen.kt`

**Update TopAppBar actions**:
```kotlin
actions = {
    // NEW: Retention settings icon
    IconButton(onClick = { viewModel.onClickLogRetention() }) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = stringResource(R.string.log_retention_title)
        )
    }

    // Existing share button
    IconButton(onClick = { viewModel.onShareLogs() }) {
        Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = stringResource(R.string.share)
        )
    }

    // Existing clear button
    IconButton(onClick = { viewModel.onClearLogs() }) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.log_view_clear_logs)
        )
    }
}
```

**Add file selector above log content**:
```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    // NEW: File selector (only visible if multiple files)
    val availableFiles by viewModel.availableLogFiles.collectAsState()
    val selectedFile by viewModel.selectedLogFile.collectAsState()

    LogFileSelector(
        availableFiles = availableFiles,
        selectedFile = selectedFile,
        onSelectFile = { viewModel.onSelectLogFile(it) }
    )

    // Existing log content
    when (val state = uiState) {
        is LogViewViewModel.UiState.Loading -> {
            // ... existing loading UI
        }
        is LogViewViewModel.UiState.Success -> {
            // ... existing success UI
        }
        is LogViewViewModel.UiState.Error -> {
            // ... existing error UI
        }
    }
}
```

**Add dialog observing**:
```kotlin
// In main LogViewScreen composable
val showRetentionDialog by viewModel.showRetentionDialog.collectAsState()
val currentRetentionDays by viewModel.logRetentionDays.collectAsState()

if (showRetentionDialog) {
    LogRetentionDialog(
        currentRetentionDays = currentRetentionDays,
        onDismiss = { viewModel.onDismissRetentionDialog() },
        onSelect = { days -> viewModel.onSelectRetentionDays(days) }
    )
}
```

**Update share intent handling**:
```kotlin
LaunchedEffect(navigationEvent) {
    when (val event = navigationEvent) {
        is LogViewViewModel.NavigationEvent.ShowShareDialog -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (event.isZip) "application/zip" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, event.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
            viewModel.onNavigationEventConsumed()
        }
        is LogViewViewModel.NavigationEvent.ShareError -> {
            // Show error snackbar
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.log_view_share_error),
                    duration = SnackbarDuration.Short
                )
            }
            viewModel.onNavigationEventConsumed()
        }
        // ... other events
    }
}
```

**Lines**: ~60

---

### Phase 5: Localization

#### 5.1 Add String Resources
**Files**:
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-de-rDE/strings.xml`
- `app/src/main/res/values-es-rES/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-gl-rES/strings.xml`
- `app/src/main/res/values-pl/strings.xml`
- `app/src/main/res/values-pt-rPT/strings.xml`
- `app/src/main/res/values-ru/strings.xml`
- `app/src/main/res/values-uk/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`

**Add to ALL language files** (English text as placeholder per CLAUDE.md):
```xml
<!-- Log retention settings -->
<string name="log_retention_title">Keep logs for</string>
<string name="log_retention_1_day">1 day</string>
<string name="log_retention_1_week">1 week</string>
<string name="log_retention_1_month">1 month</string>
<string name="log_retention_3_months">3 months</string>
<string name="log_retention_description">Logs older than the selected period are automatically deleted when the app starts.</string>
<string name="log_view_share_error">Failed to create log archive</string>
```

**Lines**: 7 strings × 10 locales = 70 lines total

---

### Phase 6: FileProvider Configuration

#### 6.1 Verify Cache Directory Access
**File**: `app/src/main/res/xml/file_paths.xml`

**Ensure cache directory is configured**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="log_cache" path="." />
    <!-- ... other paths -->
</paths>
```

If not present, add the cache-path entry.

**Lines**: ~1-2

---

## Testing Plan

### Unit Tests

#### LoggerUtil Tests
**File**: `app/src/test/java/com/mydeck/app/util/LoggerUtilTest.kt`

```kotlin
@Test
fun `getAllLogFiles returns empty list when no files exist`()

@Test
fun `getAllLogFiles returns files with correct labels`()

@Test
fun `getAllLogFiles sorts files by name`()

@Test
fun `createLogFilesZip creates valid zip with all files`()

@Test
fun `createLogFilesZip handles empty file list`()

@Test
fun `createLogFilesZip handles I-O errors gracefully`()
```

**Lines**: ~100

---

### Manual Testing Checklist

#### Part 1: Retention Settings
- [ ] **Default values**
  - [ ] Debug build defaults to 7 days
  - [ ] Production build defaults to 30 days
- [ ] **Setting persistence**
  - [ ] Change retention to 1 day → restart app → verify still 1 day
  - [ ] Change retention to 3 months → restart app → verify still 3 months
- [ ] **Cleanup behavior**
  - [ ] Set retention to 1 day → wait 2 days → restart app → verify old logs deleted
  - [ ] Generate logs → set retention to 1 week → verify logs within 7 days preserved
  - [ ] Verify cleanup doesn't crash app if log directory doesn't exist

#### Part 2: File Size Limits
- [ ] **Size limit enforcement**
  - [ ] Generate heavy logging → verify individual files don't exceed ~128KB
  - [ ] Verify rotation happens when size limit reached
  - [ ] Verify fileLimit=3 creates max 3 files
- [ ] **Log level**
  - [ ] Debug build: verify DEBUG level logs appear
  - [ ] Production build: verify only WARN/ERROR logs appear (no INFO/DEBUG)

#### Part 3: Multi-File Viewer
- [ ] **Single file scenario**
  - [ ] Fresh install (only .0.log exists) → verify no dropdown shown
  - [ ] Verify log content displays correctly
- [ ] **Multiple files scenario**
  - [ ] Generate logs until 2-3 files exist → verify dropdown appears
  - [ ] Verify dropdown shows all files with correct labels and sizes
  - [ ] Select .0.log → verify current logs displayed
  - [ ] Select .1.log → verify previous logs displayed
  - [ ] Select .2.log → verify oldest logs displayed
  - [ ] Switch between files → verify content updates correctly
- [ ] **File info accuracy**
  - [ ] Verify "Current", "Previous", "Oldest" labels correct
  - [ ] Verify file sizes displayed in KB
  - [ ] Verify checkmark on selected file

#### Part 4: Retention Dialog
- [ ] **Dialog display**
  - [ ] Tap calendar icon → verify dialog appears
  - [ ] Verify current selection highlighted
  - [ ] Verify all 4 options present (1 day, 1 week, 1 month, 3 months)
- [ ] **Selection behavior**
  - [ ] Select each option → verify saved correctly
  - [ ] Tap outside dialog → verify dismissed without changes
  - [ ] Cancel button → verify dismissed without changes
- [ ] **Description text**
  - [ ] Verify description text displayed and readable

#### Part 5: ZIP Sharing
- [ ] **Single file**
  - [ ] Only .0.log exists → share → verify ZIP contains 1 file
  - [ ] Extract ZIP → verify file readable
- [ ] **Multiple files**
  - [ ] 3 files exist → share → verify ZIP contains all 3 files
  - [ ] Extract ZIP → verify all files readable and correctly named
  - [ ] Verify files in ZIP sorted (.0, .1, .2)
- [ ] **Share intent**
  - [ ] Verify share sheet appears with ZIP attachment
  - [ ] Share via email → verify ZIP attached correctly
  - [ ] Share via messaging app → verify ZIP sent correctly
- [ ] **Error handling**
  - [ ] No log files exist → share → verify error message displayed
  - [ ] Insufficient storage → verify graceful error handling

#### Part 6: Integration Testing
- [ ] **Complete workflow**
  - [ ] Set retention to 1 month
  - [ ] Generate logs over several days
  - [ ] Switch between log files in viewer
  - [ ] Share logs as ZIP
  - [ ] Clear logs
  - [ ] Verify retention setting persists after clear
- [ ] **Edge cases**
  - [ ] Fresh install → no logs → verify empty state
  - [ ] App upgrade → verify existing logs preserved
  - [ ] App upgrade → verify retention setting migrates correctly
  - [ ] Low storage → verify cleanup doesn't crash app
  - [ ] Corrupted log file → verify app handles gracefully

#### Part 7: Localization
- [ ] Verify all 7 new strings present in all 10 language files
- [ ] Visual check: Strings fit in dialog/UI without truncation
- [ ] Test on different locale devices (if available)

---

## Implementation Estimates

| Phase | Component | Lines | Complexity | Time |
|-------|-----------|-------|------------|------|
| 1.1 | SettingsDataStore | 15 | Low | 15 min |
| 1.2 | MyDeckApplication cleanup | 30 | Low | 30 min |
| 1.3 | Treessence config updates | 5 | Low | 5 min |
| 2.1 | LoggerUtil enhancements | 60 | Medium | 45 min |
| 3.1 | LogViewViewModel updates | 80 | Medium | 1 hour |
| 4.1 | Retention dialog UI | 50 | Low | 30 min |
| 4.2 | File selector UI | 80 | Medium | 45 min |
| 4.3 | Screen integration | 60 | Medium | 45 min |
| 5.1 | Localization strings | 70 | Low | 15 min |
| 6.1 | FileProvider config | 2 | Low | 5 min |
| **Subtotal** | **Implementation** | **452** | - | **4.5 hours** |
| Testing | Unit tests | 100 | Medium | 1 hour |
| Testing | Manual testing | - | - | 2 hours |
| **Total** | **All phases** | **~550** | - | **7.5 hours** |

**Note**: Estimates assume familiarity with codebase and Jetpack Compose.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| ZIP creation fails on some devices | Low | Medium | Add try-catch, fallback to single file share |
| Treessence sizeLimit not working as expected | Low | Low | Already tested in research; can fall back to fileLimit only |
| Cleanup deletes too aggressively | Low | High | Test thoroughly with various retention periods |
| File selector dropdown doesn't fit on small screens | Medium | Low | Use scrollable dropdown, test on various screen sizes |
| Performance impact from multiple large log files | Low | Low | Size limits (128KB) and fileLimit (3) bound total size |
| Users confused by retention options | Low | Low | Clear description text in dialog |

---

## Success Criteria

✅ **Functional Requirements**:
- [ ] Users can configure log retention (1 day to 3 months)
- [ ] Old logs automatically deleted on app start per retention setting
- [ ] Users can view any available log file (not just most recent)
- [ ] Sharing creates ZIP with all log files
- [ ] Maximum log storage bounded to ~384KB

✅ **Non-Functional Requirements**:
- [ ] No performance degradation from cleanup logic
- [ ] No crashes from edge cases (no logs, corrupted files, etc.)
- [ ] UI responsive on all screen sizes
- [ ] Localization complete for all 10 languages

✅ **User Experience**:
- [ ] Simple default (single file, no dropdown clutter)
- [ ] Power user access (multi-file switching when needed)
- [ ] Foolproof sharing (always complete diagnostic info)
- [ ] Clear, understandable retention settings

---

## Future Enhancements (Out of Scope)

- Remote log upload to support server
- Log filtering/searching within app
- Export logs to external storage
- Automatic bug report creation with logs attached
- Crash report integration (Firebase Crashlytics)
- Real-time log streaming for debugging

---

## Dependencies

**External Libraries**:
- ✅ Treessence 1.1.2 (already in project)
- ✅ Timber 5.0.1 (already in project)
- ✅ Java ZIP API (standard library)
- ✅ Android FileProvider (standard library)

**Android APIs**:
- ✅ DataStore (already in use)
- ✅ FileProvider (already configured)
- ✅ Jetpack Compose (already in use)

**No new dependencies required** ✅

---

## Rollback Plan

If issues arise in production:

1. **Disable automatic cleanup**: Comment out `cleanupOldLogs()` call
2. **Revert to single file view**: Hide dropdown, show only .0.log
3. **Revert share to single file**: Use original share logic
4. **Revert Treessence config**: Change back to fileLimit=2, remove sizeLimit

All changes are additive and can be easily reverted without data loss.
