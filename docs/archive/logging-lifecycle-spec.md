# Mini Spec: Logging Lifecycle and Management

## Current Implementation Analysis

### Logging Configuration

**Location**: `MyDeckApplication.kt:21-47`

**Library**: Treessence v1.1.2 (Timber file logging wrapper)

**Configuration**:
```kotlin
// DEBUG builds
- Log level: Log.DEBUG (level 3) - verbose
- File limit: 2 files
- Append mode: true (persists across sessions)
- Location: app.filesDir/logs/

// RELEASE builds
- Log level: Log.INFO (level 4) - less verbose
- File limit: 2 files
- Append mode: true (persists across sessions)
- Location: app.filesDir/logs/
```

**Key Finding**: **Logging is ACTIVE in production releases**, not just debug builds.

---

## Current Issues

### 1. **Unlimited File Growth**
- `fileLimit = 2` means max 2 files, but **no size limit per file**
- `appendToFile = true` means logs accumulate indefinitely within those 2 files
- Over weeks/months, each file could grow to **megabytes or tens of megabytes**
- No automatic rotation based on size or age

**Impact**:
- User who never checks logs page → files grow forever
- Large files slow down log viewing (reads entire file into memory: `it.readText()` in LogViewViewModel.kt:47)
- Potential storage concerns on devices with limited space

### 2. **Production Logging Performance Impact**
- Every log statement in production writes to disk
- I/O operations on main thread could cause ANR (Application Not Responding) if blocking
- Treessence uses async logging, mitigating but not eliminating this concern

### 3. **No Automatic Cleanup**
- Manual clear only (`clearLogFiles()` in LoggerUtil.kt:42-52)
- Requires user to navigate to Settings → Logs → Clear
- Most users never visit this screen
- Clear only empties files (`file.writeText("")`), doesn't delete them

### 4. **Treessence `fileLimit` Behavior**
With `fileLimit = 2`:
- File 1: `MyDeckAppLog.0.log`
- File 2: `MyDeckAppLog.1.log`

When file 1 fills up (based on internal buffer, not size limit):
- Treessence creates a new file, shifts numbering
- Oldest file beyond limit is deleted

**However**: Without a max file size configured, files can grow very large before rotation happens.

---

## Android Best Practices

### Production Logging

**Industry Standard**:
1. **Minimal or no local file logging in production**
   - Remote crash reporting: Firebase Crashlytics, Sentry, Bugsnag
   - Analytics: Firebase Analytics, Amplitude
   - Only log ERRORs and WARNs, not DEBUG/INFO

2. **If file logging is needed**:
   - Set max file size (e.g., 500KB - 1MB per file)
   - Limit file count (2-5 files)
   - Implement automatic cleanup:
     - Delete logs older than 7-30 days
     - Delete logs on app update
     - Enforce total log directory size limit (e.g., 5MB max)

3. **User privacy concerns**:
   - Logs may contain sensitive data (URLs, user actions, server responses)
   - Should be opt-in or clearly disclosed in privacy policy
   - Should not be uploaded without user consent

### Debug Logging

**Standard Approach**:
- Verbose logging (DEBUG level) is fine
- File logging helpful for development/testing
- Can be more permissive with size limits
- Consider disabling in release builds entirely

---

## Recommended Changes

### Option A: Disable File Logging in Production (Recommended)

**Rationale**: Most Android apps don't log to files in production. Remote crash reporting is the standard.

**Implementation**:
```kotlin
// MyDeckApplication.kt
private fun initTimberLog() {
    val logDir = createLogDir(filesDir)
    startTimber {
        if (BuildConfig.DEBUG) {
            debugTree()
            logDir?.let {
                fileTree {
                    level = 3 // Log.DEBUG
                    fileName = LOGFILE
                    dir = it.absolutePath
                    fileLimit = 5  // Increased
                    sizeLimit = 1024 * 1024  // 1MB per file (if supported)
                    appendToFile = true
                }
            }
        } else {
            // Production: console logging only (for logcat during development)
            // No file logging
            debugTree()  // Still logs to logcat for adb debugging
        }
    }
}
```

