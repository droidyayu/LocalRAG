#!/usr/bin/env bash
#
# Cross-platform Maestro acceptance runner.
#
# Usage:
#   maestro/run_acceptance.sh --platform android|ios --lane scripted|model [--flow <name>]
#
# Lanes:
#   scripted  deterministic: the real bundle, retrieval, tools, output gate
#             and UI with only the model replaced by a scripted engine
#             (selected by the localrag.engine launch argument).
#   model     the real-model suite: needs the Gemma file already on the device
#             (scripts/push_model.sh / scripts/push_model_ios.sh).
#
# Prerequisites (checked below, not installed here):
#   - Maestro CLI on PATH  (https://maestro.dev — `brew install maestro`)
#   - Android: adb on PATH with exactly one device attached, debug APK built.
#   - iOS: Xcode (xcrun) with exactly one booted simulator, simulator .app
#     built; its path comes from IOS_APP_PATH.
#
# Output: ./maestro/report/<timestamp>-<platform>-<lane>/REPORT.md plus
# per-flow logs, a screenshot after each flow, and a filtered device log.
#
# Env overrides:
#   READY_LABEL  engine chip to wait for in the model lane; default
#                "ready · bm25". Set to "ready · hybrid" for builds with
#                -PlocalRagEmbed=true. (The scripted lane always waits for
#                the bm25 chip.)
#   IOS_APP_PATH path to the built iosApp.app (iOS only).
#   APK_PATH     path to the debug APK (Android only).

set -u
cd "$(dirname "$0")/.."

APP_ID="com.ayushig.localrag.demo"
READY_LABEL="${READY_LABEL:-ready · bm25}"

usage() {
    echo "Usage: maestro/run_acceptance.sh --platform android|ios --lane scripted|model [--flow <name>]"
}

PLATFORM=""
LANE=""
FLOW=""
while [ $# -gt 0 ]; do
    case "$1" in
        --platform|--lane|--flow)
            [ $# -ge 2 ] || { echo "ERROR: $1 needs a value" >&2; usage >&2; exit 1; }
            case "$1" in
                --platform) PLATFORM="$2" ;;
                --lane) LANE="$2" ;;
                --flow) FLOW="$2" ;;
            esac
            shift 2 ;;
        -h|--help)
            usage; exit 0 ;;
        *)
            echo "ERROR: unknown argument: $1" >&2; usage >&2; exit 1 ;;
    esac
done

case "$PLATFORM" in
    android|ios) ;;
    *) echo "ERROR: --platform must be android or ios" >&2; usage >&2; exit 1 ;;
esac
case "$LANE" in
    scripted|model) ;;
    *) echo "ERROR: --lane must be scripted or model" >&2; usage >&2; exit 1 ;;
esac

# shellcheck source=maestro/lib/android.sh
. "maestro/lib/${PLATFORM}.sh"

platform_check || exit 1

FLOW_DIR="maestro/flows/${LANE}"
FLOWS=()
for flow in "$FLOW_DIR"/*.yaml; do
    FLOWS+=("$flow")
done
if [ -n "$FLOW" ]; then
    want="$FLOW"
    case "$want" in
        *.yaml) ;;
        *) want="${want}.yaml" ;;
    esac
    MATCHED=()
    for flow in ${FLOWS[@]+"${FLOWS[@]}"}; do
        if [ "$(basename "$flow")" = "$want" ]; then
            MATCHED+=("$flow")
        fi
    done
    if [ "${#MATCHED[@]}" -eq 0 ]; then
        echo "ERROR: no flow named $FLOW in $FLOW_DIR" >&2
        exit 1
    fi
    FLOWS=(${MATCHED[@]+"${MATCHED[@]}"})
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT_DIR="maestro/report/${STAMP}-${PLATFORM}-${LANE}"

mkdir -p "$REPORT_DIR/screenshots"
# The whole flows tree is copied so relative runFlow paths (../common/...) keep working.
cp -R maestro/flows "$REPORT_DIR/flows"
# Maestro does not interpolate ${READY_LABEL} inside command arguments (it waits on
# the literal text), so stamp the label into run copies up front.
for flow in ${FLOWS[@]+"${FLOWS[@]}"}; do
    sed "s|\${READY_LABEL}|$READY_LABEL|g" "$flow" >"$REPORT_DIR/flows/$LANE/$(basename "$flow")"
done

platform_install || exit 1
platform_clear_logs || true

MAESTRO_ARGS=()
if [ "$PLATFORM" = "ios" ]; then
    MAESTRO_ARGS=(--platform ios --udid "$(ios_booted_udid)")
fi

pass=0; fail_count=0
summary_rows=()
i=0
for flow in ${FLOWS[@]+"${FLOWS[@]}"}; do
    i=$((i + 1))
    name="$(basename "$flow" .yaml)"
    log="$REPORT_DIR/${name}.log"
    start="$(date +%s)"
    echo "--- [$i/${#FLOWS[@]}] $name ---"
    if maestro ${MAESTRO_ARGS[@]+"${MAESTRO_ARGS[@]}"} test "$REPORT_DIR/flows/$LANE/$(basename "$flow")" >"$log" 2>&1; then
        result="PASS"; pass=$((pass + 1))
    else
        result="FAIL"; fail_count=$((fail_count + 1))
    fi
    end="$(date +%s)"
    platform_screenshot "$REPORT_DIR/screenshots/${name}.png" 2>/dev/null || true
    summary_rows+=("| $name | $result | $((end - start))s |")
    echo "$result ($((end - start))s) — log: $log"
done

platform_collect_logs "$REPORT_DIR/device-log.txt" || true

{
    echo "# Assistant acceptance report — $STAMP"
    echo
    echo "Platform: \`$PLATFORM\` · lane: \`$LANE\` · ready label: \`$READY_LABEL\` · target: \`$(platform_id)\`"
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
    if [ "$PLATFORM" = "android" ]; then
        echo "- Filtered logcat (LocalRagChat/LocalRagAgent/LocalRag): \`$REPORT_DIR/device-log.txt\`"
    else
        echo "- Filtered unified log (process iosApp): \`$REPORT_DIR/device-log.txt\`"
        echo "  (note: Kotlin println output may not reach the unified log)"
    fi
    if [ "$fail_count" -gt 0 ]; then
        echo
        echo "## Failing excerpts"
        echo
        for flow in ${FLOWS[@]+"${FLOWS[@]}"}; do
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
