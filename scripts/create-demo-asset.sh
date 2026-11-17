#!/usr/bin/env bash
set -euo pipefail

# Creates a real, playable offline demo clip so the full installer includes
# sample media and the APK size can be verified without arbitrary padding.
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required to generate the bundled demo clip." >&2
  exit 1
fi

OUT="app/src/main/assets/mediahub-demo.mp4"
mkdir -p "$(dirname "$OUT")"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=30" \
  -f lavfi -i "sine=frequency=440:sample_rate=48000" \
  -t 26 -c:v libx264 -preset ultrafast -pix_fmt yuv420p \
  -b:v 8M -minrate 8M -maxrate 8M -bufsize 16M \
  -x264-params "nal-hrd=cbr:force-cfr=1" \
  -c:a aac -b:a 128k -movflags +faststart "$OUT"

SIZE="$(stat -c %s "$OUT")"
MINIMUM=$((20 * 1024 * 1024))
if [ "$SIZE" -lt "$MINIMUM" ]; then
  echo "Generated demo media is too small: $SIZE bytes (minimum $MINIMUM)." >&2
  exit 1
fi
echo "Bundled offline demo clip: $SIZE bytes"
