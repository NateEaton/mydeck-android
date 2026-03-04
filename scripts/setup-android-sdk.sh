#!/usr/bin/env bash
# setup-android-sdk.sh — Install Android SDK for cloud AI coding environments
# Used by Claude Code for Web (via SessionStart hook) and can be sourced by Codex setup.
#
# This script:
# 1. Downloads Android command-line tools
# 2. Installs the required SDK platform and build tools
# 3. Accepts licenses non-interactively
# 4. Fixes JVM proxy bypass issues with *.google.com hosts
# 5. Exports ANDROID_HOME via CLAUDE_ENV_FILE (if available) or to current shell

set -euo pipefail

ANDROID_SDK_DIR="/usr/lib/android-sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
CMDLINE_TOOLS_ZIP="/tmp/android-commandlinetools.zip"

# SDK components required by this project (compileSdk=35)
SDK_PACKAGES=(
    "platforms;android-35"
    "build-tools;35.0.1"
)

# ─── Fix JVM proxy bypass ────────────────────────────────────────────────────
# Claude Code for Web sets JAVA_TOOL_OPTIONS with http.nonProxyHosts that
# includes *.google.com and *.googleapis.com. This causes Java/Gradle to
# bypass the proxy for dl.google.com (Google Maven), which fails because
# all egress must go through the proxy.
#
# We fix this by stripping those patterns from nonProxyHosts.
fix_java_proxy() {
    if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
        # Remove *.googleapis.com and *.google.com from nonProxyHosts
        local fixed
        fixed=$(echo "$JAVA_TOOL_OPTIONS" | sed \
            -e 's/|\*\.googleapis\.com//g' \
            -e 's/|\*\.google\.com//g' \
            -e 's/\*\.googleapis\.com|//g' \
            -e 's/\*\.google\.com|//g' \
            -e 's/|metadata\.google\.internal//g' \
            -e 's/metadata\.google\.internal|//g')
        export JAVA_TOOL_OPTIONS="$fixed"
        echo "[setup-android-sdk] Fixed JAVA_TOOL_OPTIONS proxy bypass for Google hosts"
    fi
}

# ─── Export environment variables ─────────────────────────────────────────────
export_env() {
    local var_name="$1"
    local var_value="$2"

    export "$var_name=$var_value"

    # Claude Code for Web: persist via CLAUDE_ENV_FILE
    if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
        echo "export $var_name=\"$var_value\"" >> "$CLAUDE_ENV_FILE"
    fi
}

# ─── Check if SDK is already installed ────────────────────────────────────────
if [ -d "$ANDROID_SDK_DIR/cmdline-tools" ] && [ -d "$ANDROID_SDK_DIR/platforms/android-35" ]; then
    echo "[setup-android-sdk] Android SDK already installed, skipping download"
    export_env "ANDROID_HOME" "$ANDROID_SDK_DIR"
    export_env "ANDROID_SDK_ROOT" "$ANDROID_SDK_DIR"
    fix_java_proxy
    # Also persist the fixed JAVA_TOOL_OPTIONS
    if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
        echo "export JAVA_TOOL_OPTIONS=\"$JAVA_TOOL_OPTIONS\"" >> "$CLAUDE_ENV_FILE"
    fi
    echo "[setup-android-sdk] Ready. ANDROID_HOME=$ANDROID_SDK_DIR"
    exit 0
fi

echo "[setup-android-sdk] Starting Android SDK installation..."

# Fix proxy before any downloads that use Java
fix_java_proxy

# ─── Download command-line tools ──────────────────────────────────────────────
if [ ! -f "$CMDLINE_TOOLS_ZIP" ]; then
    echo "[setup-android-sdk] Downloading Android command-line tools..."
    wget -q -O "$CMDLINE_TOOLS_ZIP" "$CMDLINE_TOOLS_URL"
fi

# ─── Install command-line tools ───────────────────────────────────────────────
echo "[setup-android-sdk] Extracting command-line tools..."
mkdir -p "$ANDROID_SDK_DIR"
unzip -q -o "$CMDLINE_TOOLS_ZIP" -d "$ANDROID_SDK_DIR"
rm -f "$CMDLINE_TOOLS_ZIP"

# The zip extracts to cmdline-tools/. The sdkmanager expects it at
# cmdline-tools/latest/ for proper version management.
if [ -d "$ANDROID_SDK_DIR/cmdline-tools/bin" ] && [ ! -d "$ANDROID_SDK_DIR/cmdline-tools/latest" ]; then
    mkdir -p "$ANDROID_SDK_DIR/cmdline-tools-tmp"
    mv "$ANDROID_SDK_DIR/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools-tmp/latest"
    mv "$ANDROID_SDK_DIR/cmdline-tools-tmp" "$ANDROID_SDK_DIR/cmdline-tools"
fi

SDKMANAGER="$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

if [ ! -f "$SDKMANAGER" ]; then
    # Fallback: try the flat structure
    SDKMANAGER="$ANDROID_SDK_DIR/cmdline-tools/bin/sdkmanager"
fi

if [ ! -f "$SDKMANAGER" ]; then
    echo "[setup-android-sdk] ERROR: sdkmanager not found after extraction"
    exit 1
fi

# ─── Accept licenses ─────────────────────────────────────────────────────────
echo "[setup-android-sdk] Accepting SDK licenses..."
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_DIR" --licenses > /dev/null 2>&1 || true

# ─── Install SDK packages ────────────────────────────────────────────────────
echo "[setup-android-sdk] Installing SDK packages..."
for pkg in "${SDK_PACKAGES[@]}"; do
    echo "[setup-android-sdk]   Installing $pkg..."
    yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_DIR" "$pkg" > /dev/null 2>&1
done

# ─── Export environment ───────────────────────────────────────────────────────
export_env "ANDROID_HOME" "$ANDROID_SDK_DIR"
export_env "ANDROID_SDK_ROOT" "$ANDROID_SDK_DIR"

# Persist the fixed JAVA_TOOL_OPTIONS
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export JAVA_TOOL_OPTIONS=\"$JAVA_TOOL_OPTIONS\"" >> "$CLAUDE_ENV_FILE"
fi

# ─── Verify installation ─────────────────────────────────────────────────────
echo "[setup-android-sdk] Verifying installation..."
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_DIR" --list_installed 2>/dev/null | head -20

echo "[setup-android-sdk] Done. ANDROID_HOME=$ANDROID_SDK_DIR"
