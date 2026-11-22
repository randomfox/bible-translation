#!/bin/bash
# Publish the translation packs built by scripts/app_db.py as assets of the GitHub release "translations".
# The app downloads catalog.json and the packs from there. Re-running replaces the assets in place.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO=randomfox/bible-translation
TAG=translations
python3 -I scripts/app_db.py
if ! gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release create "$TAG" --repo "$REPO" --title "Translation packs" \
    --notes "Downloadable translations for the Android app. The app reads catalog.json from this release. Built by scripts/app_db.py; see LICENSE.md for each text's licence."
fi
gh release upload "$TAG" --repo "$REPO" --clobber dist/packs/catalog.json dist/packs/*.db.gz
echo "Published: https://github.com/$REPO/releases/tag/$TAG"
