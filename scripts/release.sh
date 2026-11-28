#!/bin/bash
# Publish the Claude Translation as a GitHub release for Bible Reader.
#
#   scripts/release.sh ["release notes"]
#
# Builds the pack from bible.sqlite (scripts/pack.py) and publishes dist/CT.db.gz and dist/ct.json as the release
# ct-<version>, marked latest, at the current commit. Bible Reader bundles the latest release when it is built, and
# installed apps (0.5.1 and later) offer it as an update on their Translations screen. The version is a hash of the
# pack's contents, so releasing an unchanged translation again does nothing.
#
# Rebuild bible.sqlite first (scripts/export.py) and commit and push everything: the release points at this commit.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO=randomfox/bible-translation
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "Commit your changes first: the release is tagged at the current commit." >&2; exit 1
fi
git fetch -q origin
if [ "$(git rev-parse HEAD)" != "$(git rev-parse '@{u}')" ]; then
  echo "Push first: this branch and origin differ." >&2; exit 1
fi
python3 -I scripts/pack.py
VERSION=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' dist/ct.json)
TAG=ct-$VERSION
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "This translation is already released as $TAG: https://github.com/$REPO/releases/tag/$TAG"; exit 0
fi
NOTES="${1:-The Claude Translation as of $(git log -1 --format='%h, %ad' --date=short): $(git log -1 --format=%s).}"
git tag -a "$TAG" -m "Claude Translation $VERSION"
git push -q origin "refs/tags/$TAG"
gh release create "$TAG" --repo "$REPO" --verify-tag --latest --title "Claude Translation $(date +%Y-%m-%d) ($VERSION)" \
  --notes "$NOTES

Version \`$VERSION\`. \`CT.db.gz\` is the pack Bible Reader installs (https://github.com/randomfox/bible-reader); \`ct.json\` gives its version, size and SHA-256. The translation itself is in this repository at this tag. CC0 1.0; the New Testament is translated from the SBLGNT (CC BY 4.0), see LICENSE.md." \
  dist/CT.db.gz dist/ct.json
echo "Released: https://github.com/$REPO/releases/tag/$TAG"
