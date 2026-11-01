#!/usr/bin/env bash
# Builds the HackerTube TV source tree: pinned upstream SmartTube + this repo's overlay and patch.
# Usage: scripts/prepare.sh [dest-dir]   (default: build/SmartTube)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${1:-$ROOT/build/SmartTube}"
SHA="$(tr -d '[:space:]' < "$ROOT/UPSTREAM_COMMIT")"
# owner/repo this fork is published from; used for the in-app update URLs
REPO="${HACKERTUBE_REPO:-${GITHUB_REPOSITORY:-}}"
if [ -z "$REPO" ]; then
  REPO="$(git -C "$ROOT" remote get-url origin 2>/dev/null | sed -E 's#^(https://github.com/|git@github.com:)##; s#\.git$##')"
fi
[ -n "$REPO" ] || { echo "Set HACKERTUBE_REPO=owner/repo"; exit 1; }

echo "==> SmartTube @ $SHA -> $DEST"
rm -rf "$DEST"
git init -q "$DEST"
cd "$DEST"
git remote add origin https://github.com/yuliskov/SmartTube.git
git fetch -q --depth 1 origin "$SHA"
git checkout -q FETCH_HEAD
git submodule update --init --recursive --depth 1 || git submodule update --init --recursive

echo "==> Applying overlay"
cp -R "$ROOT/overlay/." "$DEST/"

echo "==> Update channel: github.com/$REPO"
grep -rl "__REPO__" "$ROOT/overlay" | sed "s#^$ROOT/overlay/##" | while read -r f; do
  sed -i "s#__REPO__#$REPO#g" "$DEST/$f"
done

echo "==> Applying patches"
for p in "$ROOT"/patches/*.patch; do
  echo "    $(basename "$p")"
  git apply --whitespace=nowarn "$p"
done
echo "==> Ready. Build with: cd $DEST && ./gradlew assembleSthackertubeRelease"
