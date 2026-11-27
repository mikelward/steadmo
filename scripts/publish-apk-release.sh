#!/usr/bin/env bash
# Publishes the signed release APK as a GitHub prerelease tagged v<versionCode>,
# for installing from an unknown source (docs/RELEASES.md).
#
# Adapted from mikelward/snoozemo's publish-github-release.sh, which publishes an
# AAB for Play; this one attaches an installable APK instead.
#
# Needs: GH_TOKEN, HEAD_SHA, APK_PATH, and a complete (unshallow) clone, since
# the versionCode is the commit count.
set -euo pipefail

code=$(git rev-list --count HEAD)
tag="v${code}"
echo "publish-apk-release: started for ${tag} (${HEAD_SHA})"

latest=$(gh release list --limit 100 --json tagName \
  --jq '[.[] | .tagName | select(test("^v[0-9]+$")) | ltrimstr("v") | tonumber] | max // 0')
case "$latest" in
  '' | *[!0-9]*)
    echo "Could not read the existing releases (got '${latest}'); refusing to publish." >&2
    exit 1
    ;;
esac
if [ "$latest" -gt "$code" ]; then
  echo "A later push already published v${latest}; skipping v${code}."
  exit 0
fi

name="steadmo-${code}.apk"
asset="${RUNNER_TEMP}/${name}"
cp "$APK_PATH" "$asset"

notes="${RUNNER_TEMP}/release-notes.md"
{
  printf 'versionCode %s, built from %s.\n\n' "$code" "$HEAD_SHA"
  printf 'To install: download %s on the phone, open it, and allow installing from this source when asked. ' "$name"
  printf 'Later releases install over it, keeping your data, because they are signed with the same key.\n\n'
  printf 'Changes since the last release are in the commit log.\n'
} > "$notes"

if gh release view "$tag" >/dev/null 2>&1; then
  gh release upload "$tag" "$asset" --clobber
  gh release edit "$tag" --title "Steadmo ${code}" --notes-file "$notes" --prerelease --draft=false
else
  gh release create "$tag" "$asset" --title "Steadmo ${code}" --notes-file "$notes" \
    --prerelease --target "$HEAD_SHA"
fi
echo "publish-apk-release: ended, published ${tag} with ${name}"
