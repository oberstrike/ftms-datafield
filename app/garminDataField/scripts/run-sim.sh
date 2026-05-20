#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE="${DEVICE:-fr970}"
PRG_FILE="$PROJECT_DIR/build/outputs/FtmsBridgeField-${DEVICE}.prg"

"$PROJECT_DIR/scripts/build.sh"

if ! command -v connectiq >/dev/null 2>&1; then
  echo "ERROR: connectiq not found. Add the active Garmin Connect IQ SDK bin directory to PATH." >&2
  exit 1
fi

if ! command -v monkeydo >/dev/null 2>&1; then
  echo "ERROR: monkeydo not found. Add the active Garmin Connect IQ SDK bin directory to PATH." >&2
  exit 1
fi

# Start simulator if it is not already running. This is harmless if one is already open on most setups.
(connectiq >/dev/null 2>&1 &)
sleep 2

echo "Running $PRG_FILE on simulator device $DEVICE..."
monkeydo "$PRG_FILE" "$DEVICE"