**Benefits**:
- ✅ No storage concerns for end users
- ✅ No performance impact from disk I/O
- ✅ No privacy concerns from log files
- ✅ Standard Android practice
- ✅ Still have crash handler for unhandled exceptions

**Trade-offs**:
- ❌ Can't retrieve logs from user devices for debugging issues
- ❌ Users can't share logs for support
- Mitigation: Add Firebase Crashlytics or similar remote logging

---

### Option B: Add Size Limits to Production Logging

**Rationale**: Keep file logging but make it bounded and safe.

**Implementation**:

1. **Add size limit configuration** (if Treessence supports it):
```kotlin
fileTree {
    level = 4 // Log.INFO
    fileName = LOGFILE
    dir = it.absolutePath
    fileLimit = 3  // 3 files for better rotation
    sizeLimit = 512 * 1024  // 512KB per file (1.5MB total max)
    appendToFile = true
}
```

2. **Add automatic cleanup on app start**:
```kotlin
// MyDeckApplication.kt:15
override fun onCreate() {
    super.onCreate()
    cleanupOldLogs()  // NEW
    initTimberLog()
    Thread.setDefaultUncaughtExceptionHandler(CustomExceptionHandler(this))
}

private fun cleanupOldLogs() {
    try {
        val logDir = File(filesDir, LOGDIR)
        if (logDir.exists() && logDir.isDirectory) {
            val maxAge = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) // 7 days
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < maxAge) {
                    file.delete()
                    Timber.tag("LOGDIR").i("Deleted old log file: ${file.name}")
                }
            }

            // Also enforce total directory size
            val maxTotalSize = 5 * 1024 * 1024L // 5MB
            val totalSize = logDir.listFiles()?.sumOf { it.length() } ?: 0
            if (totalSize > maxTotalSize) {
                // Delete oldest files until under limit
                logDir.listFiles()
                    ?.sortedBy { it.lastModified() }
                    ?.takeWhile { totalSize > maxTotalSize }
                    ?.forEach { it.delete() }
            }
        }
    } catch (e: Exception) {
        // Silent failure - don't crash app due to log cleanup
    }
}
```

3. **Change log level in production to WARN**:
```kotlin
else {
    logDir?.let {
        fileTree {
            level = 5 // Log.WARN - only warnings and errors
            fileName = LOGFILE
            dir = it.absolutePath
            fileLimit = 3
            sizeLimit = 512 * 1024
            appendToFile = true
        }
    }
}
```

**Benefits**:
- ✅ Users can still share logs for support
- ✅ Bounded storage (max ~5MB)
- ✅ Automatic cleanup
- ✅ Retains recent history (7 days)
- ✅ Less verbose (WARN level instead of INFO)

**Trade-offs**:
- ⚠️ Still some performance overhead from disk I/O
- ⚠️ Requires maintenance of cleanup logic
- ⚠️ Log files may still contain sensitive data

---

### Option C: Hybrid Approach (Best of Both)

**Rationale**: Disable file logging by default, but allow opt-in for power users/debugging.

**Implementation**:

1. **Add settings toggle**:
```kotlin
// SettingsDataStore
suspend fun saveEnableFileLogging(enabled: Boolean)
fun getEnableFileLogging(): Flow<Boolean>
```

2. **Initialize logging conditionally**:
```kotlin
private fun initTimberLog() {
    startTimber {
        if (BuildConfig.DEBUG) {
            debugTree()
            setupFileLogging(level = 3, fileLimit = 5)
        } else {
            // Production: console only by default
            debugTree()

            // Check user preference for file logging
            runBlocking {
                if (settingsDataStore.getEnableFileLogging().first()) {
                    setupFileLogging(level = 5, fileLimit = 3)  // WARN level
                }
            }
        }
    }
}
```

