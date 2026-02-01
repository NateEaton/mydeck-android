# Battery Constraint Sync Issue - Analysis & Recommendations

**Date:** 2026-02-01
**Status:** Fixed (immediate), Recommendations for future enhancement
**Severity:** Critical - Silent failure blocking all content sync

---

## Problem Summary

Article content was failing to sync when device battery was low (<15%), leaving users with bookmark metadata but no readable content. The sync status showed "0/7" instead of "7/7" with no user feedback about why sync was blocked.

### Symptoms

- Bookmarks visible in list view with thumbnails and metadata
- No article content available when opening bookmarks
- Sync status showing 0/N instead of N/N
- Debug info showing:
  - `State: LOADED`
  - `Has Article: true` (server has content)
  - `Has Article Content: false` (local database empty)
- Logs showing "Batch article loader enqueued successfully" but no execution logs

### Root Cause

`BatchArticleLoadWorker` had a battery constraint:

```kotlin
.setRequiresBatteryNotLow(true)  // Line 78 in BatchArticleLoadWorker.kt
```

Android considers battery levels below 15% as "low battery", preventing the WorkManager from executing the worker. This was a **silent failure** - the worker was queued but never ran, with no user feedback.

---

## Diagnosis Process

### Evidence from Logs

```
02-01 01:14:19:206 D/LoadBookmarksUseCase: Batch article loader enqueued successfully
```
✅ Worker successfully queued

```
[No BatchArticleLoadWorker starting logs found]
```
❌ Worker never executed due to battery constraint

### Evidence from Debug Info

```
ID: FSXFYK6GbWJ9smsTDRddRP
State: LOADED
Loaded: true
Has Article: true
Has Article Content: false

Article Resource: https://read.eatonfamily.net/api/bookmarks/.../article
```

The bookmark was in LOADED state, server confirmed it has article content, but local database had no content - clear indication of failed article download.

### Evidence from Device State

User's battery was at exactly 15% (visible in status bar screenshot), triggering Android's low battery threshold.

---

## Immediate Fix

Removed battery constraint from both locations:

**LoadBookmarksUseCase.kt (line 94-114):**
```kotlin
private fun enqueueBatchArticleLoader() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        // Removed: .setRequiresBatteryNotLow(true)
        .build()
    // ...
}
```

**BatchArticleLoadWorker.kt (line 75-89):**
```kotlin
fun enqueue(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        // Removed: .setRequiresBatteryNotLow(true)
        .build()
    // ...
}
```

### Result

After charging battery above 15%, all article content synced successfully. The fix ensures content sync works regardless of battery level, only requiring network connectivity.

---

## Research: Industry Best Practices (2025-2026)

### Key Findings

Based on current Android development best practices:

1. **Adaptive Runtime Checking** (preferred over rigid constraints)
   - Use minimal base constraints (e.g., NetworkType.CONNECTED)
   - Check battery level and charging state inside the worker
   - Adapt behavior at runtime based on conditions

2. **User Feedback is Critical**
   - Silent failures kill user experience
   - Use WorkInfo/LiveData to show sync progress
   - Provide transparency about background task status

3. **Context-Aware Sync Strategies**
   - Different behavior for different battery levels
   - User-initiated actions should bypass battery restrictions
   - Background batch operations can be battery-aware

4. **Google Play Store Changes (March 2026)**
   - New warnings for battery-draining apps
   - Apps should leverage WorkManager with appropriate constraints
   - Battery optimization is becoming more important

### Sources

