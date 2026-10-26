#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${SWIPECODE_ENV:-}" ]]; then
  if [[ ! -f "$SWIPECODE_ENV" ]]; then echo 'SWIPECODE_ENV does not point to an existing file.' >&2; exit 1; fi
  source "$SWIPECODE_ENV"
elif [[ -f "$HOME/android/env.sh" ]]; then source "$HOME/android/env.sh"; fi
if ! command -v javac >/dev/null; then echo 'A JDK is required. Stop; no tools will be downloaded.' >&2; exit 1; fi
mkdir -p build/core-tests
javac -encoding UTF-8 --release 17 -d build/core-tests app/src/main/java/com/nurdun/swipecode/core/*.java tests/*.java
java -cp build/core-tests CoreTests
java -cp build/core-tests SessionTests
java -cp build/core-tests BindingTests
java -cp build/core-tests AllKeysTests
java -cp build/core-tests ImeTests
