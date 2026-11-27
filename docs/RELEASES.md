# Releases

Every push to `main` publishes a GitHub release tagged `v<versionCode>`, marked latest
([`.github/workflows/release.yml`](../.github/workflows/release.yml)):

- `steadmo-debug.apk`: the debug build (`app.steadmo.debug`, "Steadmo Debug"). Until Steadmo is
  on Google Play, this is the one to install.
- `steadmo.apk`: the signed release build (`app.steadmo`), once release signing is set up
  (below).

## Installing and updating the debug build

On the phone, tap **⋮ → Get latest version** in Steadmo, or open
<https://github.com/mikelward/steadmo/releases/latest/download/steadmo-debug.apk>. The
browser downloads the APK; open it and allow installing from that source when Android asks.

Every debug build, from CI or a developer's machine, is signed with the same debug key,
committed as `app/debug.keystore` (Android's standard debug key, password `android`). So each
new debug APK installs over the last one and keeps your data. The key isn't a secret, and
nobody should treat a debug build as more than a family test build.

## Release signing (for later, and for Play)

The signed release APK needs four repository secrets. Until they exist, the workflow
publishes the debug APK only and leaves a notice saying so. Create the key once, on your own
computer, and keep the keystore file and passwords safe: losing them means the next release
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
