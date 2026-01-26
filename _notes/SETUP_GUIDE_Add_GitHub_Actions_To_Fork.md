# Setup Guide: Add GitHub Actions to Upstream Fork

**Document Type:** Setup Guide
**Purpose:** Enable GitHub Actions builds in a fresh fork of jensomato/ReadeckApp
**Date:** 2026-01-25
**Audience:** Setting up MyDeck or any other fork

---

## Overview

This guide explains how to add GitHub Actions build automation to a fresh fork of the upstream `jensomato/ReadeckApp` repository, which doesn't include CI/CD workflows.

**Use Case:** You've forked the upstream app and want to enable automated builds without needing Android Studio.

**Source:** The workflows in `NateEaton/ReadeckApp` serve as the reference implementation.

---

## Prerequisites

- Fresh fork of `jensomato/ReadeckApp` (or similar Android project)
- GitHub account with repository admin access
- macOS, Linux, or WSL (for keystore generation)
- Command line tools: `keytool`, `base64`, `git`

---

## Step 1: Generate Release Keystore

This is a **one-time setup** that creates the signing key for your app.

### 1.1 Create Keystore File

```bash
# Navigate to a secure directory (NOT in the git repo)
cd ~/secure-keys

# Generate keystore
keytool -genkey -v \
  -keystore mydeck-release.keystore \
  -alias mydeck \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

**Prompts you'll see:**
```
Enter keystore password: [create strong password]
Re-enter new password: [confirm password]
What is your first and last name? [Your Name]
What is the name of your organizational unit? [Your Team/Personal]
What is the name of your organization? [Your Org/Personal]
What is the name of your City or Locality? [Your City]
What is the name of your State or Province? [Your State]
What is the two-letter country code for this unit? [US]
Is CN=..., OU=..., O=..., L=..., ST=..., C=... correct? [yes]

Enter key password for <mydeck>: [can be same as keystore password]
Re-enter new password: [confirm]
```

**CRITICAL - Save These:**
- ✅ Keystore file: `mydeck-release.keystore`
- ✅ Keystore password
- ✅ Key alias: `mydeck` (or whatever you chose)
- ✅ Key password

**Backup Strategy:**
1. Store keystore file in secure cloud storage (encrypted)
2. Save passwords in password manager
3. Keep local backup on encrypted drive
4. **NEVER commit keystore to git!**

### 1.2 Convert Keystore to Base64

```bash
# macOS/Linux
base64 -i mydeck-release.keystore -o keystore-base64.txt

# Verify it was created
ls -lh keystore-base64.txt
# Should see a file around 3-4 KB
```

**The `keystore-base64.txt` file contains:**
- Base64-encoded version of your keystore
- This will be uploaded to GitHub secrets
- Safe to copy/paste (it's encrypted data)

---

## Step 2: Configure GitHub Repository Secrets

### 2.1 Navigate to Secrets Settings

1. Go to your forked repository on GitHub
2. Click **Settings** (top bar)
3. In left sidebar: **Secrets and variables** → **Actions**
4. Click **New repository secret** button

### 2.2 Add Four Required Secrets

**Secret 1: SIGNING_KEY**
- Click **New repository secret**
- Name: `SIGNING_KEY`
- Value: Open `keystore-base64.txt` and copy **entire contents**
- Click **Add secret**

**Secret 2: ALIAS**
- Click **New repository secret**
- Name: `ALIAS`
- Value: `mydeck` (or whatever alias you used in keytool)
- Click **Add secret**

**Secret 3: KEY_STORE_PASSWORD**
- Click **New repository secret**
- Name: `KEY_STORE_PASSWORD`
- Value: Your keystore password from Step 1.1
- Click **Add secret**

**Secret 4: KEY_PASSWORD**
- Click **New repository secret**
- Name: `KEY_PASSWORD`
- Value: Your key password from Step 1.1 (often same as keystore password)
- Click **Add secret**

### 2.3 Verify Secrets

You should now see 4 secrets listed:
- `SIGNING_KEY`
- `ALIAS`
- `KEY_STORE_PASSWORD`
- `KEY_PASSWORD`

✅ **Security Note:** Once added, secret values cannot be viewed again (only updated).

---

## Step 3: Copy Workflow Files

### 3.1 Create Workflows Directory

In your forked repository:

```bash
# Clone your fork if you haven't already
git clone https://github.com/YourUsername/YourFork.git
cd YourFork

