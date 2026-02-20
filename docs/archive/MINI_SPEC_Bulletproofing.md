This "Bulletproofing" Mini-Spec is designed to move the app from a "fragile/inherited" build state to a "stable/modern" state. Completing this will stop the "cascade of build failures" and provide a clean foundation for the **Revised Sync Model** and **Filtered List** features.

---

# Mini-Spec: Build Environment & Tooling Modernization

## 1. Goal
*   **Remove KAPT:** Eliminate the slow/flaky legacy Java annotation processor.
*   **Modernize JVM:** Switch from Java 8 to Java 17 (standard for modern Android).
*   **Consolidate Logging:** Ensure all system logs are captured by the app's diagnostic "Send Logs" feature.
*   **Fix Dependency Drift:** Align library versions to prevent hidden runtime conflicts.

---

## 2. Phase 1: Eliminate KAPT & KSP Coexistence
The app currently runs two different compilers (KAPT and KSP) to process annotations. This is the #1 cause of build instability.

### 2.1 Remove Retrofit Keeper
The `retrofit-response-type-keeper` is the only library requiring KAPT. We will replace it with a manual ProGuard rule.

1.  **Modify `proguard-rules.pro`:** Add the following line to ensure your network data models aren't deleted during release builds:
    ```pro
    # Keep all network DTOs to prevent R8/ProGuard from stripping them
    -keep class com.mydeck.app.io.rest.model.** { *; }
    ```
2.  **Modify `build.gradle.kts` (Module:app):**
    *   Delete: `alias(libs.plugins.kotlin.kapt)` from the `plugins` block.
    *   Delete: `implementation(libs.retrofit.response.type.keeper)` from `dependencies`.
    *   Delete: `kapt(libs.retrofit.response.type.keeper)` from `dependencies`.

### 2.2 Remove KAPT Plugin
*   In the root `build.gradle.kts` or `libs.versions.toml`, remove any references to `kotlin-kapt`.

---

## 3. Phase 2: Modernize Java Toolchain
We will move to Java 17 to align with `compileSdk 35` requirements.

1.  **Modify `build.gradle.kts` (Module:app):**
    Update the `android` block:
    ```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // Keep this for Android 7.0 support
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    ```

---

## 4. Phase 3: Dependency Hygiene & Lint Reconciliation
We need to stop "ignoring" version conflicts and start fixing them.

1.  **Update `gradle/libs.versions.toml`:**
    *   Bump **Android Gradle Plugin (AGP)** to `8.9.2` (or current stable).
    *   Bump **Room** to `2.7.1` (which has better KSP support).
    *   Bump **Compose BOM** to `2025.04.01`.
2.  **Clean Lint Baseline:**
    *   **Delete `lint-baseline.xml`** entirely.
    *   Run `./gradlew lint`.
    *   If any "Newer Version Available" errors appear, update them in `libs.versions.toml` until the build passes without a baseline.

---

## 5. Phase 4: Logging Standardization
Currently, `Log.d` is used in `AppModule` and `LoggerUtil`. These logs are **invisible** to your "Send Logs" diagnostic feature because they bypass the `Timber` file tree.

1.  **Code Audit:** Search for `import android.util.Log` in the project.
2.  **Refactor:** Replace all `Log.x(...)` calls with `Timber.x(...)`.
3.  **Removal:** Remove `import android.util.Log` from those files.
4.  **Enforcement:** In `build.gradle.kts`, configure Lint to treat `LogNotTimber` as an **Error**, preventing future "silent" logs from being introduced.

---

## 6. Verification Plan

### 6.1 Build Performance
*   Run `./gradlew clean assembleDebug`.
*   **Success Metric:** Build completes without "KAPT" tasks appearing in the execution log.

### 6.2 Regression Check (Release Mode)
*   Build the Release APK: `./gradlew assembleRelease`.
*   Install it on a device.
*   **Success Metric:** The app launches and fetches bookmarks without crashing. (This confirms the ProGuard rule we added in Phase 1 is working).

### 6.3 Diagnostic Check
*   Open the app -> Settings -> Logs.
*   **Success Metric:** You should now see entries related to "Hilt Initialization" and "Database Creation" that were previously missing because they used standard `Log.d`.

---

## 7. Developer Rationale (For your Records)
*   **Why Java 17?** Modern Android tools (R8, D8) and Google libraries now require JDK 17 to run. Using 1.8 for the target bytecode adds a translation layer that slows down every build.
*   **Why kill KAPT?** KAPT generates Java stubs for every Kotlin class, doubling the work of the compiler. KSP (which we are keeping) works directly on Kotlin metadata and is ~2x faster and significantly more stable.
*   **Why fix Lint?** A `lint-baseline.xml` is essentially a "to-do list of technical debt." By clearing it, we ensure that when you add the **Sync Model** later, any *new* errors are surfaced immediately rather than buried in the noise.