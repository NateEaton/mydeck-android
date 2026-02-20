# MyDeck Bulletproofing Follow-up Changes (Post-Branch Review)

This document summarizes additional improvements recommended after reviewing the `environment/bulletproofing` branch implementation.

---

## 1. ProGuard / R8 Hardening

### File

`app/proguard-rules.pro`

### Add (in addition to existing DTO keep rule)

```pro
# Keep all network DTOs to prevent R8/ProGuard from stripping them
-keep class com.mydeck.app.io.rest.model.** { *; }

# Preserve annotation/signature metadata (helps reflection-based libs)
-keepattributes *Annotation*
-keepattributes Signature
```

---

## 2. Lint Strategy Improvement

### Goal

Avoid permanently disabling many lint rules. Prefer using a baseline file to track existing debt while preventing new lint regressions.

### File

`app/build.gradle.kts`

### Update lint config to use baseline

```kotlin
android {
    lint {
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
}
```

### Command to generate baseline

Run locally:

```bash
./gradlew updateLintBaseline
```

This generates:

`app/lint-baseline.xml`

Commit that file to the repo.

---

## 3. Java Toolchain Consistency

### Goal

Ensure builds are consistent across developer machines and GitHub Actions by allowing Gradle to auto-download the correct JDK.

### File

`gradle.properties`

### Add

```properties
org.gradle.java.installations.auto-download=true
```

---

## 4. GitHub Actions Build Workflow Hardening

### Goal

Ensure CI validates:

* Debug build
* Lint
* Release build (R8/ProGuard validation)

### File

`.github/workflows/build.yml`

### Change Java version to 17

Replace JDK 21 setup with:

```yaml
- name: Setup JDK 17
  uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: 17
```

### Add build steps

```yaml
- name: Clean
  run: ./gradlew clean

- name: Build Debug
  run: ./gradlew assembleDebug

- name: Lint Debug
  run: ./gradlew lintDebug

- name: Build Release (R8/ProGuard validation)
  run: ./gradlew assembleRelease
```

---

## 5. Fix Release Signing So CI Can Build Release

### Problem

Current signing config fails in CI when keystore env vars are missing (because it uses `"none"` as a keystore path).

### Goal

Allow release builds without signing in CI, while still signing properly when env vars exist.

### File

`app/build.gradle.kts`

### Change signing config to conditional env-based signing

```kotlin
signingConfigs {
    create("release") {
        val appKeystoreFile = System.getenv("KEYSTORE")
        val appKeyAlias = System.getenv("KEY_ALIAS")
        val appKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
        val appKeyPassword = System.getenv("KEY_PASSWORD")

        if (appKeystoreFile != null &&
            appKeyAlias != null &&
            appKeystorePassword != null &&
            appKeyPassword != null
        ) {
            keyAlias = appKeyAlias
            storeFile = file(appKeystoreFile)
            storePassword = appKeystorePassword
            keyPassword = appKeyPassword
        }
    }
}
```

This prevents `assembleRelease` from failing in CI.

---

## 6. Apply JDK 17 Standardization Across All Workflows (Recommended)

### Files

`.github/workflows/*.yml`

### Recommended change

Ensure all workflows use:

```yaml
java-version: 17
```

This prevents “build passes in one workflow but fails in another” inconsistencies.

---

# Summary Checklist

✅ Add ProGuard keepattributes
✅ Re-enable lint enforcement using a baseline file
✅ Enable Gradle JDK auto-download
✅ Update GitHub Actions build.yml to run lint + release build
✅ Fix signing config so release builds work without keystore env vars
✅ Standardize all workflows on JDK 17

