# Releases

Every push to `main` builds a signed release APK and publishes it as a GitHub prerelease
tagged `v<versionCode>`, with `steadmo-<versionCode>.apk` attached
([`.github/workflows/release.yml`](../.github/workflows/release.yml)). Pull requests
upload a debug APK as a workflow artifact instead (`ci.yml`).

## Installing

On the phone, open the repository's
[Releases page](https://github.com/mikelward/steadmo/releases), download the newest
`steadmo-<n>.apk`, open it, and allow installing from that source when Android asks.
Later releases install over it and keep your data, because every release is signed with
the same key. The release app (`app.steadmo`) and a debug build (`app.steadmo.debug`)
can be installed side by side.

## One-time setup: the signing key

Releases need four repository secrets. Until they exist, the release workflow builds
nothing and leaves a warning saying so. Create the key once, on your own computer, and
keep the keystore file and passwords somewhere safe: losing them means the next release
can't install over the old one.

```sh
keytool -genkeypair -v -keystore steadmo-release.keystore -alias steadmo \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 steadmo-release.keystore > steadmo-release.keystore.b64   # macOS: base64 -i steadmo-release.keystore
```

Then add these in GitHub, under the repository's Settings → Secrets and variables →
Actions → New repository secret:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the contents of `steadmo-release.keystore.b64` |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password you chose |
| `RELEASE_KEY_ALIAS` | `steadmo` |
| `RELEASE_KEY_PASSWORD` | the key password (the same as the keystore password if you pressed Enter) |

Delete the `.b64` file afterwards. To publish without a new commit, run the **release**
workflow from the Actions tab.

## Building a signed APK locally

```sh
RELEASE_KEYSTORE_FILE=$PWD/steadmo-release.keystore RELEASE_KEYSTORE_PASSWORD=... \
  RELEASE_KEY_ALIAS=steadmo RELEASE_KEY_PASSWORD=... ./gradlew assembleRelease
```
