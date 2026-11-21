#!/usr/bin/env bash
#
# Local CI for LocalRAG. Runs every check the factory gates on and prints an evidence table.
#
# Usage:
#   scripts/verify.sh [--quick] [--skip-ios]
#
#   --quick     core tests and the platform-dependency check only
#   --skip-ios  skip steps that need Xcode (they are listed as SKIPPED, never as passed)
#
# A step skipped by a flag is shown as SKIPPED and does not fail the run, so the table always says
# what was left out. A step that is expected but could not start counts as NOT RUN and fails it.
#
# Env:
#   GRADLE   gradle command to use (default: ./gradlew if its distribution is cached, else gradle)
#
# Output: build/verify/<head-sha>.md (git-ignored) plus the table on stdout.

set -u
cd "$(dirname "$0")/.."

QUICK=0
SKIP_IOS=0
for arg in "$@"; do
    case "$arg" in
        --quick) QUICK=1 ;;
        --skip-ios) SKIP_IOS=1 ;;
        *) echo "unknown flag: $arg" >&2; exit 2 ;;
    esac
done

if [ -z "${GRADLE:-}" ]; then
    if [ -d "$HOME/.gradle/wrapper/dists/gradle-9.5.0-bin" ] \
        && find "$HOME/.gradle/wrapper/dists/gradle-9.5.0-bin" -name "gradle-9.5.0" -maxdepth 2 | grep -q .; then
        GRADLE=./gradlew
    else
        GRADLE=gradle
    fi
fi

SHA="$(git rev-parse --short HEAD)"
DIRTY=""
[ -n "$(git status --porcelain -- . ':!.idea')" ] && DIRTY=" (dirty tree)"
OUT_DIR="build/verify"
mkdir -p "$OUT_DIR/logs"
REPORT="$OUT_DIR/${SHA}.md"

rows=()
failed=0
not_run=0

# step <name> <command...>
step() {
    local name="$1"; shift
    local log="$OUT_DIR/logs/${SHA}-$(echo "$name" | tr -c 'a-zA-Z0-9' '-').log"
    local start end
    start="$(date +%s)"
    if "$@" >"$log" 2>&1; then
        rows+=("| $name | PASS | $(( $(date +%s) - start ))s |")
    else
        end="$(date +%s)"
        rows+=("| $name | **FAIL** | $((end - start))s (log: $log) |")
        failed=$((failed + 1))
        tail -15 "$log" >&2
    fi
}

skip() {
    rows+=("| $1 | SKIPPED | $2 |")
}

step "core tests (jvm)" $GRADLE -p localrag-tooling :localrag-core:jvmTest --console=plain
step "core stays platform-free" $GRADLE -p localrag-tooling :localrag-core:verifyNoPlatformDependencies --console=plain

if [ "$QUICK" -eq 0 ]; then
    if [ "$SKIP_IOS" -eq 0 ] && grep -q "iosSimulatorArm64" localrag-tooling/localrag-core/build.gradle.kts 2>/dev/null; then
        step "core tests (ios simulator)" $GRADLE -p localrag-tooling :localrag-core:iosSimulatorArm64Test --console=plain
    else
        skip "core tests (ios simulator)" "no iOS target yet, or --skip-ios"
    fi

    SDK_MODULE=":localrag-android"
    [ -d localrag ] && SDK_MODULE=":localrag"
    DEMO_MODULE=":app"
    [ -d androidApp ] && DEMO_MODULE=":androidApp"

    step "sdk unit tests" $GRADLE "${SDK_MODULE}:testDebugUnitTest" --console=plain
    step "demo unit tests" $GRADLE "${DEMO_MODULE}:testDebugUnitTest" --console=plain
    step "demo assembleDebug" $GRADLE "${DEMO_MODULE}:assembleDebug" --console=plain
    step "docs bundle builds" $GRADLE "${DEMO_MODULE}:generateLocalRagBundle" --console=plain
else
    skip "sdk, demo, bundle" "--quick"
fi

{
    echo "# verify ${SHA}${DIRTY}"
    echo
    echo "Gradle: \`$GRADLE\` · $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "| step | result | time |"
    echo "| ---- | ------ | ---- |"
    printf "%s\n" "${rows[@]}"
    echo
    echo "Failed: $failed · Not run: $not_run"
} | tee "$REPORT"

[ "$failed" -eq 0 ] && [ "$not_run" -eq 0 ]
