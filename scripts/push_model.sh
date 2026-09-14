#!/usr/bin/env bash
# Pushes the Gemma 4 E2B IT LiteRT-LM model onto a connected device.
#
# The model is deliberately not bundled in the APK: at ~2 GB it would break the build.
# Download it manually from https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
# after accepting the Gemma license. The repo is gated, so a scripted download returns an HTML
# error page rather than the model — this script checks the file size before pushing.
#
# Usage: scripts/push_model.sh [path-to-model] [adb-serial]
# The exact published filename may differ; override with MODEL_NAME=... if it does. It must
# match ModelFileLocator.MODEL_FILE_NAME or the app will not see the pushed file.

set -euo pipefail

MODEL_NAME="${MODEL_NAME:-gemma-4-E2B-it.litertlm}"
HF_REPO="litert-community/gemma-4-E2B-it-litert-lm"
APPLICATION_ID="com.ayushig.localrag.demo"
MIN_BYTES=$((200 * 1024 * 1024))

SOURCE="${1:-$MODEL_NAME}"
SERIAL="${2:-}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
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
