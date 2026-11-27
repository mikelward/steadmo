# Steadmo

Your glucose, trend and CamAPS FX status in one place, with a trend arrow in the status bar
and alerts that say what's coming. Buttons open CamAPS FX for meals, Boost and Ease-off;
Steadmo never doses.

- **What it does and why**: [SPEC.md](SPEC.md)
- **Plan**: [TODO.md](TODO.md)
- **Integration research and mocks**: [docs/EXPLORATION.md](docs/EXPLORATION.md),
  [docs/mocks.html](docs/mocks.html)
- **Engineering conventions**: [AGENTS.md](AGENTS.md)
- **Installing a release APK**: [docs/RELEASES.md](docs/RELEASES.md)

**Status: MVP.** Reads CamAPS FX's "My diabetes info" notification. Not yet tried on a
real phone.

## Trying it

1. In CamAPS FX's notification settings (Android Settings → Apps → CamAPS FX →
   Notifications), turn on **My diabetes info**.
2. Install Steadmo, open it, and allow notification access and notifications.
3. Wait for the next CamAPS FX update. The trend arrow appears after 5 minutes of
   readings. "Show CamAPS FX notification" shows exactly what Steadmo read.

## Modules

`:core` is a plain Kotlin JVM module with the parser, trend and alert rules
(`./gradlew :core:test` needs no Android SDK). `:app` holds the listener and UI.

## Building

```sh
./gradlew assembleDebug   # build debug APK
./gradlew test            # unit tests
./gradlew test -Proborazzi.test.record=true   # also re-record screenshots
./gradlew koverVerify     # line coverage of 95% or more
./gradlew lint            # lint
```

Requires JDK 17+ and an Android SDK (`ANDROID_HOME`).
