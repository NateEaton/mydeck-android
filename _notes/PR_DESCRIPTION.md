# Feature Specifications and Enhancement Proposals

## Summary

This PR adds five comprehensive specification documents covering critical improvements to MyDeck Android's sync functionality, reading experience, logging system, and accessibility features. These documents provide detailed implementation guidance for future development.

---

## Changes

### New Specification Documents

#### 1. **`_notes/manual-bookmark-sync-spec.md`** - Manual Bookmark Sync Feature
**Problem**: Users must wait for scheduled sync (up to 24 hours) to see bookmarks deleted on the server removed from the app. Manual sync (pull-to-refresh) only performs delta sync and doesn't detect deletions.

**Solution**: Add "Sync Bookmarks Now" button in Sync Settings that performs immediate full sync with deletion detection, bypassing the 24-hour gate.

**Key Features**:
- Manual trigger for full sync with deletion detection
- Reuses existing `FullSyncWorker` infrastructure
- Non-disruptive to scheduled sync behavior
- ~20-30 lines of new code + localization

**Architecture**:
```kotlin
// Option A: Force Full Sync Flag (Recommended)
val forceFullSync = inputData.getBoolean(INPUT_FORCE_FULL_SYNC, false)
val needsFullSync = forceFullSync || lastFullSyncTimestamp == null ||
    Clock.System.now() - lastFullSyncTimestamp > FULL_SYNC_INTERVAL
```

**Implementation**: 1-2 hours development, 30 minutes testing

---

#### 2. **`_notes/save-reading-progress-on-app-close-spec.md`** - Reading Progress Persistence
**Problem**: When users minimize or close the app while reading an article, scroll progress is lost. `ViewModel.onCleared()` is unreliable when the system kills the app process.

**Solution**: Add lifecycle observation in `BookmarkDetailScreen` to save progress when the screen goes to background using `Lifecycle.Event.ON_STOP`.

**Key Features**:
- Lifecycle observer triggers save on `ON_STOP` event
- Only applies to Article type (photos/videos already auto-complete)
- Progress persists even if app is killed by system
- ~20 lines of new code

**Implementation**:
```kotlin
DisposableEffect(lifecycleOwner, bookmarkId) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            val state = viewModel.uiState.value
            if (state is BookmarkDetailViewModel.UiState.Success &&
                state.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE) {
                viewModel.saveProgressOnPause()
            }
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

**Implementation**: 20 lines code, 10 minutes testing, very low risk

---

#### 3. **`_notes/logging-lifecycle-spec.md`** - Logging System Analysis
**Current State**:
- Logging active in BOTH debug and production builds (not just debug)
- No file size limits - only `fileLimit = 2` (file count)
- Files grow indefinitely with `appendToFile = true`
- No automatic cleanup (manual clear only)

**Issues Identified**:
- Unlimited file growth over weeks/months (potential MB of logs)
- Production logging impacts performance (disk I/O)
- Privacy concerns (logs may contain sensitive data)
- Most users never access logs page

**Analysis**: Three implementation options presented
- **Option A** (Recommended): Disable production file logging, use Crashlytics
- **Option B**: Add size limits and automatic cleanup
- **Option C**: Hybrid opt-in approach

**Recommendation**: Option A follows Android best practices
- Production apps typically don't log to files
- Remote crash reporting is industry standard
- Eliminates storage/performance/privacy concerns
- ~30 minutes implementation

---

#### 4. **`_notes/log-retention-hybrid-implementation-spec.md`** - Configurable Log Retention (Hybrid Solution)
**Rationale**: User values production file logging for mobile debugging (away from desk, no logcat access). Needs bounded growth with user control.

**Solution**: Three-part enhancement
1. **Configurable Retention**: User-selectable periods (1 day, 1 week, 1 month, 3 months)
2. **Multi-File Viewer**: Dropdown to switch between log files when multiple exist
3. **ZIP Sharing**: Share all log files as single archive for complete diagnostics

**Key Features**:
- **Retention settings**:
  - Defaults: 7 days (debug), 30 days (production)
  - Automatic cleanup on app startup
  - Persists across builds

- **Enhanced Treessence config**:
  ```kotlin
  fileLimit = 3          // Up from 2
  sizeLimit = 128        // 128KB per file (384KB max total)
  level = 5 (WARN)       // Production: errors/warnings only
  ```

- **Multi-file viewer**:
  - Dropdown appears only when >1 file exists
  - Shows file name, label (Current/Previous/Oldest), size
  - Switch between files seamlessly

- **ZIP sharing**:
  - Filename: `mydeck-logs-YYYY-MM-DD-HHmmss.zip`
  - Contains all available log files
  - Single package for support/debugging

**Implementation Plan**: 6 phases
1. Core infrastructure (SettingsDataStore, cleanup logic, Treessence config)
2. Enhanced utilities (multi-file support, ZIP creation)
3. ViewModel updates (state management, file selection)
4. UI implementation (retention dialog, file selector, screen integration)
5. Localization (7 new strings × 10 languages = 70 lines)
6. FileProvider configuration

**Estimates**:
- ~550 lines of code
- 7.5 hours total (4.5 dev + 3 testing)
- Low risk, additive changes only

**Triple Protection**:
1. Size-based rotation (sizeLimit): Hard cap per file
2. File count limit (fileLimit = 3): Circular logging
3. Time-based cleanup (retention): Remove old files on app start

---

#### 5. **`_notes/tts-feature-proposal.md`** - Text-to-Speech Feature
**Motivation**: Users want to listen to articles hands-free while commuting, exercising, or multitasking. Accessibility need for vision-impaired users.

**Solution**: Text-to-speech integration using Android's native `TextToSpeech` API, similar to Instapaper, Pocket, and Substack.

**Key Features**:
- **Entry point**: Article overflow menu → "Listen to article"
- **Playback controls**: Bottom sheet with play/pause/stop/speed controls
- **Background playback**: Foreground service with notification
- **Lock screen controls**: MediaSession integration for Bluetooth/headphones
- **Speed control**: 0.5x - 2.0x adjustable playback speed
- **Progress persistence**: Resume from last position
- **Smart text processing**: HTML cleaning, chunking, special case handling

**Architecture**:
```
BookmarkDetailScreen (UI)
    ↓
