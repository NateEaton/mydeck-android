# Cloud AI Environment Setup for Android Builds

This guide explains how to set up Android SDK builds in cloud AI coding environments so that the AI agent can compile, lint, and test your code changes.

## The Problem

Cloud coding environments (Claude Code for Web, ChatGPT Codex) don't come with the Android SDK pre-installed. Additionally, their default network restrictions block `dl.google.com`, which hosts both the Android SDK downloads and the Google Maven repository (required for ~80% of Android dependencies like AndroidX, Compose, Hilt, etc.).

## Claude Code for Web

### Setup (one-time)

1. **Set Network Access to "Full"** in your project settings:
   - Open your project on claude.ai
   - Go to Project Settings
   - Under Network Access, select **"Full"**
   - This unblocks `dl.google.com` (Google Maven) and `jitpack.io`

2. **The rest is automatic.** The `.claude/settings.json` file in this repo configures a `SessionStart` hook that runs `scripts/setup-android-sdk.sh`. When you start a new web session, it will:
   - Download and install Android command-line tools
   - Install the required SDK platform (android-35) and build tools
   - Accept SDK licenses
   - Fix JVM proxy settings so Gradle can reach Google Maven
   - Export `ANDROID_HOME` and `ANDROID_SDK_ROOT`

### Validating Code Changes

Once the SDK is installed, Claude can run:

```bash
# Full build
./gradlew assembleGithubSnapshotDebug

# Lint check
./gradlew lintGithubSnapshotDebug

# Unit tests
./gradlew testGithubSnapshotDebugUnitTest
```

## ChatGPT Codex Cloud

### Setup

1. **Configure environment setup:** Add `scripts/setup-codex.sh` as your Codex environment setup script. The exact method depends on your Codex configuration, but typically:
   - Reference it in your Codex project settings as the setup command
   - Or run it manually at the start of each session

2. **Network access:** Ensure your Codex environment allows outbound access to:
   - `dl.google.com` — Android SDK and Google Maven repository
   - `repo1.maven.org` — Maven Central
   - `plugins.gradle.org` — Gradle Plugin Portal
   - `services.gradle.org` — Gradle wrapper downloads
   - `jitpack.io` — JitPack (for the Treessence library)

### What the Script Does

`scripts/setup-codex.sh`:
- Downloads Android command-line tools (~150MB)
- Installs platform android-35 and build-tools
- Accepts SDK licenses
- Fixes JVM proxy bypass issues
- Initializes git submodules (if any)
- Sets `ANDROID_HOME` and `ANDROID_SDK_ROOT`

## Troubleshooting

### "403 Forbidden" or "host_not_allowed" errors

**Cause:** The environment's network proxy is blocking access to required hosts.

**Fix for Claude Code for Web:** Switch Network Access from "Limited" to "Full" in project settings.

**Fix for Codex:** Check your environment's network configuration. The key host that must be reachable is `dl.google.com`.

### "Could not resolve dependencies" during Gradle build

**Cause:** Google Maven repository (`google()` in Gradle) isn't reachable, or the JVM is configured to bypass the proxy for `*.google.com` hosts.

**Fix:** The setup scripts fix this automatically by modifying `JAVA_TOOL_OPTIONS`. The `gradle.properties` file also includes `systemProp.http.nonProxyHosts=localhost|127.0.0.1` to override the default bypass list at the Gradle level.

### SDK installation succeeds but build fails

If the SDK installs but `./gradlew assembleGithubSnapshotDebug` fails:

1. Check `ANDROID_HOME` is set: `echo $ANDROID_HOME`
2. Verify the platform is installed: `ls $ANDROID_HOME/platforms/`
3. Check Gradle can reach Maven Central: `curl -sI https://repo1.maven.org/maven2/ | head -1`
4. Check Gradle can reach Google Maven: `curl -sI https://dl.google.com/dl/android/maven2/ | head -1`

### Build takes a long time on first run

The first Gradle build downloads all dependencies (~500MB+). Subsequent builds in the same session use the Gradle cache. If the session is ephemeral (resets between uses), each new session will need to re-download.

## Technical Details

### Why *.google.com proxy bypass is a problem

Cloud environments route all outbound traffic through an authenticated HTTP proxy. The JVM is configured with `JAVA_TOOL_OPTIONS` that includes:

```
-Dhttp.nonProxyHosts=...|*.googleapis.com|*.google.com
```

This tells Java to connect directly to `*.google.com` hosts (bypassing the proxy). But since the environment has no direct internet access — only through the proxy — these connections fail silently.

The setup scripts fix this by stripping `*.google.com` and `*.googleapis.com` from the `nonProxyHosts` list, ensuring all traffic goes through the proxy.

### Hosts required for Android builds

| Host | Purpose |
|------|---------|
| `dl.google.com` | Android SDK downloads, Google Maven repository |
| `repo1.maven.org` | Maven Central (Kotlin, Retrofit, OkHttp, etc.) |
| `plugins.gradle.org` | Gradle Plugin Portal |
| `services.gradle.org` | Gradle wrapper distribution |
| `jitpack.io` | JitPack (Treessence logging library) |
