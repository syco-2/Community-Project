#!/usr/bin/env bash
# Build the debug APK and install it on the connected device.
# Usage: ./build_and_install.sh [adb-serial]
set -euo pipefail

SERIAL="${1:-}"

./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
  echo "Build finished but $APK is missing." >&2
  exit 1
fi

if [[ -n "$SERIAL" ]]; then
  adb -s "$SERIAL" install -r "$APK"
else
  adb install -r "$APK"
fi

echo "Installed $APK"
