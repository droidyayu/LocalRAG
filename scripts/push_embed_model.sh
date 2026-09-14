#!/usr/bin/env bash
# Pushes the EmbeddingGemma 300M LiteRT model onto a connected device.
#
# Usage: scripts/push_embed_model.sh [path-to-model] [adb-serial]

set -euo pipefail

MODEL_NAME="embeddinggemma-300m-seq256.tflite"
APPLICATION_ID="com.ayushig.localrag.demo"
MIN_BYTES=$((50 * 1024 * 1024)) # Ensure it's not a small error page (model is ~1.2GB)

SOURCE="${1:-$MODEL_NAME}"
SERIAL="${2:-}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

if [[ ! -f "$SOURCE" ]]; then
  if [[ -n "${HF_TOKEN:-}" ]]; then
    echo "Model not found at: $SOURCE. Downloading via curl using HF_TOKEN..."
    curl -L -H "Authorization: Bearer $HF_TOKEN" -o "$SOURCE" "https://huggingface.co/litert-community/embeddinggemma-300m/resolve/main/embeddinggemma-300m-seq256-cpu.tflite"
  else
    echo "Model not found at: $SOURCE" >&2
    echo "Download it from https://huggingface.co/litert-community/embeddinggemma-300m" >&2
    echo "Or set the HF_TOKEN environment variable to download it automatically." >&2
    exit 1
  fi
fi

SIZE=$(wc -c < "$SOURCE" | tr -d ' ')
if (( SIZE < MIN_BYTES )); then
  echo "Refusing to push: $SOURCE is only $SIZE bytes." >&2
  echo "A gated-repo download usually fails to an HTML error page. Check your token and re-download it." >&2
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