# Create .github/workflows directory
mkdir -p .github/workflows
```

### 3.2 Download Reference Workflows

You need to copy workflow files from `NateEaton/ReadeckApp`. Here are the essential ones:

**Option A: Download from GitHub (easiest)**

```bash
# Base URL for raw files
BASE_URL="https://raw.githubusercontent.com/NateEaton/ReadeckApp/main/.github/workflows"

# Download essential workflows
curl -o .github/workflows/test-build.yml "$BASE_URL/test-build.yml"
curl -o .github/workflows/dev-release.yml "$BASE_URL/dev-release.yml"
curl -o .github/workflows/release.yml "$BASE_URL/release.yml"

# Optional: CI workflows
curl -o .github/workflows/build.yml "$BASE_URL/build.yml"
curl -o .github/workflows/run-tests.yml "$BASE_URL/run-tests.yml"
```

**Option B: Copy from local clone**

If you have `NateEaton/ReadeckApp` cloned locally:

```bash
# From your fork directory
cp /path/to/NateEaton/ReadeckApp/.github/workflows/*.yml .github/workflows/
```

**Option C: Manual download**

1. Visit: https://github.com/NateEaton/ReadeckApp/tree/main/.github/workflows
2. Click each `.yml` file
3. Click **Raw** button
4. Save file to `.github/workflows/` in your fork

---

## Step 4: Update Product Flavors in build.gradle.kts

The workflows expect specific product flavors. Add them to `app/build.gradle.kts`:

### 4.1 Locate Build Configuration

Open `app/build.gradle.kts` and find the `android {}` block.

### 4.2 Add Signing Configuration

After the `defaultConfig {}` block, add or update `signingConfigs`:

```kotlin
android {
    // ... existing config ...

    signingConfigs {
        getByName("debug") {
            val debugKeystorePath = System.getProperty("user.home") + "/.android/debug.keystore"
            val debugKeystoreFile = file(debugKeystorePath)
            if (debugKeystoreFile.exists()) {
                storeFile = debugKeystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            val appKeystoreFile = System.getenv()["KEYSTORE"] ?: "none"
            val appKeyAlias = System.getenv()["KEY_ALIAS"]
            val appKeystorePassword = System.getenv()["KEYSTORE_PASSWORD"]
            val appKeyPassword = System.getenv()["KEY_PASSWORD"]

            keyAlias = appKeyAlias
            storeFile = file(appKeystoreFile)
            storePassword = appKeystorePassword
            keyPassword = appKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }
}
```

### 4.3 Add Product Flavors

After `signingConfigs`, add:

```kotlin
android {
    // ... existing config ...

    flavorDimensions += "version"

    productFlavors {
        create("githubSnapshot") {
            dimension = "version"
            applicationIdSuffix = ".snapshot"
            versionName = System.getenv()["SNAPSHOT_VERSION_NAME"]
                ?: "${defaultConfig.versionName}-snapshot"
            versionCode = System.getenv()["SNAPSHOT_VERSION_CODE"]?.toInt()
                ?: defaultConfig.versionCode
            signingConfig = signingConfigs.getByName("release")
        }
        create("githubRelease") {
            dimension = "version"
            versionName = System.getenv()["RELEASE_VERSION_NAME"]
                ?: defaultConfig.versionName
            versionCode = System.getenv()["RELEASE_VERSION_CODE"]?.toInt()
                ?: defaultConfig.versionCode
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**What this does:**
- Creates two build variants: `githubSnapshot` and `githubRelease`
- Snapshot builds get `.snapshot` suffix (can install alongside release)
- Both read version info from environment variables (set by GitHub Actions)
- Both use release signing configuration

### 4.4 Customize Application IDs (Optional)

If you're creating MyDeck or another fork:

```kotlin
defaultConfig {
    applicationId = "com.mydeck.app"  // ← Change this
    // ... rest of config
}

productFlavors {
    create("githubSnapshot") {
        dimension = "version"
        applicationIdSuffix = ".snapshot"
        // Result: com.mydeck.app.snapshot
    }
    create("githubRelease") {
        dimension = "version"
        // Uses: com.mydeck.app
    }
}
```

---

## Step 5: Customize Workflow Files

### 5.1 Update Release Names (Optional)

Edit `.github/workflows/dev-release.yml`:

```yaml
- name: Create release
  uses: ncipollo/release-action@v1.16.0
  with:
    name: MyDeck Snapshot Build  # ← Change from "ReadeckApp"
    body: |
      This release represents a snapshot build...
```

Edit `.github/workflows/release.yml`:

```yaml
- name: Create Release
  uses: ncipollo/release-action@v1.16.0
  with:
    name: MyDeck Release v${{ steps.get_version.outputs.version }}  # ← Change
```

### 5.2 Update APK File Naming (Optional)

If you changed the app name, update `app/build.gradle.kts`:

```kotlin
applicationVariants.all {
    outputs.all {
        if (outputFile != null && (outputFile.name.endsWith(".apk") || outputFile.name.endsWith(".aab"))) {
            val extension = if (outputFile.name.endsWith(".apk")) "apk" else "aab"
            val newName = "MyDeck-${versionName}.${extension}"  // ← Change from "ReadeckApp"
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName = newName
        }
    }
}
```

---

## Step 6: Commit and Push

### 6.1 Add Workflow Files

```bash
# Stage workflow files
git add .github/workflows/*.yml

# Stage build.gradle.kts changes
git add app/build.gradle.kts

# Commit
git commit -m "Add GitHub Actions workflows for automated builds

- Add test-build, dev-release, and release workflows
- Configure product flavors for GitHub builds
- Add signing configuration from environment variables"

# Push to your fork
git push origin main
# Or push to develop branch if that's your workflow
```

### 6.2 Verify Workflows Appear

1. Go to your repository on GitHub
2. Click **Actions** tab
3. You should see workflow names in left sidebar:
   - Build Test Release
   - Build Snapshot Release
   - Release Build and Github Release
   - (Plus any optional CI workflows)

---

## Step 7: Test the Setup

### 7.1 Trigger Manual Test Build

1. Go to **Actions** tab
2. Click **Build Test Release** in left sidebar
3. Click **Run workflow** (top right)
4. Select branch: `main` (or your working branch)
5. Click green **Run workflow** button

### 7.2 Monitor Build Progress

1. Click on the running workflow
2. Watch build steps execute:
   - ✅ Setup JDK 21
   - ✅ Setup Gradle
   - ✅ Setup Android SDK
   - ✅ Decode Keystore
   - ✅ Build APK
   - ✅ Upload Artifact

**Build time:** ~5-10 minutes

### 7.3 Download and Test APK

**If build succeeds:**
1. Scroll to **Artifacts** section at bottom
2. Click artifact name to download ZIP
3. Extract ZIP to get APK
4. Install on Android device

**If build fails:**
1. Click on failed step to see error logs
2. Common issues:
   - Missing/incorrect secrets → Verify Step 2
   - Build configuration errors → Check Step 4
   - Gradle sync issues → Verify build.gradle.kts syntax

---

## Step 8: Set Up Automatic Snapshot Builds (Optional)

### 8.1 Create Develop Branch

If you don't have a `develop` branch:

```bash
# Create develop branch from main
git checkout -b develop
git push -u origin develop
```

### 8.2 Enable Snapshot Workflow

The `dev-release.yml` workflow triggers automatically on pushes to `develop`:

```yaml
on:
  push:
    branches: [develop]
  workflow_dispatch:
```

**How it works:**
1. Push code to `develop` branch
2. Workflow automatically runs
3. Creates/updates `develop-snapshot` GitHub Release
4. Attaches signed APK to release

### 8.3 Test Automatic Build

```bash
# Make a change on develop
echo "# Test" >> README.md
git add README.md
git commit -m "Test automatic snapshot build"
git push origin develop

# Check Actions tab - build should start automatically
```

---

## Step 9: Using the Builds

### Manual Test Builds

**When to use:** Quick test from any branch

**How:**
1. Actions → Build Test Release → Run workflow
2. Select branch → Run
3. Download from Artifacts (expires in 5 days)

### Snapshot Builds

**When to use:** Regular testing from develop branch

**How (automatic):**
- Push to `develop` branch
- Wait for build to complete
- Download from Releases → `develop-snapshot`

**How (manual):**
1. Actions → Build Snapshot Release → Run workflow
2. Download from Releases → `develop-snapshot`

### Release Builds

**When to use:** Official versioned releases

**How:**
1. Update version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 100
   versionName = "1.0.0"
   ```
2. Commit and push
3. Create and push tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
4. Build triggers automatically
5. Edit draft release on GitHub
6. Publish when ready

---

## Troubleshooting

### Build Fails: "No value has been specified for property 'signingConfig.storeFile'"

**Cause:** GitHub secrets not configured

**Fix:**
1. Go to Settings → Secrets and variables → Actions
2. Verify all 4 secrets exist: `SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`
3. Re-add `SIGNING_KEY` if needed (copy from `keystore-base64.txt`)

### Build Fails: "Task 'assembleGithubSnapshotRelease' not found"

**Cause:** Product flavors not configured

**Fix:**
1. Verify `flavorDimensions` and `productFlavors` added to `app/build.gradle.kts`
2. Run locally to test: `./gradlew assembleGithubSnapshotRelease`
3. Push changes and retry workflow

### Build Succeeds but APK Won't Install

**Cause:** Signature conflict with existing app

**Fix:**
- Uninstall original app first, OR
- Use snapshot flavor (different app ID), OR
- Change `applicationId` in build.gradle.kts

### Workflow Not Appearing in Actions Tab

**Cause:** Workflow file syntax error

**Fix:**
1. Check YAML syntax (indentation must be exact)
2. Validate with: https://www.yamllint.com/
3. Compare with reference files from NateEaton/ReadeckApp

### "Invalid Base64" Error During Keystore Decode

**Cause:** Corrupted base64 encoding

**Fix:**
1. Re-generate base64:
   ```bash
   base64 -i mydeck-release.keystore -o keystore-base64.txt
   ```
2. Copy **entire** contents (no truncation)
3. Update `SIGNING_KEY` secret on GitHub
4. Ensure no extra whitespace/newlines

---

## Advanced Configuration

### Build Multiple Branches Automatically

Edit `dev-release.yml` to trigger on multiple branches:

```yaml
on:
  push:
    branches:
      - develop
      - staging
      - feature/*
  workflow_dispatch:
```

### Custom Version Naming

For snapshot builds with custom naming:

```yaml
- name: Build apk with Gradle
  run: ./gradlew assembleGithubSnapshotRelease
  env:
    SNAPSHOT_VERSION_NAME: "alpha-${{ github.run_number }}"  # ← Custom
```

### Build Notifications

Add email/Slack notifications:

```yaml
- name: Notify on failure
  if: failure()
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

---

## Checklist

Use this checklist when setting up a new fork:

- [ ] Fork upstream repository
- [ ] Clone fork locally
- [ ] Generate release keystore
- [ ] Convert keystore to base64
- [ ] Add 4 secrets to GitHub repository
- [ ] Create `.github/workflows/` directory
- [ ] Copy workflow files from reference
- [ ] Update `app/build.gradle.kts` with flavors
- [ ] Customize app ID and names (if fork)
- [ ] Commit and push workflow files
- [ ] Trigger test build manually
- [ ] Verify build succeeds
- [ ] Download and test APK
- [ ] Create `develop` branch (optional)
- [ ] Test automatic snapshot build (optional)
- [ ] Document keystore backup location
- [ ] Save passwords in password manager

---

## Summary

**What You've Set Up:**
- ✅ Automated builds on GitHub servers
- ✅ Three build workflows (test, snapshot, release)
- ✅ Code signing with your keystore
- ✅ APK downloads from GitHub
- ✅ No Android Studio required for builds

**Daily Workflow:**
1. Write code (any computer)
2. Push to GitHub
3. Trigger/wait for build
4. Download APK
5. Install on phone
6. Test and iterate

**For MyDeck Fork:**
- Follow this guide exactly
- Customize app ID, names, and branding
- Keep workflows the same (they're generic)
- Test thoroughly before first release

---

## Files Reference

**Required Files to Add:**

```
.github/
  workflows/
    test-build.yml         # Manual test builds
    dev-release.yml        # Automatic snapshot builds
    release.yml            # Tag-triggered releases
    build.yml              # CI checks (optional)
    run-tests.yml          # Unit tests (optional)
```

**Files to Modify:**

```
app/build.gradle.kts       # Add flavors, signing config
```

**Files to Create Locally (NOT committed):**

```
~/secure-keys/
  mydeck-release.keystore  # Your signing key
  keystore-base64.txt      # For GitHub upload
```

---

## Next Steps

1. **Complete this setup** in your fork
2. **Test all three workflows** (test, snapshot, release)
3. **Document your process** for team members
4. **Set up branch protection** rules (optional)
5. **Configure automatic builds** on develop branch
6. **Start building!** 🚀
