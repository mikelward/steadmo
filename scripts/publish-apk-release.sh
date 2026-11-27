#!/usr/bin/env bash
# Publishes the builds of this commit as the GitHub release v<versionCode>, marked
# latest, so .../releases/latest/download/steadmo-debug.apk always serves the newest
# debug APK ("Get latest version" in debug builds; docs/RELEASES.md).
#
# Adapted from mikelward/snoozemo's publish-github-release.sh, which publishes an
# AAB for Play; this one attaches installable APKs instead.
#
# Needs: GH_TOKEN, HEAD_SHA, DEBUG_APK_PATH; RELEASE_APK_PATH when release signing
# is set up; and a complete (unshallow) clone, since the versionCode is the commit count.
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

# Fixed names: the debug one is what the latest-download link names.
assets=()
cp "$DEBUG_APK_PATH" "${RUNNER_TEMP}/steadmo-debug.apk"
assets+=("${RUNNER_TEMP}/steadmo-debug.apk")
if [ -n "${RELEASE_APK_PATH:-}" ]; then
  cp "$RELEASE_APK_PATH" "${RUNNER_TEMP}/steadmo.apk"
  assets+=("${RUNNER_TEMP}/steadmo.apk")
fi

notes="${RUNNER_TEMP}/release-notes.md"
{
  printf 'versionCode %s, built from %s.\n\n' "$code" "$HEAD_SHA"
  printf 'To install: download steadmo-debug.apk on the phone (or tap Get latest version in Steadmo), open it, '
  printf 'and allow installing from this source when asked. It installs over the previous debug build and keeps '
  printf 'your data. Use at your own risk: see the disclaimer in the app.\n'
} > "$notes"

if gh release view "$tag" >/dev/null 2>&1; then
  gh release upload "$tag" "${assets[@]}" --clobber
  gh release edit "$tag" --title "Steadmo ${code}" --notes-file "$notes" --prerelease=false --latest --draft=false
else
  gh release create "$tag" "${assets[@]}" --title "Steadmo ${code}" --notes-file "$notes" \
    --latest --target "$HEAD_SHA"
fi
echo "publish-apk-release: ended, published ${tag} with ${#assets[@]} APK(s)"