- [WorkManager in 2025: 5 Patterns That Actually Work](https://medium.com/@hiren6997/workmanager-in-2025-5-patterns-that-actually-work-in-production-fde952c0d095)
- [Android WorkManager Best Practices](https://medium.com/@nachare.reena8/android-workmanager-overview-best-practices-and-when-to-avoid-it-5d857977330a)
- [Optimize Battery Use - Android Developers](https://developer.android.com/develop/background-work/background-tasks/optimize-battery)
- [Monitor Battery Level - Android Developers](https://developer.android.com/training/monitoring-device-state/battery-monitoring)

---

## Recommendations for Future Enhancement

### Option 1: Hybrid Strategy (Recommended)

Distinguish between user-initiated and background sync:

#### User Creates Single Bookmark
```kotlin
// In BookmarkRepositoryImpl.enqueueArticleDownload()
private fun enqueueArticleDownload(bookmarkId: String) {
    val request = OneTimeWorkRequestBuilder<LoadArticleWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                // NO battery constraint - user wants this NOW
                .build()
        )
        .build()
    workManager.enqueue(request)
}
```

#### Background Batch Sync
```kotlin
// In BatchArticleLoadWorker.doWork()
override suspend fun doWork(): Result {
    val batteryLevel = getBatteryLevel()
    val isCharging = isCharging()

    val strategy = when {
        isCharging -> SyncStrategy.ALL
        batteryLevel < 15 -> SyncStrategy.CRITICAL_ONLY  // First 3-5 articles
        batteryLevel < 30 -> SyncStrategy.CONSERVATIVE   // Smaller batches, longer delays
        else -> SyncStrategy.NORMAL
    }

    syncWithStrategy(pendingIds, strategy)
}
```

### Option 2: UI Status Indicators

Add visible sync status so users understand what's happening:

**In Settings/Sync Screen:**
```
Content Sync Status: 5 / 12 synced
⏸ 7 articles waiting (low battery)
[Sync Now Anyway] button
```

**In Main List (subtle indicator):**
```
📥 Syncing content... (5/12)
```

**In Bookmark Details:**
```
✓ Content available
or
⏳ Content pending sync
```

### Option 3: Adaptive Batch Sync

Implement battery-aware sync strategy:

```kotlin
sealed class SyncStrategy {
    object ALL : SyncStrategy() {
        override val batchSize = 5
        override val delayMs = 500L
    }

    object CONSERVATIVE : SyncStrategy() {
        override val batchSize = 2
        override val delayMs = 2000L
    }

    object CRITICAL_ONLY : SyncStrategy() {
        override val maxArticles = 3
        override val batchSize = 1
        override val delayMs = 3000L
    }
}
```

### Option 4: Smart Notifications

Notify users when sync is deferred:

```
"Article sync paused (battery low)"
"Tap to sync anyway or wait until charging"
[Sync Now] [Dismiss]
```

---

## Implementation Priority

### Immediate (Already Done)
- ✅ Remove battery constraint to fix silent failure
- ✅ Document issue and findings

### Short-term (Quick Wins)
1. **Add sync status indicator** showing "X/Y articles synced"
2. **Add content status in bookmark details** (so users can see if specific article has content)
3. **Add "Force Sync" button** in settings to manually trigger content download

### Medium-term (Better UX)
1. **Implement runtime battery checking** in BatchArticleLoadWorker
2. **Add sync status UI** with progress indicator
3. **Differentiate user vs background sync** (user actions bypass battery, background respects it)

### Long-term (Full Solution)
1. **Adaptive sync strategy** based on battery level and charging state
2. **Smart notifications** for deferred syncs
3. **Granular sync controls** in settings (e.g., "Sync on low battery", "Max articles when battery low")

---

## Code Examples

### Battery Level Helper

```kotlin
// Add to utilities or worker
private fun getBatteryLevel(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
}

private fun isCharging(context: Context): Boolean {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.isCharging
}
```

### Adaptive Sync Implementation

```kotlin
@HiltWorker
class BatchArticleLoadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookmarkDao: BookmarkDao,
    private val loadArticleUseCase: LoadArticleUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val batteryLevel = getBatteryLevel(applicationContext)
        val isCharging = isCharging(applicationContext)

        Timber.d("Starting batch sync - battery: $batteryLevel%, charging: $isCharging")

        val pendingIds = bookmarkDao.getBookmarkIdsWithoutContent()

        if (pendingIds.isEmpty()) {
            return Result.success()
        }

        // Determine sync strategy based on battery state
        val strategy = when {
            isCharging -> {
                Timber.i("Charging - full sync of ${pendingIds.size} articles")
                SyncStrategy.ALL
            }
            batteryLevel < 15 -> {
                Timber.i("Low battery - syncing first 3 articles only")
                SyncStrategy.CRITICAL_ONLY
            }
            batteryLevel < 30 -> {
                Timber.i("Medium battery - conservative sync")
                SyncStrategy.CONSERVATIVE
            }
            else -> {
                Timber.i("Normal battery - full sync")
                SyncStrategy.NORMAL
            }
        }

        return syncWithStrategy(pendingIds, strategy)
    }

    private suspend fun syncWithStrategy(
        pendingIds: List<String>,
        strategy: SyncStrategy
    ): Result {
        val idsToSync = when (strategy) {
            is SyncStrategy.CRITICAL_ONLY -> pendingIds.take(strategy.maxArticles)
            else -> pendingIds
        }

        idsToSync.chunked(strategy.batchSize).forEachIndexed { index, batch ->
            coroutineScope {
                batch.map { id ->
                    async {
                        try {
                            loadArticleUseCase.execute(id)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load article $id")
                        }
                    }
                }.awaitAll()
            }

            if (index < idsToSync.chunked(strategy.batchSize).size - 1) {
                delay(strategy.delayMs)
            }
        }

        if (pendingIds.size > idsToSync.size) {
            Timber.i("Deferred ${pendingIds.size - idsToSync.size} articles due to battery")
            // TODO: Show notification or update UI status
        }

        return Result.success()
    }

    sealed class SyncStrategy {
        abstract val batchSize: Int
        abstract val delayMs: Long

        object ALL : SyncStrategy() {
            override val batchSize = 5
            override val delayMs = 500L
        }

        object NORMAL : SyncStrategy() {
            override val batchSize = 5
            override val delayMs = 500L
        }

        object CONSERVATIVE : SyncStrategy() {
            override val batchSize = 2
            override val delayMs = 2000L
        }

        data class CRITICAL_ONLY(val maxArticles: Int = 3) : SyncStrategy() {
            override val batchSize = 1
            override val delayMs = 3000L
        }
    }
}
```

---

## Testing Checklist

When implementing battery-aware sync:

- [ ] Test sync with battery at 100%, 50%, 30%, 15%, 10%
- [ ] Test sync while charging vs not charging
- [ ] Test user-initiated bookmark creation at low battery (should work)
- [ ] Test background sync at low battery (should adapt or defer)
- [ ] Verify UI shows accurate sync status
- [ ] Verify notifications appear when sync is deferred
- [ ] Test "Force Sync" button bypasses battery constraints
- [ ] Test on different Android versions (WorkManager behavior varies)
- [ ] Test on low-end devices (2GB RAM) vs flagships
- [ ] Verify no excessive battery drain from sync operations

---

## Conclusion

The immediate fix (removing battery constraint) resolves the critical issue of silent sync failure. However, battery-aware sync is still valuable for background operations. The recommended approach is:

1. **User actions** = No battery constraint (users want content now)
2. **Background batch sync** = Adaptive strategy based on battery level
3. **UI transparency** = Always show sync status to avoid confusion

This balances user experience (getting content when needed) with battery optimization (respecting device constraints for background operations).
