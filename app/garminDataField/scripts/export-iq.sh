#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_NAME="${APP_NAME:-FtmsDataField}"
OUT_DIR="$PROJECT_DIR/build/outputs"
OUT_FILE="$OUT_DIR/${APP_NAME}.iq"

if ! command -v monkeyc >/dev/null 2>&1; then
  echo "ERROR: monkeyc not found. Add the active Garmin Connect IQ SDK bin directory to PATH." >&2
  exit 1
fi

if [[ -z "${GARMIN_DEVELOPER_KEY:-}" ]]; then
  echo "ERROR: GARMIN_DEVELOPER_KEY is not set." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
cd "$PROJECT_DIR"

echo "Exporting Connect IQ package..."
monkeyc \
  -f monkey.jungle \
  -y "$GARMIN_DEVELOPER_KEY" \
  -o "$OUT_FILE" \
  -e \
  -w

echo "Exported: $OUT_FILE"
