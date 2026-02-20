# Feature Proposal: Text-to-Speech for Articles

## Overview

Add text-to-speech (TTS) capability to article reading view, allowing users to listen to article content with natural-sounding voices, similar to features in Instapaper, Pocket, and Substack.

---

## Motivation

### User Problems
1. **Hands-free reading**: Users want to consume articles while commuting, exercising, or doing other activities
2. **Accessibility**: Vision-impaired users need audio alternatives to visual reading
3. **Multitasking**: Users want to "read" articles while doing other tasks
4. **Eye strain**: Long reading sessions cause fatigue; audio provides relief
5. **Learning preferences**: Some users prefer or learn better through audio

### Market Context
- **Instapaper**: Premium feature with natural voices, speed control, background play
- **Pocket**: Built-in "Listen" feature with voice selection
- **Substack**: "Listen to this post" with AI-generated narration
- **Medium**: Partner with Google for TTS
- **Apple News**: Read aloud with Siri voices

**User expectation**: Modern read-later apps should support audio playback.

---

## User Stories

### Primary Use Cases

**As a commuter**, I want to listen to articles during my drive so I can stay informed without looking at my phone.

**As a visually impaired user**, I need audio narration of articles so I can consume content independently.

**As a multitasker**, I want to listen to articles while cooking/cleaning so I can make productive use of time.

**As an ESL learner**, I want to hear articles read aloud so I can improve pronunciation and comprehension.

**As a long-form reader**, I want to switch between reading and listening so I can reduce eye strain during long articles.

### Secondary Use Cases

**As a speed reader**, I want to adjust playback speed so I can consume content faster.

**As a premium user**, I want high-quality voices so the listening experience feels natural.

**As a mobile user**, I want background playback so I can minimize the app and continue listening.

---

## Proposed Solution

### Feature Scope

