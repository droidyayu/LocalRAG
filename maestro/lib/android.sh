#!/usr/bin/env bash
#
# Android platform helpers for maestro/run_acceptance.sh.
# Sourced, not executed. Same function names as maestro/lib/ios.sh.
#
# Expects APP_ID to be set by the runner; falls back to the demo id.

set -u

APP_ID="${APP_ID:-com.ayushig.localrag.demo}"
APK_PATH="${APK_PATH:-androidApp/build/outputs/apk/debug/androidApp-debug.apk}"

# Tools present and exactly one target ready.
platform_check() {
    command -v maestro >/dev/null || { echo "ERROR: maestro not on PATH (brew install maestro)" >&2; return 1; }
    command -v adb >/dev/null || { echo "ERROR: adb not on PATH" >&2; return 1; }
    local count
    count="$(adb devices | grep -c -w device)"
    if [ "$count" -eq 0 ]; then
        echo "ERROR: no adb device attached (plug one in or start an emulator)" >&2
        return 1
    fi
    if [ "$count" -gt 1 ]; then
        echo "ERROR: $count adb devices attached, need exactly one:" >&2
        adb devices >&2
        return 1
    fi
}

# Install the debug APK (reinstalls over the existing copy).
platform_install() {
    if [ ! -f "$APK_PATH" ]; then
        echo "ERROR: APK not found at $APK_PATH (./gradlew :androidApp:installDebug)" >&2
        return 1
    fi
    adb install -r "$APK_PATH"
}

platform_clear_logs() {
    adb logcat -c
}

# $1 = destination file (parent dir already exists).
platform_screenshot() {
    adb exec-out screencap -p >"$1" 2>/dev/null
}

# $1 = destination file (parent dir already exists).
platform_collect_logs() {
    adb logcat -d -s LocalRagChat LocalRagAgent LocalRag >"$1" 2>/dev/null
}

# Device serial for the report.
platform_id() {
    adb devices | awk 'NR==2{print $1}'
}
