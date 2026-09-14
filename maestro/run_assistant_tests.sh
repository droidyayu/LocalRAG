#!/usr/bin/env bash
#
# Runs the assistant Maestro acceptance suite and writes a success report.
#
# Usage:
#   ./maestro/run_assistant_tests.sh
#
# Prerequisites (checked below, not installed here):
#   - Maestro CLI on PATH  (https://maestro.dev — `brew install maestro`)
#   - adb on PATH with exactly one device attached
#   - the demo app installed (`./gradlew :app:installDebug`)
#   - the Gemma model file already pushed to the device
#
# Output: ./maestro/report/<timestamp>/REPORT.md plus per-flow logs,
# screenshots, and a filtered logcat for debugging.
#
# Env overrides:
#   READY_LABEL  engine chip to wait for; default "ready · bm25".
#                Set to "ready · hybrid" for builds with -PlocalRagEmbed=true.

set -u
cd "$(dirname "$0")/.."

READY_LABEL="${READY_LABEL:-ready · bm25}"
APP_ID="com.ayushig.localrag.demo"
STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT_DIR="maestro/report/${STAMP}"
FLOWS=(maestro/flows/01_portfolio.yaml maestro/flows/02_docs.yaml maestro/flows/03_honesty.yaml)

fail() { echo "ERROR: $*" >&2; exit 1; }

command -v maestro >/dev/null || fail "maestro not on PATH (brew install maestro)"
command -v adb >/dev/null || fail "adb not on PATH"
[ "$(adb devices | grep -c -w device)" -ge 1 ] || fail "no adb device attached"
adb shell cmd package list packages | grep -q "$APP_ID" \
    || fail "$APP_ID not installed (./gradlew :app:installDebug)"

mkdir -p "$REPORT_DIR/screenshots" "$REPORT_DIR/flows"
# Maestro does not interpolate ${READY_LABEL} inside command arguments (it waits on
# the literal text), so stamp the label into run copies up front.
for flow in "${FLOWS[@]}"; do
    sed "s|\${READY_LABEL}|$READY_LABEL|g" "$flow" >"$REPORT_DIR/flows/$(basename "$flow")"
done
adb logcat -c

pass=0; fail_count=0
summary_rows=()
i=0
for flow in "${FLOWS[@]}"; do
    i=$((i + 1))
    name="$(basename "$flow" .yaml)"
    log="$REPORT_DIR/${name}.log"
    start="$(date +%s)"
    echo "--- [$i/${#FLOWS[@]}] $name ---"
    if maestro test "$REPORT_DIR/flows/$(basename "$flow")" >"$log" 2>&1; then
        result="PASS"; pass=$((pass + 1))
    else
        result="FAIL"; fail_count=$((fail_count + 1))
    fi
    end="$(date +%s)"
    adb exec-out screencap -p >"$REPORT_DIR/screenshots/${name}.png" 2>/dev/null || true
    summary_rows+=("| $name | $result | $((end - start))s |")
    echo "$result ($((end - start))s) — log: $log"
done

adb logcat -d -s LocalRagChat LocalRagAgent LocalRag >"$REPORT_DIR/logcat.txt" 2>/dev/null || true

{
    echo "# Assistant acceptance report — $STAMP"
    echo
    echo "Ready label: \`$READY_LABEL\` · device: \`$(adb devices | awk 'NR==2{print $1}')\`"
    echo
    echo "| flow | result | duration |"
    echo "| ---- | ------ | -------- |"
    printf "%s\n" "${summary_rows[@]}"
    echo
    echo "## Totals"
    echo
    echo "Passed: $pass · Failed: $fail_count"
    echo
    echo "## Artifacts"
    echo
    echo "- Per-flow Maestro console logs: \`$REPORT_DIR/\`"
    echo "- Screenshots after each flow: \`$REPORT_DIR/screenshots/\`"
    echo "- Filtered logcat (LocalRagChat/LocalRagAgent/LocalRag): \`$REPORT_DIR/logcat.txt\`"
    if [ "$fail_count" -gt 0 ]; then
        echo
        echo "## Failing excerpts"
        echo
        for flow in "${FLOWS[@]}"; do
            name="$(basename "$flow" .yaml)"
            echo "### $name"
            echo
            echo '```'
            grep -i -m 10 -E "✕|failed|error|assertion" "$REPORT_DIR/${name}.log" \
                || echo "(no failure lines matched — see full log)"
            echo '```'
            echo
        done
    fi
} >"$REPORT_DIR/REPORT.md"

echo
echo "==============================="
echo "Passed: $pass · Failed: $fail_count"
echo "Report: $REPORT_DIR/REPORT.md"
[ "$fail_count" -eq 0 ]
