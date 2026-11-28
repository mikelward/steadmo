# Steadmo

Your glucose, trend and CamAPS FX status in one place, with a trend arrow in the status bar
and alerts that say what's coming. Buttons open CamAPS FX for meals, Boost and Ease-off,
and optional CamAPS FX shortcuts go straight to the meal entry (with the carbs entered),
Boost or Ease-off for you to confirm;
Steadmo never doses.

- **What it does and why**: [SPEC.md](SPEC.md)
- **Plan**: [TODO.md](TODO.md)
- **Integration research and mocks**: [docs/EXPLORATION.md](docs/EXPLORATION.md),
  [docs/mocks.html](docs/mocks.html)
- **Known issues**: [GitHub issues](https://github.com/mikelward/steadmo/issues), or ⋮ → Known issues in the app
- **Engineering conventions**: [AGENTS.md](AGENTS.md)
- **Installing a release APK**: [docs/RELEASES.md](docs/RELEASES.md), or ⋮ → Get latest version
  in the app

**Status: MVP.** Reads CamAPS FX's "My diabetes info" notification. Not yet tried on a
real phone.

## Disclaimer

Use Steadmo at your own risk. It comes with no warranty of any kind. It's made for the
developer's family and hasn't been tested for anyone else's circumstances. It isn't a
medical device and isn't approved by any regulator. It can be wrong or out of date, its
alerts can be late or missing, its alerts aim to be actionable and avoid false alarms
(which can be unsafe if its thresholds don't fit you or if you're inexperienced managing
lows and highs), and its trend arrows and predictions are its own estimates. Don't base
treatment decisions on it, and keep CamAPS FX's and your CGM's alarms on.

## Trying it

1. In CamAPS FX's notification settings (Android Settings → Apps → CamAPS FX →
   Notifications), turn on **My diabetes info**.
2. Install Steadmo, open it, and allow notification access and notifications. If Android
   says notification access is "controlled by a restricted setting" (sideloaded apps, Android
   13 and later), open Steadmo's App info, tap ⋮, choose **Allow restricted settings**, then
   try again.
3. Optional: ⋮ → Settings → CamAPS FX shortcuts, then turn on **Steadmo CamAPS FX shortcuts** in Accessibility
   (allow restricted settings first, as above, if it's greyed out).
4. Wait for the next CamAPS FX update. The trend arrow appears after 5 minutes of
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

## Licence

[Apache License 2.0](LICENSE). Like the disclaimer, it comes with no warranty of any kind.