#### In Scope (MVP)
- ✅ TTS for article content (Article type only, not photos/videos)
- ✅ Basic playback controls (play, pause, stop)
- ✅ Speed control (0.5x - 2.0x)
- ✅ Progress tracking (resume from where user left off)
- ✅ Background playback (continues when app minimized)
- ✅ System TTS integration (uses device's installed voices)
- ✅ Persistent playback controls (bottom sheet or notification)

#### Out of Scope (Future)
- ❌ Custom AI voices (OpenAI TTS, ElevenLabs, etc.)
- ❌ Downloadable audio files
- ❌ Offline voice caching
- ❌ Text highlighting sync with audio
- ❌ Sleep timer
- ❌ Chapter/section navigation
- ❌ Voice customization beyond system settings

---

## Technical Approach

### Android Text-to-Speech API

Android provides the [`TextToSpeech`](https://developer.android.com/reference/android/speech/tts/TextToSpeech) API, which handles:
- **Speech synthesis**: Converts text to audio
- **Voice engines**: Google TTS, Samsung TTS, device manufacturer voices
- **Language support**: 100+ languages/locales
- **Voice quality**: Varies by engine (standard vs high-quality/neural voices)

**What Android provides**:
- Audio generation and playback
- Voice selection and configuration
- Language detection
- SSML support (Speech Synthesis Markup Language)

**What the app must implement**:
- Text preparation (cleaning HTML, chunking)
- Playback control UI
- State management
- Background service for continuous playback
- Progress tracking

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────┐
│     BookmarkDetailScreen (UI)           │
│  - TTS menu option                      │
│  - Playback controls UI                 │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│   TtsController (ViewModel/Manager)     │
│  - State management                     │
│  - Progress tracking                    │
│  - Control interface                    │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│      TtsService (Foreground Service)    │
│  - Android TTS integration              │
│  - Background playback                  │
│  - Media session                        │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│      Android TextToSpeech API           │
│  - Speech synthesis                     │
│  - Voice engines                        │
└─────────────────────────────────────────┘
```

### Key Components

#### 1. TtsService (Foreground Service)
- Manages Android TextToSpeech instance
- Handles background playback when app minimized
- Shows persistent notification with controls
- Integrates with MediaSession for lock screen controls

#### 2. TtsController (State Manager)
- Manages playback state (idle, playing, paused)
- Tracks current position in article
- Handles speed adjustments
- Coordinates UI updates

#### 3. TtsTextProcessor
- Cleans HTML from article content
- Chunks text into manageable segments
- Handles special cases (code blocks, lists, links)
- Preserves paragraph structure

#### 4. PlaybackControlsUI (Bottom Sheet)
- Play/Pause button
- Stop button
- Speed selector
- Progress indicator
- Current position display

---

## User Experience

### Entry Point

**Location**: Article detail screen (reading view)

**Trigger**: Overflow menu (⋮) → "Listen to article"

**Conditions**:
- Only visible for Article type bookmarks (not photos/videos)
- Only visible if article has content (`hasArticle == true`)
- Disabled if TTS not available on device

**Menu structure**:
```
⋮ More
  ├─ Open in browser
  ├─ Share
  ├─ Listen to article      ← NEW
  ├─ Labels
  └─ Delete
```

---

### Playback Controls UI

#### Initial Design: Bottom Sheet

**Collapsed state** (minimal, persistent):
```
┌────────────────────────────────────────┐
│ Reading Article Title...               │
│ [▶ Play] 2:34 / 8:15        [1.0x] [X]│
└────────────────────────────────────────┘
```

**Expanded state** (more controls):
```
┌────────────────────────────────────────┐
│           Reading Article Title...     │
├────────────────────────────────────────┤
│                                        │
│  ┌──────────────────────────────────┐ │
│  │ ████████░░░░░░░░░░░░░░░░░░░░░░  │ │  ← Progress bar
│  └──────────────────────────────────┘ │
│                                        │
│         2:34              8:15         │
│                                        │
│      [⏮] [⏸] [⏭]                     │  ← Play/Pause/Skip
│                                        │
│  Speed: [0.5x][1.0x][1.5x][2.0x]      │  ← Speed selector
│                                        │
│                          [Stop] [X]    │
└────────────────────────────────────────┘
```

#### Alternative: Notification Controls

When app is minimized:
```
┌────────────────────────────────────────┐
│ 🔊 MyDeck                              │
│ Reading: Article Title                 │
│ 2:34 / 8:15                            │
│ [⏮] [⏸] [⏭]                 1.0x      │
└────────────────────────────────────────┘
```

---

### Interaction Flow

#### Starting Playback

1. User taps "Listen to article" in overflow menu
2. Bottom sheet appears with controls
3. Text processing begins (cleaning HTML, chunking)
4. TTS initializes (may take 1-2 seconds)
5. Playback starts automatically
6. Bottom sheet shows "Playing..." with pause button

#### During Playback

**User can**:
- Pause/resume playback
- Adjust speed (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
- Seek forward/backward (skip 10 seconds)
- Stop playback entirely
- Minimize app (continues in background)

**App tracks**:
- Current paragraph/sentence being read
- Elapsed time and total duration
- Playback speed preference

#### Background Behavior

**When user minimizes app**:
- Playback continues
- Notification appears with controls
- Lock screen shows media controls
- Can control from Bluetooth/headphone buttons

**When user returns to app**:
- Bottom sheet still visible
- Sync state with background service
- Resume scrolling to current position (optional)

#### Ending Playback

**User can stop via**:
- Stop button (clears progress)
- X button (dismisses controls, preserves progress)
- Navigating away from article
- System interruption (phone call, alarm)

**On completion**:
- Show "Article finished" message
- Option to replay or mark as read
- Automatically stop and clean up

---

## Text Processing

### HTML Cleaning

**Remove**:
- HTML tags (`<p>`, `<div>`, `<span>`, etc.)
- Code blocks (wrapped in `<pre>` or `<code>`)
- Image captions (may be redundant)
- Inline styles and scripts

**Preserve**:
- Paragraph breaks (adds natural pauses)
- List structure (reads "bullet" or "number one")
- Emphasis (can use SSML for bold/italic if desired)
- Links (reads link text, not URL)

**Example transformation**:
```html
<!-- Input HTML -->
<p>This is a <strong>great</strong> article.</p>
<p>Here's a list:</p>
<ul>
  <li>First item</li>
  <li>Second item</li>
</ul>

<!-- Processed for TTS -->
This is a great article.

Here's a list:
• First item
• Second item
```

### Text Chunking

**Why chunk?**
- TextToSpeech has max input limits (~4000 characters)
- Better progress tracking
- Allows pause/resume at paragraph boundaries

**Strategy**:
- Split by paragraph (`\n\n`)
- If paragraph > 4000 chars, split by sentence
- Queue chunks for sequential playback
- Track which chunk is currently playing

---

## Implementation Details

### Phase 1: Core TTS Integration

#### 1.1 TtsService (Foreground Service)

**File**: `app/src/main/java/com/mydeck/app/service/TtsService.kt`

**Responsibilities**:
- Initialize Android TextToSpeech
- Manage foreground notification
- Handle playback state
- Expose control interface via binder

**Key methods**:
```kotlin
class TtsService : Service() {
    private lateinit var tts: TextToSpeech
    private var currentUtteranceId: String? = null

    override fun onCreate() {
        super.onCreate()
        initializeTts()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS ready
                _ttsState.value = TtsState.Ready
            } else {
                _ttsState.value = TtsState.Error("TTS initialization failed")
            }
        }
    }

    fun speak(text: String, utteranceId: String) {
        currentUtteranceId = utteranceId
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun pause() {
        tts.stop()
        _playbackState.value = PlaybackState.Paused
    }

    fun resume() {
        // Resume from last position
        _playbackState.value = PlaybackState.Playing
    }

    fun setSpeed(speed: Float) {
        tts.setSpeechRate(speed)
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
```

#### 1.2 TtsController (ViewModel)

**File**: `app/src/main/java/com/mydeck/app/ui/detail/TtsController.kt`

**Responsibilities**:
- Manage TTS playback state
- Process article text
- Track progress
- Coordinate with service

**State management**:
```kotlin
sealed class TtsState {
    data object Idle : TtsState()
    data object Initializing : TtsState()
    data object Ready : TtsState()
    data class Error(val message: String) : TtsState()
}

sealed class PlaybackState {
    data object Stopped : PlaybackState()
    data object Playing : PlaybackState()
    data object Paused : PlaybackState()
    data class Progress(val currentMs: Long, val totalMs: Long) : PlaybackState()
}

data class TtsPlaybackInfo(
    val articleTitle: String,
    val currentPosition: Int,  // Current chunk index
    val totalChunks: Int,
    val playbackSpeed: Float,
    val estimatedDurationMs: Long
)
```

#### 1.3 TtsTextProcessor

**File**: `app/src/main/java/com/mydeck/app/domain/TtsTextProcessor.kt`

**Responsibilities**:
- Clean HTML from article content
- Chunk text appropriately
- Handle special cases

```kotlin
class TtsTextProcessor {
    fun processArticleContent(htmlContent: String): List<String> {
        val cleanText = cleanHtml(htmlContent)
        return chunkText(cleanText)
    }

    private fun cleanHtml(html: String): String {
        // Use Jsoup or regex to strip HTML
        return Jsoup.parse(html).text()
    }

    private fun chunkText(text: String): List<String> {
        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n\n")

        for (paragraph in paragraphs) {
            if (paragraph.length <= MAX_CHUNK_SIZE) {
                chunks.add(paragraph)
            } else {
                // Split long paragraphs by sentence
                val sentences = paragraph.split(". ")
                chunks.addAll(sentences)
            }
        }

        return chunks
    }

    companion object {
        const val MAX_CHUNK_SIZE = 4000
    }
}
```

---

### Phase 2: Playback Controls UI

#### 2.1 Bottom Sheet Controls

**File**: `app/src/main/java/com/mydeck/app/ui/detail/TtsControlsBottomSheet.kt`

**Composable**:
```kotlin
@Composable
fun TtsControlsBottomSheet(
    playbackInfo: TtsPlaybackInfo,
    playbackState: PlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = playbackInfo.articleTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress
            when (playbackState) {
                is PlaybackState.Progress -> {
                    LinearProgressIndicator(
                        progress = playbackState.currentMs.toFloat() / playbackState.totalMs,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(playbackState.currentMs))
                        Text(formatTime(playbackState.totalMs))
                    }
                }
                else -> {
                    // Placeholder
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Skip backward */ }) {
                    Icon(Icons.Default.SkipPrevious, "Skip backward")
                }

                IconButton(
                    onClick = {
                        when (playbackState) {
                            is PlaybackState.Playing -> onPause()
                            else -> onPlay()
                        }
                    }
                ) {
                    Icon(
                        when (playbackState) {
                            is PlaybackState.Playing -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        "Play/Pause"
                    )
                }

                IconButton(onClick = { /* Skip forward */ }) {
                    Icon(Icons.Default.SkipNext, "Skip forward")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speed control
            Text("Speed", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    FilterChip(
                        selected = playbackInfo.playbackSpeed == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed}x") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stop button
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop")
            }
        }
    }
}
```

---

### Phase 3: Background Playback

#### 3.1 Foreground Service

**Why foreground service?**
- Allows playback when app is minimized
- Prevents system from killing the process
- Required for persistent notification

**Manifest**:
```xml
<service
    android:name=".service.TtsService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

#### 3.2 Media Session Integration

**Why MediaSession?**
- Lock screen controls
- Bluetooth/headphone button support
- Android Auto integration
- Consistent with platform expectations

```kotlin
class TtsService : Service() {
    private lateinit var mediaSession: MediaSessionCompat

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "TtsService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumePlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onStop() {
                    stopPlayback()
                }

                override fun onSkipToNext() {
                    skipForward()
                }

                override fun onSkipToPrevious() {
                    skipBackward()
                }
            })

            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
                    )
                    .build()
            )

            isActive = true
        }
    }
}
```

#### 3.3 Persistent Notification

```kotlin
private fun createNotification(
    articleTitle: String,
    isPlaying: Boolean
): Notification {
    val playPauseAction = if (isPlaying) {
        NotificationCompat.Action(
            R.drawable.ic_pause,
            "Pause",
            getPendingIntent(ACTION_PAUSE)
        )
    } else {
        NotificationCompat.Action(
            R.drawable.ic_play,
            "Play",
            getPendingIntent(ACTION_PLAY)
        )
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Reading: $articleTitle")
        .setContentText("MyDeck Text-to-Speech")
        .setSmallIcon(R.drawable.ic_notification_logo)
        .setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1)
        )
        .addAction(
            R.drawable.ic_skip_previous,
            "Previous",
            getPendingIntent(ACTION_SKIP_PREVIOUS)
        )
        .addAction(playPauseAction)
        .addAction(
            R.drawable.ic_skip_next,
            "Next",
            getPendingIntent(ACTION_SKIP_NEXT)
        )
        .setOngoing(true)
        .build()
}
```

---

### Phase 4: Progress Persistence

#### 4.1 Save Playback Position

**When to save**:
- On pause
- On app backgrounded
- On service destroyed
- Periodically during playback (every 10 seconds)

**Storage**: SettingsDataStore or local database

```kotlin
// SettingsDataStore.kt
suspend fun saveTtsProgress(bookmarkId: String, position: TtsPosition)
suspend fun getTtsProgress(bookmarkId: String): TtsPosition?

data class TtsPosition(
    val chunkIndex: Int,
    val characterOffset: Int,
    val timestamp: Long
)
```

#### 4.2 Resume from Saved Position

```kotlin
fun resumeFromSaved(bookmarkId: String, articleContent: String) {
    viewModelScope.launch {
        val savedPosition = settingsDataStore.getTtsProgress(bookmarkId)
        if (savedPosition != null) {
            val chunks = textProcessor.processArticleContent(articleContent)
            startPlaybackFromPosition(chunks, savedPosition.chunkIndex)
        } else {
            startPlaybackFromBeginning(articleContent)
        }
    }
}
```

---

## Edge Cases & Error Handling

### TTS Not Available

**Scenario**: Device doesn't have TTS engine installed

**Handling**:
- Detect on app start: `TextToSpeech.isLanguageAvailable()`
- Show message: "Text-to-speech not available. Install a TTS engine from Play Store."
- Provide deep link to TTS settings or Google TTS download

### Article Has No Content

**Scenario**: Article type but `hasArticle == false`

**Handling**:
- Disable "Listen to article" menu option
- Show tooltip: "Article content not available"

### Language Not Supported

**Scenario**: Article language not supported by installed TTS engines

**Handling**:
- Attempt playback with default language (English)
- Show warning: "TTS may not pronounce correctly for this language"
- Option to download language pack

### Playback Interruptions

**Phone call**:
- Pause playback automatically
- Resume when call ends (optional, user preference)

**Another media app**:
- Pause when other app requests audio focus
- Don't auto-resume (user must manually restart)

**Headphones disconnected**:
- Pause immediately
- Don't auto-resume (privacy/safety)

### Memory Constraints

**Large articles** (>50,000 words):
- Process in smaller batches
- Don't load entire article into memory
- Stream chunks as needed

### Network Dependency

**Scenario**: Some TTS engines require network (Google Cloud TTS)

**Handling**:
- Prefer offline engines when available
- Show warning if network required and unavailable
- Graceful fallback or error message

---

## Settings & Preferences

### TTS Settings Screen

**Location**: Settings → Text-to-Speech (new section)

**Options**:
```
Text-to-Speech
  ├─ Voice
  │   └─ [System default ▼]  → Opens Android TTS settings
  ├─ Default Speed
  │   └─ [1.0x ▼] (0.5x - 2.0x)
  ├─ Resume from last position
  │   └─ [Toggle: ON]
  └─ Auto-pause on interruption
      └─ [Toggle: ON]
```

**Note**: Voice selection is handled by Android system settings. App can't directly control this, but can provide shortcut to system TTS settings.

---

## Accessibility

### Screen Reader Compatibility

**Ensure**:
- Playback controls have proper content descriptions
- Bottom sheet is navigable via TalkBack
- State changes announced ("Playing", "Paused", "Stopped")

**Note**: Be careful with TalkBack + TTS interaction
- When TalkBack active, TTS playback may conflict
- Consider auto-pausing TTS when TalkBack speaks
- Or provide warning: "TalkBack is active. Playback may interfere."

### Keyboard Navigation

**Support**:
- Space: Play/Pause
- Arrow keys: Seek forward/backward
- Number keys: Speed shortcuts (1 = 1.0x, 2 = 2.0x, etc.)

---

## Performance Considerations

### Battery Impact

**TTS is CPU-intensive**:
- Speech synthesis uses CPU for audio generation
- Background playback prevents device sleep
- Notification requires constant wakelock

**Mitigation**:
- Use efficient TTS engines (prefer on-device over cloud)
- Release resources when not in use
- Implement timeout: auto-stop after 2 hours of inactivity

### Memory Usage

**Article content in memory**:
- Large articles (50k+ words) consume significant RAM
- Chunking helps, but all chunks still stored

**Mitigation**:
- Process chunks on-demand rather than preloading all
- Release processed chunks after speaking
- Monitor memory usage, warn if article too large

### Storage

**Progress persistence**:
- Store progress for recently played articles
- Clean up old progress data (older than 30 days)
- Limit to last 50 articles

---

## Analytics & Success Metrics

### Track

**Feature Adoption**:
- % of users who try TTS feature
- % of articles with TTS playback initiated
- Average articles played per user per week

**Engagement**:
- Average listen duration
- Completion rate (% of article listened to)
- Repeat usage rate

**Technical**:
- TTS initialization failures
- Playback errors
- Most common speeds used
- Average playback speed

**User Preferences**:
- Distribution of speed settings
- Resume from last position usage
- Background playback usage

### Success Criteria

**Adoption** (3 months post-launch):
- 15%+ of active users try TTS at least once
- 5%+ of users use TTS regularly (weekly)

**Engagement**:
- 60%+ completion rate for articles under 2000 words
- Average listen duration > 5 minutes

**Quality**:
- TTS error rate < 2%
- Positive feedback in reviews mentioning TTS

---

## Alternatives Considered

### Option 1: Premium AI Voices (ElevenLabs, OpenAI)

**Pros**:
- Much higher quality, natural-sounding voices
- Better pronunciation, emotion, pacing
- Competitive with professional narration

**Cons**:
- Requires API costs ($0.30 per 1000 characters)
- Network dependency (can't work offline)
- Privacy concerns (article content sent to third party)
- Subscription model needed to cover costs

**Decision**: Start with system TTS (free, offline, privacy-friendly). Consider premium voices as future paid feature.

---

### Option 2: Pre-generated Audio Files

**Approach**: Generate MP3 files for articles on server

**Pros**:
- Consistent quality
- Can use best AI voices server-side
- Offload processing from mobile device
- Can download for offline listening

**Cons**:
- Requires server infrastructure and storage
- High latency (user must wait for generation)
- Storage costs scale with articles
- Not practical for personal/private bookmarks

**Decision**: Not feasible for self-hosted Readeck. Better suited for commercial services with curated content.

---

### Option 3: WebView Speech API

**Approach**: Use browser's built-in `speechSynthesis` API

**Pros**:
- No native Android code needed
- Works in WebView showing article
- Simple implementation

**Cons**:
- Limited control over playback
- Can't continue in background
- No progress tracking
- Poor mobile browser support
- Can't show native controls/notification

**Decision**: Not suitable. Native Android TTS provides much better UX.

---

## Competitive Analysis

### Instapaper (Premium)
- ✅ High-quality voices
- ✅ Speed control (0.8x - 2.5x)
- ✅ Background playback
- ✅ Lock screen controls
- ❌ Requires subscription ($3/month)

### Pocket (Free)
- ✅ Built-in to free tier
- ✅ Basic voice selection
- ✅ Speed control
- ✅ Background playback
- ✅ Offline support

### Substack (Free)
- ✅ AI-generated audio (ElevenLabs)
- ✅ Premium voice quality
- ❌ Only for selected posts
- ❌ Pre-generated (not real-time)

### Medium (Free with limits)
- ✅ Google Cloud TTS integration
- ✅ Natural voices
- ❌ Network required
- ❌ Limited daily usage for free users

**MyDeck Positioning**: Free, privacy-respecting, offline-capable TTS using system voices. Quality depends on user's device, but no subscription or network required.

---

## Localization

### String Resources

**Required strings** (add to all 10 language files per CLAUDE.md):

```xml
<!-- TTS Feature -->
<string name="tts_listen_to_article">Listen to article</string>
<string name="tts_playing">Playing</string>
<string name="tts_paused">Paused</string>
<string name="tts_stopped">Stopped</string>
<string name="tts_speed">Speed</string>
<string name="tts_stop">Stop</string>
<string name="tts_skip_forward">Skip forward</string>
<string name="tts_skip_backward">Skip backward</string>
<string name="tts_not_available">Text-to-speech not available</string>
<string name="tts_not_available_description">Install a TTS engine from Play Store to use this feature</string>
<string name="tts_error">Playback error</string>
<string name="tts_no_content">Article content not available for playback</string>

<!-- TTS Settings -->
<string name="settings_tts_title">Text-to-Speech</string>
<string name="settings_tts_voice">Voice</string>
<string name="settings_tts_default_speed">Default speed</string>
<string name="settings_tts_resume_position">Resume from last position</string>
<string name="settings_tts_auto_pause">Auto-pause on interruption</string>

<!-- TTS Notification -->
<string name="tts_notification_channel_name">Article Playback</string>
<string name="tts_notification_channel_description">Controls for text-to-speech playback</string>
<string name="tts_notification_reading">Reading: %s</string>
```

**Total**: ~20 strings × 10 locales = 200 lines

---

## Implementation Phases

### Phase 1: Foundation (MVP)
**Goal**: Basic TTS playback with simple controls

**Deliverables**:
- TtsService (foreground service)
- TtsController (state management)
- TtsTextProcessor (HTML cleaning, chunking)
- Basic UI (play/pause button in bottom sheet)
- Menu option in article overflow menu

**Estimate**: 2-3 weeks (80-120 hours)

---

### Phase 2: Enhanced Controls
**Goal**: Full playback control experience

**Deliverables**:
- Speed control (0.5x - 2.0x)
- Skip forward/backward
- Progress bar with seek
- MediaSession integration
- Lock screen controls
- Notification controls

**Estimate**: 1-2 weeks (40-80 hours)

---

### Phase 3: Persistence & Polish
**Goal**: Production-ready feature

**Deliverables**:
- Progress saving/resuming
- Settings screen
- Error handling
- Edge cases (interruptions, no TTS, etc.)
- Analytics integration
- Comprehensive testing

**Estimate**: 1-2 weeks (40-80 hours)

---

### Total Estimate: 4-7 weeks (160-280 hours)

Breakdown by role:
- **Android Development**: 120-200 hours
- **UI/UX Design**: 20-40 hours
- **QA/Testing**: 20-40 hours

---

## Testing Plan

### Unit Tests
- [ ] TtsTextProcessor correctly cleans HTML
- [ ] TtsTextProcessor chunks text appropriately
- [ ] Speed conversion calculations correct
- [ ] Progress tracking accurate
- [ ] State transitions valid

### Integration Tests
- [ ] Service starts and binds correctly
- [ ] TTS initialization succeeds
- [ ] Playback starts/stops as expected
- [ ] Speed changes applied correctly
- [ ] Progress persists across app restarts

### Manual Testing
- [ ] **Playback**
  - [ ] Plays article from beginning
  - [ ] Pauses and resumes correctly
  - [ ] Speed changes take effect immediately
  - [ ] Skip forward/backward works
  - [ ] Stops playback cleanly
- [ ] **Background behavior**
  - [ ] Continues when app minimized
  - [ ] Notification shows correct state
  - [ ] Lock screen controls work
  - [ ] Bluetooth controls work
- [ ] **Interruptions**
  - [ ] Pauses on phone call
  - [ ] Pauses when headphones disconnected
  - [ ] Yields to other audio apps
- [ ] **Edge cases**
  - [ ] Short articles (< 100 words)
  - [ ] Long articles (> 10,000 words)
  - [ ] Articles with code blocks
  - [ ] Articles in non-English languages
  - [ ] No TTS engine installed
  - [ ] Low battery
  - [ ] Airplane mode

### Accessibility Testing
- [ ] TalkBack compatible
- [ ] All controls have content descriptions
- [ ] State changes announced
- [ ] Keyboard navigation works

### Performance Testing
- [ ] Battery drain acceptable (< 5% per hour)
- [ ] Memory usage reasonable (< 50MB)
- [ ] No ANR or crashes during playback
- [ ] Smooth performance on low-end devices

---

## Risks & Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| TTS quality varies by device | High | High | Set user expectations; suggest Google TTS download |
| Background playback drains battery | Medium | High | Implement timeout; allow user to disable |
| Complex HTML breaks text processing | Medium | Medium | Robust HTML cleaning; fallback to raw text |
| TTS not available on device | Medium | Low | Clear error message; link to Play Store |
| Conflicts with TalkBack | Low | Low | Detect TalkBack; show warning |
| Large articles cause OOM | Low | Low | Chunk processing; memory monitoring |

---

## Future Enhancements

**Phase 4+** (Post-MVP):
- [ ] Sleep timer (stop after X minutes)
- [ ] Chapter/heading navigation
- [ ] Text highlighting sync with audio
- [ ] Downloadable audio files for offline
- [ ] Variable speed by section (slower for code, faster for prose)
- [ ] Smart pauses (longer at headings, shorter at commas)
- [ ] Premium AI voices (OpenAI, ElevenLabs) as paid feature
- [ ] Playlist mode (auto-play next article)
- [ ] CarPlay / Android Auto integration
- [ ] Wear OS controls

---

## Conclusion

Text-to-speech is a high-value feature that addresses multiple user needs: accessibility, convenience, and multitasking. By leveraging Android's built-in TTS capabilities, MyDeck can offer this feature for free, offline, and privacy-respecting.

**MVP focus**: Simple, reliable playback with background support. Get the basics right before adding bells and whistles.

**Success depends on**:
1. Robust text processing (clean HTML extraction)
2. Reliable background playback (service + notification)
3. Intuitive controls (bottom sheet + media session)
4. Good error handling (TTS not available, interruptions)

**Recommended approach**: Implement in phases, ship MVP first, iterate based on user feedback.