3. **Add UI in Settings**:
```
Advanced Settings
  ├── Enable Debug Logging (toggle)
  │   └── Warning: May impact performance and use storage
  └── View Logs (only visible if enabled)
```

**Benefits**:
- ✅ Safe default (no production file logging)
- ✅ Power users can enable for debugging
- ✅ Clear user consent
- ✅ No unexpected storage usage

---

## Recommended: Option A + Crashlytics

**Rationale**: Follow Android best practices while maintaining debugging capability.

**Implementation Plan**:

1. **Disable file logging in production**
   - Keep debug file logging for development
   - Remove production file tree configuration

2. **Add Firebase Crashlytics** (or alternative)
   - Automatic crash reporting
   - Remote logging for production issues
   - No local storage concerns

3. **Update LogViewScreen**
   - Show message in production: "Logs only available in debug builds"
   - Or hide the screen entirely in release builds

4. **Keep CustomExceptionHandler**
   - Still logs crashes to Crashlytics before app terminates
   - No local file needed

**Code changes**:
```kotlin
// MyDeckApplication.kt
private fun initTimberLog() {
    startTimber {
        if (BuildConfig.DEBUG) {
            debugTree()
            createLogDir(filesDir)?.let { logDir ->
                fileTree {
                    level = 3
                    fileName = LOGFILE
                    dir = logDir.absolutePath
                    fileLimit = 5
                    sizeLimit = 1024 * 1024  // 1MB per file
                    appendToFile = true
                }
            }
        } else {
            // Production: Crashlytics tree only
            debugTree()  // Still logs to logcat for adb debugging
            // Crashlytics.setTimberTree() if using Crashlytics
        }
    }
}
```

---

## Testing Checklist

### Current Behavior Test
- [ ] Install app, use for 1 week → Check log file sizes
- [ ] Never visit logs page → Verify files still grow
- [ ] Fill logs with 1000+ entries → Check file sizes and app performance
- [ ] Check log files on production build → Confirm they exist and grow

### After Changes Test (Option A)
- [ ] Production build → Verify no log files created in app.filesDir/logs
- [ ] Debug build → Verify logs still work
- [ ] Crash app in production → Verify Crashlytics receives report

### After Changes Test (Option B)
- [ ] Use app for 10 days → Verify old logs auto-deleted after 7 days
- [ ] Generate large volume of logs → Verify individual file max 512KB
- [ ] Check total log directory size → Verify max 5MB
- [ ] Production vs Debug → Verify different log levels

---

## Recommendation Summary

**For MyDeck Android**: Use **Option A** (disable production file logging)

**Reasoning**:
1. MyDeck is a read-later app, not a development tool - users don't need logs
2. Most issues can be debugged via logcat during development
3. Critical crashes can be caught by crash reporting services
4. Eliminates storage/performance concerns entirely
5. Follows Android best practices
6. Privacy-friendly (no log files on user devices)

**If you need production logs** (e.g., for support troubleshooting):
- Use **Option C** (opt-in file logging) or **Option B** with strict size limits
- Add Firebase Crashlytics for automatic crash reporting
- Consider remote logging service (Sentry, Bugsnag) for production debugging

---

## Implementation Estimate

**Option A**: 30 minutes
- Modify MyDeckApplication.kt: 5 lines changed
- Update LogViewScreen to show "Debug only" message: 10 lines
- Test in debug + release builds: 15 minutes

**Option B**: 2-3 hours
- Add cleanup logic: 30 lines
- Add size limit configuration: 5 lines
- Test cleanup behavior: 1 hour
- Test size limits: 30 minutes
- Edge case testing: 1 hour

**Option C**: 3-4 hours
- Add settings toggle: 20 lines (DataStore + UI)
- Conditional initialization: 15 lines
- UI updates: 30 lines
- Testing: 2 hours
