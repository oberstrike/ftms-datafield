#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE="${DEVICE:-fr970}"
VARIANT="${VARIANT:-all}"
OUT_DIR="$PROJECT_DIR/build/outputs"

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

node tools/garmin-variants.mjs prepare

while IFS='|' read -r VARIANT_KEY VARIANT_LABEL; do
  if [[ "$VARIANT" != "all" && "$VARIANT" != "$VARIANT_KEY" ]]; then
    continue
  fi

  JUNGLE_FILE="$PROJECT_DIR/build/generated/garmin-variants/$VARIANT_KEY/monkey.jungle"
  OUT_FILE="$OUT_DIR/FtmsDataField-${VARIANT_KEY}-${DEVICE}.prg"

  echo "Building $VARIANT_LABEL ($VARIANT_KEY) for $DEVICE..."
  monkeyc \
    -f "$JUNGLE_FILE" \
    -d "$DEVICE" \
    -y "$GARMIN_DEVELOPER_KEY" \
    -o "$OUT_FILE" \
    -w

  echo "Built: $OUT_FILE"
done < <(node tools/garmin-variants.mjs list)

if [[ "$VARIANT" != "all" && ! -f "$OUT_DIR/FtmsDataField-${VARIANT}-${DEVICE}.prg" ]]; then
  echo "ERROR: unknown variant '$VARIANT'" >&2
  exit 1
fi

echo "For sideloading, copy this .prg to GARMIN/APPS/ on the watch."
