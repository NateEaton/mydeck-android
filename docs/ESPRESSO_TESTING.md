# Espresso UI Testing in MyDeck (Setup + AI Prompts)

## Feasibility in This Environment
- **Espresso tests are instrumented UI tests** and **require a connected Android device or emulator**.
- In this cloud/AI execution environment, I **can author tests and provide the exact Gradle/Android Studio steps**, but **I should not run `connectedAndroidTest` tasks here** (device/emulator required).
- For **deterministic reproduction**, instrumented tests should use **local fixtures** (sample HTML/article data) instead of relying on live websites that can change.

## What’s Already in the Project
- Instrumented tests live under `app/src/androidTest/`.
- Espresso and AndroidX test libraries are already declared in `app/build.gradle.kts`.
- The default test runner is `androidx.test.runner.AndroidJUnitRunner` (see `defaultConfig`).
- No separate Espresso extension is required; the dependencies and runner already enable CLI execution.

## Local Setup Checklist
1. **Android Studio** installed with Android SDK + platform tools.
2. **Device or emulator** available:
   - Emulator: install a system image (Pixel 6/7, API 34+ recommended).
   - Device: enable **Developer Options** + **USB debugging**.
3. Verify a device is connected:
   - In Android Studio: **Device Manager** shows it running.
   - Or `adb devices` shows the device/emulator (if using terminal).

## Running Espresso Tests Locally
### Android Studio
- Right-click a test under `app/src/androidTest/` → **Run**.
- Or use **Run > Edit Configurations** and choose **Android Instrumented Tests**.

### Gradle (local terminal)
> The project uses product flavors (`githubSnapshot`, `githubRelease`), so the connected test task includes the flavor name.

Common options:
- `./gradlew :app:connectedGithubSnapshotDebugAndroidTest`
- `./gradlew :app:connectedGithubReleaseDebugAndroidTest`

Run a single test class:
- `./gradlew :app:connectedGithubSnapshotDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mydeck.app.YourTestClass`

Run a single test method:
- `./gradlew :app:connectedGithubSnapshotDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mydeck.app.YourTestClass#yourTestMethod`

If multiple devices/emulators are running, either stop the extra devices or target one explicitly:
- `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedGithubSnapshotDebugAndroidTest`

If you’re unsure of the exact task name, list verification tasks:
- `./gradlew :app:tasks --group verification`

> **Note:** These are **device/emulator-dependent** tasks and should be run locally.

## Writing Tests for UI Reproduction
### Recommended Structure
1. **Navigate** to the relevant screen (e.g., open a bookmark in reading view).
2. **Set reading preferences** (font, size, spacing, hyphenation, layout).
3. **Assert visibility** of text content.
4. **Repeat toggles or rapid changes** to reproduce the issue.

### Deterministic Content
For issues tied to a specific article:
- Prefer **fixture HTML** or a **local test bookmark** to avoid site changes.
- If the bug only reproduces on remote content, record a stable HTML snapshot (or sync the bookmark and store the article locally) so tests remain reliable.

## Example AI Prompt Template
Use this when asking an AI model to create or update tests:

```
Goal: Add an Espresso UI test that reproduces a reading-view text disappearance bug.

Context:
- App: MyDeck Android
- Tests: app/src/androidTest/
- Existing test runner: AndroidJUnitRunner
- Reading settings screen: (provide route or composable name if known)

Repro steps:
1) Open the bookmark for: https://newlinesmag.com/essays/the-russian-complex-why-chinas-ties-to-moscow-run-deeper-than-politics/
2) Set reading layout to Wide body.
3) Set font = Literata, size = 105%, spacing = 90%.
4) Toggle hyphenation ON (text disappears). Toggle OFF (text still disappears).
5) Rapidly switch font sizes; text reappears only after scrolling.

Expectations:
- Text content remains visible after toggling hyphenation and changing font size.
- If text becomes invisible, test should fail with a clear assertion.

Constraints:
- Avoid relying on live network content if possible.
- If a fixture is needed, add a minimal HTML asset and load it into the reader.

Please provide:
- The new test file + class name.
- Any helper utilities or test fixtures you add.
- The Gradle task to run the test locally.
```

## Example AI Prompt (Running Tests)
```
Please run the new instrumented test locally.
- Task: ./gradlew :app:connectedGithubSnapshotDebugAndroidTest
- Device: Pixel 7, API 34 emulator
- Output: Provide the test results and any failures.
```

> In this environment I can **draft the commands and tests**, but the **actual run must happen locally** on a device/emulator.

## Notes for This Specific Bug Report
- The bug likely relates to **text reflow** and **hyphenation/layout recalculation**.
- Espresso can reproduce by **rapidly toggling font size** and **hyphenation** while verifying that the article content remains visible.
- If the reader uses a WebView, we may need to add **WebView/Compose test hooks** or a **test-only idling resource** to ensure layout completes before assertions.
