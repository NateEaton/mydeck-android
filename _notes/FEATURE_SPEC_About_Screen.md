# Feature Spec: About Screen

**Document Type:** Technical Implementation Spec
**Purpose:** Refactoring reference for MyDeck
**Date:** 2026-01-25
**Status:** Current implementation in ReadeckApp

---

## Overview

A full-screen informational page displaying app details, credits, system information, project links, license information, and a link to view open-source libraries. Accessed from the navigation drawer.

**Note:** While referred to as "About Dialog" in the request, this is implemented as a full navigation screen, not a dialog.

---

## User-Facing Behavior

### Access Point
- **Navigation Drawer Item:** "About" with info icon (`Icons.Outlined.Info`)
- **Navigation:** Opens AboutRoute as a full screen in navigation stack

### Screen Layout

**TopAppBar:**
- **Title:** "About" (string resource: `R.string.about_title`)
- **Navigation Icon:** Back arrow (`Icons.AutoMirrored.Filled.ArrowBack`)
- **Content Description:** "Back" (string resource: `R.string.back`)
- **Action:** Navigates back to previous screen

**Content (Scrollable Column):**

Layout sections from top to bottom:

1. **App Header**
   - App name (centered, headline style)
   - Version (centered, large body style)

2. **Description**
   - App description text (centered, medium body style)

3. **Credits Section**
   - Section title: "Credits"
   - App author credit
   - Readeck project credit
   - Fork information (if applicable)

4. **System Info Section**
   - Section title: "System Info"
   - App version (name + code)
   - Build timestamp
   - Android version (release + SDK)
   - Device info (manufacturer + model)

5. **Project Section**
   - Section title: "Project"
   - Fork repository link (clickable, with link icon)
   - Original repository link (clickable, with link icon)
   - Readeck server link (clickable, with link icon)

6. **License Section**
   - Section title: "License"
   - App license text
   - Readeck license note

7. **Open Source Libraries**
   - Clickable row with list icon
   - Title + subtitle
   - Navigates to OpenSourceLibrariesRoute

**Visual Styling:**
- **Padding:** 16dp content padding
- **Section Spacing:** 24dp between major sections
- **Item Spacing:** 8dp between items within section, 4dp for dense info
- **Dividers:** HorizontalDivider between sections
- **Center Alignment:** Header and description centered
- **Left Alignment:** All section content left-aligned

---

## Implementation Details

### File Locations

**Screen Component:**
- `/app/src/main/java/de/readeckapp/ui/about/AboutScreen.kt`

**ViewModel:**
- `/app/src/main/java/de/readeckapp/ui/about/AboutViewModel.kt`

### Screen Structure

**AboutScreen Composable (with ViewModel):**
```kotlin
@Composable
fun AboutScreen(navHostController: NavHostController) {
    val viewModel: AboutViewModel = hiltViewModel()
    val navigationEvent = viewModel.navigationEvent.collectAsState()

    LaunchedEffect(key1 = navigationEvent.value) {
        navigationEvent.value?.let { event ->
            when (event) {
                AboutViewModel.NavigationEvent.NavigateBack -> {
                    navHostController.popBackStack()
                }
                AboutViewModel.NavigationEvent.NavigateToOpenSourceLibraries -> {
                    navHostController.navigate(OpenSourceLibrariesRoute)
                }
            }
            viewModel.onNavigationEventConsumed()
        }
    }

    val context = LocalContext.current
    AboutScreenContent(
        onBackClick = { viewModel.onClickBack() },
        onOpenSourceLibrariesClick = { viewModel.onClickOpenSourceLibraries() },
        onUrlClick = { url -> openUrlInCustomTab(context, url) }
    )
}
```

**AboutScreenContent Composable (Stateless UI):**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(
    onBackClick: () -> Unit,
    onOpenSourceLibrariesClick: () -> Unit,
    onUrlClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Content sections...
        }
    }
}
```

### Content Sections Implementation

**App Header:**
```kotlin
// App Name
Text(
    text = stringResource(R.string.app_name),
    style = MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center
)

Spacer(modifier = Modifier.height(8.dp))

// Version
Text(
    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
    style = MaterialTheme.typography.bodyLarge,
    textAlign = TextAlign.Center
)

Spacer(modifier = Modifier.height(24.dp))

// Description
Text(
    text = stringResource(R.string.about_description),
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center
)
```

**Credits Section:**
```kotlin
HorizontalDivider()
Spacer(modifier = Modifier.height(16.dp))

Text(
    text = stringResource(R.string.about_credits_title),
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(8.dp))

