#!/usr/bin/env bash
# Pushes a LiteRT-LM generation model into the iOS app's sandbox.
#
# Mirrors scripts/push_model.sh: the same gated Hugging Face repos, the same
# size check (a scripted download returns an HTML error page rather than the
# model). The model file is looked up at ./<name> or $MODEL_DIR/<name>.
#
# Usage: scripts/push_model_ios.sh [e2b|270m] [simulator|device <udid>]
# The default target is the booted simulator. The app must have been installed
# (and launched) once so its data container exists. On a device the <udid> is
# required.
# The pushed filename must match ModelOption.fileName or the app will not see
# the file.

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
TARGET="${2:-simulator}"
UDID="${3:-}"

if [[ -n "${MODEL_DIR:-}" && ! -f "$SOURCE" && -f "$MODEL_DIR/$MODEL_NAME" ]]; then
  SOURCE="$MODEL_DIR/$MODEL_NAME"
fi

if [[ ! -f "$SOURCE" ]]; then
  if [[ -n "${HF_TOKEN:-}" ]]; then
    echo "Model not found at: $SOURCE. Downloading via curl using HF_TOKEN..."
    curl -L -H "Authorization: Bearer $HF_TOKEN" -o "$SOURCE" "https://huggingface.co/${HF_REPO}/resolve/main/${MODEL_NAME}"
  else
    echo "Model not found at: $SOURCE" >&2
    if [[ -n "${MODEL_DIR:-}" ]]; then
      echo "Also looked in: $MODEL_DIR/$MODEL_NAME" >&2
    fi
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

case "$TARGET" in
  simulator)
    CONTAINER="$(xcrun simctl get_app_container booted "$APPLICATION_ID" data)" \
      || { echo "Cannot resolve the app container. Install and launch the app on the booted simulator once, then re-run this script." >&2; exit 1; }
    DEST_DIR="$CONTAINER/Documents"
    mkdir -p "$DEST_DIR"
    cp "$SOURCE" "$DEST_DIR/$MODEL_NAME"
    ls -l "$DEST_DIR/$MODEL_NAME"
    ;;
  device)
    if [[ -z "$UDID" ]]; then
      echo "Usage: scripts/push_model_ios.sh [e2b|270m] [simulator|device <udid>]" >&2
      exit 1
    fi
    xcrun devicectl device copy to --device "$UDID" --source "$SOURCE" \
      --destination "Documents/$MODEL_NAME" \
      --domain-type appDataContainer --domain-identifier "$APPLICATION_ID"
    ;;
  *)
    echo "Unknown target: $TARGET (want simulator or device <udid>)" >&2
    echo "Usage: scripts/push_model_ios.sh [e2b|270m] [simulator|device <udid>]" >&2
    exit 1
    ;;
esac
