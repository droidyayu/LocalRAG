#!/usr/bin/env bash
#
# Runs the assistant real-model Maestro suite on Android.
#
# Thin wrapper kept so existing instructions keep working; all behaviour
# lives in maestro/run_acceptance.sh.
#
# Usage:
#   ./maestro/run_assistant_tests.sh
#
# Env overrides: see maestro/run_acceptance.sh (READY_LABEL et al).

set -u
cd "$(dirname "$0")/.."

exec maestro/run_acceptance.sh --platform android --lane model "$@"