Text(
    text = stringResource(R.string.about_credits_app_author),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(8.dp))

Text(
    text = stringResource(R.string.about_credits_readeck),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth()
)

// Fork info (optional)
Spacer(modifier = Modifier.height(8.dp))

Text(
    text = stringResource(R.string.about_credits_fork),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth()
)
```

**System Info Section:**
```kotlin
HorizontalDivider()
Spacer(modifier = Modifier.height(16.dp))

Text(
    text = stringResource(R.string.about_system_info_title),
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(8.dp))

Text(
    text = stringResource(
        R.string.about_system_info_version,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE
    ),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(4.dp))

Text(
    text = stringResource(
        R.string.about_system_info_build_time,
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIME.toLong()))
    ),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(4.dp))

Text(
    text = stringResource(
        R.string.about_system_info_android,
        Build.VERSION.RELEASE,
        Build.VERSION.SDK_INT
    ),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(4.dp))

Text(
    text = stringResource(
        R.string.about_system_info_device,
        Build.MANUFACTURER,
        Build.MODEL
    ),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth()
)
```

**Project Links Section:**
```kotlin
HorizontalDivider()
Spacer(modifier = Modifier.height(16.dp))

Text(
    text = stringResource(R.string.about_project_title),
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(8.dp))

// Fork Repository (optional)
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onUrlClick("https://github.com/NateEaton/ReadeckApp") }
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        Icons.Filled.Link,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp)
    )
    Text(
        text = stringResource(R.string.about_project_this_repo),
        style = MaterialTheme.typography.bodyMedium
    )
}

// Original Repository
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onUrlClick("https://github.com/jensomato/ReadeckApp") }
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        Icons.Filled.Link,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp)
    )
    Text(
        text = stringResource(R.string.about_project_original_repo),
        style = MaterialTheme.typography.bodyMedium
    )
}

// Readeck Server
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onUrlClick("https://codeberg.org/readeck/readeck") }
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        Icons.Filled.Link,
        contentDescription = null,
        modifier = Modifier.padding(end = 16.dp)
    )
    Text(
        text = stringResource(R.string.about_project_readeck_repo),
        style = MaterialTheme.typography.bodyMedium
    )
}
```

**License Section:**
```kotlin
HorizontalDivider()
Spacer(modifier = Modifier.height(16.dp))

Text(
    text = stringResource(R.string.about_license_title),
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(8.dp))

Text(
    text = stringResource(R.string.about_license_text),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth()
)

Spacer(modifier = Modifier.height(8.dp))

