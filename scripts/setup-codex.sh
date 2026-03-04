#!/usr/bin/env bash
# setup-codex.sh — Install Android SDK for ChatGPT Codex Cloud environment
#
# Usage: Add this script as the Codex environment setup command, or run it
# manually at the start of a Codex session.
#
# This script handles Codex-specific differences:
# - No CLAUDE_ENV_FILE; exports are set in the current shell
# - Codex may have different proxy/network configurations
# - Includes git submodule init (common Codex setup step)

set -euo pipefail

ANDROID_SDK_DIR="/usr/lib/android-sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
CMDLINE_TOOLS_ZIP="/tmp/android-commandlinetools.zip"

SDK_PACKAGES=(
    "platforms;android-35"
    "build-tools;35.0.1"
)

# ─── Fix JVM proxy settings if needed ────────────────────────────────────────
# Some cloud environments set JAVA_TOOL_OPTIONS with nonProxyHosts that bypass
# the proxy for *.google.com, causing Google Maven to fail when all egress
# must go through a proxy. Strip those patterns.
fix_java_proxy() {
    if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
        if echo "$JAVA_TOOL_OPTIONS" | grep -q 'google\.com'; then
            JAVA_TOOL_OPTIONS=$(echo "$JAVA_TOOL_OPTIONS" | sed \
                -e 's/|\*\.googleapis\.com//g' \
                -e 's/|\*\.google\.com//g' \
                -e 's/\*\.googleapis\.com|//g' \
                -e 's/\*\.google\.com|//g' \
                -e 's/|metadata\.google\.internal//g' \
                -e 's/metadata\.google\.internal|//g')
            export JAVA_TOOL_OPTIONS
            echo "[setup-codex] Fixed JAVA_TOOL_OPTIONS proxy bypass for Google hosts"
        fi
    fi
}

# ─── Check if SDK is already installed ────────────────────────────────────────
if [ -d "$ANDROID_SDK_DIR/cmdline-tools" ] && [ -d "$ANDROID_SDK_DIR/platforms/android-35" ]; then
    echo "[setup-codex] Android SDK already installed, skipping"
    export ANDROID_HOME="$ANDROID_SDK_DIR"
    export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
    fix_java_proxy
    echo "[setup-codex] Ready. ANDROID_HOME=$ANDROID_SDK_DIR"
    exit 0
fi

echo "[setup-codex] Starting Android SDK installation..."

fix_java_proxy

# ─── Download command-line tools ──────────────────────────────────────────────
echo "[setup-codex] Downloading Android command-line tools..."
wget -q -O "$CMDLINE_TOOLS_ZIP" "$CMDLINE_TOOLS_URL" || {
    echo "[setup-codex] ERROR: Failed to download command-line tools."
    echo "[setup-codex] This usually means dl.google.com is blocked by network policy."
    echo "[setup-codex] Check your Codex environment's network access settings."
    exit 1
}

# ─── Install command-line tools ───────────────────────────────────────────────
echo "[setup-codex] Extracting command-line tools..."
mkdir -p "$ANDROID_SDK_DIR"
unzip -q -o "$CMDLINE_TOOLS_ZIP" -d "$ANDROID_SDK_DIR"
rm -f "$CMDLINE_TOOLS_ZIP"

# Reorganize to expected directory structure
if [ -d "$ANDROID_SDK_DIR/cmdline-tools/bin" ] && [ ! -d "$ANDROID_SDK_DIR/cmdline-tools/latest" ]; then
    mkdir -p "$ANDROID_SDK_DIR/cmdline-tools-tmp"
    mv "$ANDROID_SDK_DIR/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools-tmp/latest"
    mv "$ANDROID_SDK_DIR/cmdline-tools-tmp" "$ANDROID_SDK_DIR/cmdline-tools"
fi

SDKMANAGER="$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ ! -f "$SDKMANAGER" ]; then
    SDKMANAGER="$ANDROID_SDK_DIR/cmdline-tools/bin/sdkmanager"
fi

if [ ! -f "$SDKMANAGER" ]; then
    echo "[setup-codex] ERROR: sdkmanager not found after extraction"
    exit 1
fi

# ─── Accept licenses ─────────────────────────────────────────────────────────
echo "[setup-codex] Accepting SDK licenses..."
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_DIR" --licenses > /dev/null 2>&1 || true

# ─── Install SDK packages ────────────────────────────────────────────────────
echo "[setup-codex] Installing SDK packages..."
for pkg in "${SDK_PACKAGES[@]}"; do
    echo "[setup-codex]   Installing $pkg..."
    yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_DIR" "$pkg" > /dev/null 2>&1 || {
        echo "[setup-codex] WARNING: Failed to install $pkg"
        echo "[setup-codex] This may be a network access issue. Check if dl.google.com is reachable."
    }
done

# ─── Set environment variables ────────────────────────────────────────────────
export ANDROID_HOME="$ANDROID_SDK_DIR"
export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"

# ─── Init git submodules (if any) ────────────────────────────────────────────
if [ -f ".gitmodules" ]; then
    echo "[setup-codex] Initializing git submodules..."
    git submodule update --init --recursive || true
fi

# ─── Verify ───────────────────────────────────────────────────────────────────
echo "[setup-codex] Verifying installation..."
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_DIR" --list_installed 2>/dev/null | head -20

echo "[setup-codex] Done. ANDROID_HOME=$ANDROID_SDK_DIR"
echo ""
echo "[setup-codex] You can now run:"
echo "  ./gradlew assembleGithubSnapshotDebug    # Build"
echo "  ./gradlew lintGithubSnapshotDebug        # Lint"
echo "  ./gradlew testGithubSnapshotDebugUnitTest # Unit tests"
