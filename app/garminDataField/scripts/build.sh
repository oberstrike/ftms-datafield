#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE="${DEVICE:-fr970}"
OUT_DIR="$PROJECT_DIR/build/outputs"
OUT_FILE="$OUT_DIR/FtmsBridgeField-${DEVICE}.prg"

if ! command -v monkeyc >/dev/null 2>&1; then
  echo "ERROR: monkeyc not found. Add the active Garmin Connect IQ SDK bin directory to PATH." >&2
  exit 1
fi

if [[ -z "${GARMIN_DEVELOPER_KEY:-}" ]]; then
  echo "ERROR: GARMIN_DEVELOPER_KEY is not set." >&2
  echo "Set it to your Connect IQ developer key file, e.g.:" >&2
  echo "  export GARMIN_DEVELOPER_KEY=\$HOME/garmin-dev/ciq.key" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
cd "$PROJECT_DIR"
rm -f "$OUT_DIR"/FtmsDataField-*-*.prg \
  "$OUT_DIR"/FtmsDataField-*-*.prg.debug.xml \
  "$OUT_DIR"/FtmsDataField-*-*-fit_contributions.json \
  "$OUT_DIR"/FtmsDataField-*-*-settings.json

echo "Building FTMS Bridge Field for $DEVICE..."
monkeyc \
  -f monkey.jungle \
  -d "$DEVICE" \
  -y "$GARMIN_DEVELOPER_KEY" \
  -o "$OUT_FILE" \
  -w

echo "Built: $OUT_FILE"
echo "For sideloading, copy this .prg to GARMIN/APPS/ on the watch."