Text(
    text = stringResource(R.string.about_license_readeck),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth()
)
```

**Open Source Libraries Link:**
```kotlin
HorizontalDivider()
Spacer(modifier = Modifier.height(16.dp))

Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onOpenSourceLibrariesClick() }
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        Icons.Filled.List,
        contentDescription = stringResource(R.string.settings_open_source_libraries),
        modifier = Modifier.padding(end = 16.dp)
    )
    Column {
        Text(
            text = stringResource(R.string.settings_open_source_libraries),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.settings_open_source_libraries_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

---

## ViewModel Implementation

**AboutViewModel.kt:**
```kotlin
@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    fun onClickBack() {
        _navigationEvent.value = NavigationEvent.NavigateBack
    }

    fun onClickOpenSourceLibraries() {
        _navigationEvent.value = NavigationEvent.NavigateToOpenSourceLibraries
    }

    fun onNavigationEventConsumed() {
        _navigationEvent.value = null
    }

    sealed class NavigationEvent {
        data object NavigateBack : NavigationEvent()
        data object NavigateToOpenSourceLibraries : NavigationEvent()
    }
}
```

---

## Build Configuration

**BuildConfig Fields Used:**
```kotlin
BuildConfig.VERSION_NAME      // e.g., "1.2.3"
BuildConfig.VERSION_CODE       // e.g., 10203
BuildConfig.BUILD_TIME         // Unix timestamp as Long
```

**Required in build.gradle.kts:**
```kotlin
android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }
}
```

---

## String Resources Required

| String ID | Type | English Example | Notes |
|-----------|------|-----------------|-------|
| `about_title` | Simple | "About" | TopAppBar title |
| `about_version` | Format (1 arg) | "Version %s" | Version display |
| `about_description` | Simple | "A read-later app for Readeck" | App description |
| `about_credits_title` | Simple | "Credits" | Section title |
| `about_credits_app_author` | Simple | "App by [Author]" | Author credit |
| `about_credits_readeck` | Simple | "Based on Readeck by..." | Readeck credit |
| `about_credits_fork` | Simple | "This is a fork of..." | Fork info (optional) |
| `about_system_info_title` | Simple | "System Info" | Section title |
| `about_system_info_version` | Format (2 args) | "Version %s (%d)" | Version + code |
| `about_system_info_build_time` | Format (1 arg) | "Built: %s" | Build timestamp |
| `about_system_info_android` | Format (2 args) | "Android %s (API %d)" | Android version |
| `about_system_info_device` | Format (2 args) | "Device: %s %s" | Manufacturer + model |
| `about_project_title` | Simple | "Project" | Section title |
| `about_project_this_repo` | Simple | "This Repository (Fork)" | Fork link |
| `about_project_original_repo` | Simple | "Original Repository" | Original app link |
| `about_project_readeck_repo` | Simple | "Readeck Server" | Server link |
| `about_license_title` | Simple | "License" | Section title |
| `about_license_text` | Simple | "MIT License..." | License text |
| `about_license_readeck` | Simple | "Readeck is licensed..." | Server license note |
| `settings_open_source_libraries` | Simple | "Open Source Libraries" | Link title |
| `settings_open_source_libraries_subtitle` | Simple | "View third-party licenses" | Link subtitle |
| `back` | Simple | "Back" | Back button description |

---

## URL Handling

**External URLs Opened in Custom Tab:**
- Fork repository (if applicable)
- Original repository
- Readeck server repository

**Implementation:**
```kotlin
val context = LocalContext.current
onUrlClick = { url -> openUrlInCustomTab(context, url) }
```

**Utility Function:**
```kotlin
// From de.readeckapp.util package
fun openUrlInCustomTab(context: Context, url: String)
```

---

## Navigation Integration

**Route Definition:**
```kotlin
data object AboutRoute : Route
```

**Navigation from Drawer:**
```kotlin
NavigationDrawerItem(
    label = { Text(stringResource(id = R.string.about)) },
    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
    selected = false,
    onClick = {
        viewModel.onClickAbout()
        scope.launch { drawerState.close() }
    }
)
```

**ViewModel Handler (BookmarkListViewModel):**
```kotlin
fun onClickAbout() {
    _navigationEvent.value = NavigationEvent.NavigateToAbout
}
```

---

## Fork-Specific Sections

**Markers in Code:**
```kotlin
// FORK_INFO_START - Remove this section if merging back to original repo
...
// FORK_INFO_END
```

**Fork-Specific Elements:**
1. Fork credits text
2. Fork repository link

**Purpose:** Easy identification and removal if merging back to upstream

---

## Key Behaviors

1. **Scrollable Content:** Entire content area scrollable for long content/small screens
2. **Center Alignment:** Header and description centered for visual hierarchy
3. **External Links:** All repository URLs open in Chrome Custom Tab
4. **Internal Navigation:** Open Source Libraries navigates within app
5. **Build Info:** Version/build info pulled from BuildConfig at compile time
6. **Device Info:** System info pulled from Build class at runtime
7. **Back Navigation:** Standard back button uses navigation stack
8. **Section Dividers:** HorizontalDivider between all major sections for visual separation

---

## Testing Considerations

**Test Cases:**
1. Navigate to About screen from drawer
2. Verify all text displays correctly
3. Verify version info matches BuildConfig
4. Verify build timestamp formats correctly
5. Verify Android version and device info displays
6. Click fork repository link (opens in Custom Tab)
7. Click original repository link (opens in Custom Tab)
8. Click Readeck link (opens in Custom Tab)
9. Click Open Source Libraries (navigates in app)
10. Click back button (returns to previous screen)
11. Test scrolling on small screen device
12. Verify all sections separated by dividers

---

## Implementation Notes for Refactor

1. **Full Screen, Not Dialog:** Despite being called "About Dialog," this is a navigation destination
2. **BuildConfig Fields:** Requires build.gradle.kts configuration for BUILD_TIME field
3. **Date Formatting:** Uses SimpleDateFormat with "yyyy-MM-dd HH:mm:ss" format
4. **Fork Markers:** FORK_INFO_START/END comments mark fork-specific code for easy removal
5. **ViewModel Pattern:** Separates stateful (AboutScreen) from stateless (AboutScreenContent) composables
6. **Navigation Events:** Uses sealed class for type-safe navigation events
7. **Custom Tab:** External links use Chrome Custom Tab instead of external browser
8. **Icon Consistency:** Link icon for external URLs, List icon for internal navigation
9. **Spacing Pattern:** 24dp between sections, 16dp after dividers, 8dp between items, 4dp for dense items
10. **Optional Sections:** Fork-specific sections can be conditionally included/removed