TtsController (State Management)
    ↓
TtsService (Foreground Service + MediaSession)
    ↓
Android TextToSpeech API
```

**Components**:
1. **TtsService**: Manages Android TTS, foreground notification, MediaSession
2. **TtsController**: State management, progress tracking, text processing
3. **TtsTextProcessor**: HTML cleaning, text chunking (4000 char max)
4. **PlaybackControlsUI**: Bottom sheet with controls

**User Experience**:
```
┌────────────────────────────────────────┐
│           Reading Article Title...     │
├────────────────────────────────────────┤
│  Progress: ████████░░░░░░░░░ 2:34/8:15│
│                                        │
│      [⏮] [⏸] [⏭]                     │
│                                        │
│  Speed: [0.5x][1.0x][1.5x][2.0x]      │
│                          [Stop] [X]    │
└────────────────────────────────────────┘
```

**Advantages vs Competitors**:
- ✅ **Free** (vs Instapaper $3/month)
- ✅ **Offline** (no network required)
- ✅ **Privacy** (no third-party API calls)
- ✅ **System voices** (quality depends on device)

**Implementation Phases**:
- **Phase 1**: Foundation (basic playback, simple controls) - 2-3 weeks
- **Phase 2**: Enhanced controls (speed, MediaSession, notifications) - 1-2 weeks
- **Phase 3**: Polish (persistence, error handling, testing) - 1-2 weeks

**Total Estimate**: 4-7 weeks (160-280 hours)
- Android development: 120-200 hours
- UI/UX design: 20-40 hours
- QA/testing: 20-40 hours

**Edge Cases Handled**:
- TTS not available on device
- Article has no content
- Language not supported
- Playback interruptions (phone calls, headphones disconnected)
- Large articles (>50k words)
- Memory constraints

**Future Enhancements** (Post-MVP):
- Sleep timer
- Premium AI voices (OpenAI, ElevenLabs) as paid feature
- Downloadable audio files
- Playlist mode (auto-play next article)
- CarPlay / Android Auto integration
- Wear OS controls

**Localization**: ~20 new strings × 10 languages = 200 lines

---

## Implementation Priority & Dependencies

### High Priority (User Impact)
1. **Manual Bookmark Sync** - Quick win, addresses immediate user frustration
2. **Reading Progress on Close** - Prevents data loss, improves UX

### Medium Priority (System Health)
3. **Log Retention** - Prevents storage issues, maintains debugging capability

### Feature Additions (Enhancement)
4. **Text-to-Speech** - Significant feature, requires dedicated sprint(s)

### Dependencies
- None between specs - all can be implemented independently
- TTS requires Android API 21+ (already met)
- Log retention builds on existing Treessence 1.1.2

---

## Technical Details

### Localization Requirements (per CLAUDE.md)
All new strings must be added to **10 language files** with English placeholders:
- `values/strings.xml` (English)
- `values-de-rDE/strings.xml` (German)
- `values-es-rES/strings.xml` (Spanish)
- `values-fr/strings.xml` (French)
- `values-gl-rES/strings.xml` (Galician)
- `values-pl/strings.xml` (Polish)
- `values-pt-rPT/strings.xml` (Portuguese)
- `values-ru/strings.xml` (Russian)
- `values-uk/strings.xml` (Ukrainian)
- `values-zh-rCN/strings.xml` (Simplified Chinese)

**Total localization burden**:
- Manual Sync: 3 strings × 10 = 30 lines
- Reading Progress: 0 strings (no UI text)
- Log Retention: 7 strings × 10 = 70 lines
- TTS: 20 strings × 10 = 200 lines
- **Total: 300 localization lines**

### No Code Changes in This PR
These are **specification documents only** - no implementation code is included. They serve as:
- Design documentation for future development
- Reference for code review and testing
- Basis for task breakdown and estimation
- Architecture decision records
- Risk mitigation planning

---

## Testing Strategy

Each spec includes comprehensive testing plans:
- **Unit tests** for business logic
- **Integration tests** for component interaction
- **Manual testing checklists** for user flows
- **Accessibility testing** (TalkBack compatibility)
- **Performance testing** (battery, memory, ANR)
- **Edge case coverage**

---

## Risk Assessment Summary

| Feature | Risk Level | Key Risks | Mitigation |
|---------|-----------|-----------|------------|
| Manual Sync | Low | Conflicts with scheduled sync | Reuses existing infrastructure |
| Reading Progress | Very Low | Lifecycle edge cases | Comprehensive testing |
| Log Retention | Low | Over-aggressive cleanup | Configurable, user-controlled |
| TTS | Medium | Device compatibility, battery drain | Graceful fallbacks, timeout |

---

## Success Metrics

### Manual Bookmark Sync
- [ ] Deleted bookmarks sync within seconds (vs hours)
- [ ] No interference with scheduled sync
- [ ] <2% error rate

### Reading Progress
- [ ] 100% progress preservation on app close
- [ ] No performance impact
- [ ] Works across all Android versions

### Log Retention
- [ ] Storage bounded to <1MB
- [ ] 15%+ users adjust retention settings
- [ ] Zero storage-related issues

### Text-to-Speech
- [ ] 15%+ adoption within 3 months
- [ ] 60%+ completion rate for short articles
- [ ] <2% TTS error rate

---

## Future Considerations

### Upcoming Readek 0.22 API
The manual bookmark sync spec acknowledges that Readek 0.22 will introduce a new `/api/sync` endpoint. When available:
- Manual sync button can leverage improved API
- No UI changes needed
- Backend implementation swap only

### Premium Features
TTS proposal includes path to monetization:
- Premium AI voices (ElevenLabs, OpenAI) as paid tier
- Downloadable audio files
- Advanced playback features

---

## Documentation Quality

Each specification includes:
- ✅ Problem statement and motivation
- ✅ User stories and use cases
- ✅ Technical architecture diagrams
- ✅ Detailed implementation plan
- ✅ Code examples and snippets
- ✅ UI/UX mockups
- ✅ Edge case analysis
- ✅ Testing checklist
- ✅ Risk assessment
- ✅ Time estimates
- ✅ Success criteria
- ✅ Future enhancements
- ✅ Alternative approaches considered

**Total Documentation**: ~3,500 lines of comprehensive specifications

---

## Next Steps

These specifications are ready for:

1. **Team Review & Feedback**
   - Architecture discussion (especially logging options)
   - UI/UX review of proposed interfaces
   - Technical feasibility validation

2. **Prioritization & Planning**
   - Assign to sprints based on business value
   - Break down into implementable tasks
   - Resource allocation

3. **Implementation**
   - Create feature branches from these specs
   - Implement with test-driven development
   - Iterate based on user feedback

4. **Future Enhancements**
   - Use as living documents
   - Update as requirements evolve
   - Track decisions and rationale

---

## Related Issues

- Manual sync addresses user frustration with delayed deletion sync
- Reading progress prevents data loss on app termination
- Log retention balances debugging needs with storage concerns
- TTS adds competitive parity with Instapaper/Pocket

---

## Session Context

All specifications developed in collaborative session: https://claude.ai/code/session_01Qy5s2G5gUpuCPJhUXuVanR

Branch: `claude/manual-bookmark-sync-vyqXN`
