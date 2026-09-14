#!/usr/bin/env bash
# Pushes a LiteRT-LM generation model onto a connected device.
#
# The models are deliberately not bundled in the APK: even the small one would break the
# build. Download the file manually from its Hugging Face repo after accepting the Gemma
# license. Both repos are gated, so a scripted download returns an HTML error page rather
# than the model — this script checks the file size before pushing.
#
# Usage: scripts/push_model.sh [e2b|270m] [adb-serial]
# The pushed filename must match ModelOption.fileName or the app will not see the file.
# With no serial, a lone device is used as-is; when emulators crowd the list, the
# single physical device is auto-selected. Pass a serial explicitly to override.

set -euo pipefail

case "${1:-e2b}" in
  e2b)
    MODEL_NAME="gemma-4-E2B-it.litertlm"
    HF_REPO="litert-community/gemma-4-E2B-it-litert-lm"
    MIN_BYTES=$((1024 * 1024 * 1024))
    ;;
  270m)
    MODEL_NAME="gemma3-270m-it-q8.litertlm"
    HF_REPO="litert-community/gemma-3-270m-it"
    MIN_BYTES=$((250 * 1024 * 1024))
    ;;
  *)
    echo "Unknown model: $1 (want e2b or 270m)" >&2
    exit 1
    ;;
esac

APPLICATION_ID="com.ayushig.localrag.demo"

SOURCE="$MODEL_NAME"
SERIAL="${2:-}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
else
  # No serial given: a lone device needs no disambiguation, and one physical device
  # among emulators is the obvious target. Anything else is genuinely ambiguous.
  READY_COUNT=0
  PHYSICAL_COUNT=0
  CANDIDATE=""
  while IFS=$'\t' read -r serial state _; do
    [[ -n "$serial" && "$state" == "device" ]] || continue
    READY_COUNT=$((READY_COUNT + 1))
    case "$serial" in
      emulator-*) ;;
      *)
        PHYSICAL_COUNT=$((PHYSICAL_COUNT + 1))
        CANDIDATE="$serial"
        ;;
    esac
  done < <("${ADB[@]}" devices | tail -n +2)
  if (( READY_COUNT != 1 && PHYSICAL_COUNT == 1 )); then
    SERIAL="$CANDIDATE"
    ADB=(adb -s "$SERIAL")
    echo "Auto-selected physical device $SERIAL (pass a serial explicitly to target an emulator)."
  elif (( READY_COUNT != 1 )); then
    echo "Found $READY_COUNT ready devices and cannot pick one; pass a serial explicitly." >&2
    "${ADB[@]}" devices >&2
    echo "Usage: scripts/push_model.sh [e2b|270m] [adb-serial]" >&2
    exit 1
  fi
fi

if [[ ! -f "$SOURCE" ]]; then
  if [[ -n "${HF_TOKEN:-}" ]]; then
    echo "Model not found at: $SOURCE. Downloading via curl using HF_TOKEN..."
    curl -L -H "Authorization: Bearer $HF_TOKEN" -o "$SOURCE" "https://huggingface.co/${HF_REPO}/resolve/main/${MODEL_NAME}"
  else
    echo "Model not found at: $SOURCE" >&2
    echo "Download it from https://huggingface.co/${HF_REPO}" >&2
    echo "Or set the HF_TOKEN environment variable to download it automatically." >&2
    exit 1
  fi
fi

SIZE=$(wc -c < "$SOURCE" | tr -d ' ')
if (( SIZE < MIN_BYTES )); then
  echo "Refusing to push: $SOURCE is only $SIZE bytes." >&2
  echo "A gated-repo download usually fails to an HTML error page. Re-download it." >&2
  exit 1
fi

TARGET_DIR="/sdcard/Android/data/${APPLICATION_ID}/files"

# The directory only exists once the app has been launched at least once.
if ! "${ADB[@]}" shell "[ -d $TARGET_DIR ]"; then
  echo "$TARGET_DIR does not exist. Launch the app once, then re-run this script." >&2
  exit 1
fi

"${ADB[@]}" push "$SOURCE" "$TARGET_DIR/$MODEL_NAME"
"${ADB[@]}" shell ls -l "$TARGET_DIR/$MODEL_NAME"
