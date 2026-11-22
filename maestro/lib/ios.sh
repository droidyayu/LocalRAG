#!/usr/bin/env bash
#
# iOS platform helpers for maestro/run_acceptance.sh.
# Sourced, not executed. Same function names as maestro/lib/android.sh.
#
# Expects APP_ID to be set by the runner; falls back to the demo id.
# IOS_APP_PATH points at the built simulator bundle (.app directory).

set -u

APP_ID="${APP_ID:-com.ayushig.localrag.demo}"
IOS_APP_PATH="${IOS_APP_PATH:-/Users/bgaurav/Ayushi/.factory-kmp-ios/dd/Build/Products/Debug-iphonesimulator/iosApp.app}"

# UDID of the single booted simulator, or empty when there is not exactly one.
ios_booted_udid() {
    xcrun simctl list devices booted -j 2>/dev/null | python3 2>/dev/null -c \
        'import json,sys
try:
    devices = json.load(sys.stdin).get("devices", {})
except ValueError:
    devices = {}
booted = [d["udid"] for runtimes in devices.values() for d in runtimes if d.get("udid")]
print(booted[0] if len(booted) == 1 else "")'
}

# Human-readable "name (udid)" of the booted simulator for the report.
ios_booted_name() {
    xcrun simctl list devices booted -j 2>/dev/null | python3 2>/dev/null -c \
        'import json,sys
try:
    devices = json.load(sys.stdin).get("devices", {})
except ValueError:
    devices = {}
booted = [d for runtimes in devices.values() for d in runtimes if d.get("udid")]
print(booted[0].get("name", "") if len(booted) == 1 else "")'
}

# Tools present and exactly one target ready.
platform_check() {
    command -v maestro >/dev/null || { echo "ERROR: maestro not on PATH (brew install maestro)" >&2; return 1; }
    command -v xcrun >/dev/null || { echo "ERROR: xcrun not on PATH (install Xcode)" >&2; return 1; }
    local udid
    udid="$(ios_booted_udid)"
    if [ -z "$udid" ]; then
        echo "ERROR: need exactly one booted iOS simulator (boot one, e.g. xcrun simctl boot <udid>)" >&2
        return 1
    fi
}

# Install the simulator .app bundle onto the booted simulator.
platform_install() {
    if [ ! -d "$IOS_APP_PATH" ]; then
        echo "ERROR: iosApp bundle not found at $IOS_APP_PATH (set IOS_APP_PATH)" >&2
        return 1
    fi
    xcrun simctl install booted "$IOS_APP_PATH"
}

# The unified log has nothing to clear; kept for interface symmetry.
platform_clear_logs() {
    return 0
}

# $1 = destination file (parent dir already exists).
platform_screenshot() {
    xcrun simctl io booted screenshot "$1"
}

# $1 = destination file (parent dir already exists).
platform_collect_logs() {
    xcrun simctl spawn booted log show --last 10m --style compact \
        --predicate 'process == "iosApp"' >"$1" 2>/dev/null
}

# Device name and UDID for the report.
platform_id() {
    echo "$(ios_booted_name) ($(ios_booted_udid))"
}
