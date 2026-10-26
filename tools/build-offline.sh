#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${SWIPECODE_ENV:-}" ]]; then
  if [[ ! -f "$SWIPECODE_ENV" ]]; then echo 'SWIPECODE_ENV does not point to an existing file.' >&2; exit 1; fi
  source "$SWIPECODE_ENV"
elif [[ -f "$HOME/android/env.sh" ]]; then source "$HOME/android/env.sh"; fi
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "${ANDROID_HOME:-}" ]]; then echo 'ANDROID_HOME is not set. No components will be downloaded.' >&2; exit 1; fi
AAPT="$ANDROID_HOME/build-tools/35.0.0/aapt2"
if [[ ! -x "$AAPT" ]]; then echo "Missing installed build tool: $AAPT. Stop; do not download automatically." >&2; exit 1; fi
if ! command -v gradle >/dev/null; then echo 'Gradle is not on PATH. Stop; no tools will be downloaded.' >&2; exit 1; fi
bash tools/test-core.sh
gradle --offline --no-daemon --console=plain "-Pandroid.aapt2FromMavenOverride=$AAPT" :app:assembleDebug :app:assembleDebugAndroidTest
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
