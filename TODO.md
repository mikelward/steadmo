# TODO

## Phase 1: MVP (CamAPS FX only)

- [x] Repo scaffold matching the sibling Android apps (AGENTS.md, SPEC.md, hook, CI).
- [x] Read CamAPS FX's "My diabetes info" notification; parse value, unit, Auto mode.
- [x] Trend arrows from the readings, standard CGM thresholds; arrow as status bar icon.
- [x] Fast rise and fall, low-soon and high-soon alerts.
- [x] Home screen: value, arrow, age, Auto mode, 3-hour graph, Boost, Ease-off and meal
      buttons that open CamAPS FX.
- [x] Unit tests for `:core` and `:app` (Robolectric), Roborazzi screenshots in light and
      dark, and a 95% line-coverage gate (Kover), all in CI.
- [x] Follow the system light or dark setting.
- [ ] Try on a real phone: check the parsed text and the arrow against CamAPS FX's own.
- [ ] Map CamAPS FX's arrow image (logged as a pixel hash) to `TrendArrow`, and show
      both if they disagree.
- [ ] Read Boost and Ease-off state, if the notification shows it.
- [x] Persist alert repeat times across process restarts.

## Phase 2: glanceable surfaces

- [x] Live Update (Android 16) while out of range: status bar chip with value and arrow,
      promoted card with a ProgressStyle bar on the 2–14 scale (red, green, amber), demoted
      once a reading is back in range.
- [ ] Play policy check for `POST_PROMOTED_NOTIFICATIONS` before a Play release
      (maintainer's call).
- [x] Setup step to minimize CamAPS FX's "My diabetes info" channel (Silent + Minimize,
      not off: a channel that is off posts nothing to read), opening its channel settings
      with the channel id Steadmo has seen.
- [ ] "Treated" action on low alerts (label to try first; "Took 15 g" is the alternative): records a hypo treatment in Steadmo only (not
      CamAPS FX), keeps it on the timeline, holds off rise alerts it explains, and reminds
      you to recheck after 15 minutes.

- [x] Full-page home screen widget (Glance): value, arrow, graph, Auto mode, Boost,
      Ease-off, 20/40/45/Other.
- [x] Display units setting: mmol/L or mg/dL (⋮ → Units).
- [ ] Bolus progress: if CamAPS FX shows a notification while a bolus is delivering,
      mirror its progress as a Live Update (check on a real phone first).
- [x] Launcher shortcuts: 20 g, 40 g, 45 g and Boost on a long press of Steadmo's icon.
- [ ] Quick Settings tiles for the meal presets.
- [x] CamAPS FX shortcuts: an accessibility service, limited to CamAPS FX, that takes the
      meal, Boost and Ease-off buttons (app and widget) to their CamAPS FX screens and types
      a meal's carbs, never delivering (SPEC.md §4.6). Mapped from v1.4 screenshots.
- [ ] Check the shortcuts on a real phone against the logged screens (tab class names, the
      Carbs cell, the dialog field), and what Boost does when it's already on.
- [ ] Play policy check for the AccessibilityService API (declaration form, prominent
      disclosure) before a Play release (maintainer's call).

## Diagnostics

- [x] Debug log on the shared `mikelward/androidlog`: logcat, a persisted file with earlier
      runs and crashes, and process exit reasons.
- [x] "Report a bug": app state, last CamAPS FX notification, log and screenshot, through
      the share sheet (clipboard fallback).
- [ ] Add androidlog to the weekly Gradle update batch, as the sibling apps do.

## Phase 2b: Steadmo as the alerting app (to consider)

Goal: alerts come from Steadmo's own logic, without double alarms, and never weaker than
CamAPS FX's. Each step needs the maintainer's sign-off: these are safety decisions.

- [ ] Decide which CamAPS FX high and low alerts can be silenced (its own settings, or
      its channels set to Silent) and which must stay on. An urgent low must always reach
      the user, from CamAPS FX or Steadmo, even if Steadmo is killed or loses notification
      access.
- [x] Signal loss: warn when no glucose has come from CamAPS FX for 15 minutes (sensor,
      Bluetooth, CamAPS FX, or Steadmo's notification access), repeating every 30 minutes
      for 2 hours (SPEC.md §4.7).
- [x] Say so in the app when notifications are off or the Alerts channel is set below
      Default, with a button to its settings.
- [ ] Pump connection: find what CamAPS FX's notifications say when it loses the pump
      (bug report while disconnected), then warn, and say so on the Boost and meal buttons.
- [x] Urgent low alarm below 3.0 mmol/L: an alarm-sound channel that Do Not Disturb lets
      through wherever it lets alarms through, repeating until OK (SPEC.md §4.8).
- [ ] Check on a real phone that the urgent low alarm sounds in Do Not Disturb; if not,
      ask for notification policy access so its channel can bypass it.
- [ ] Full-screen urgent low after it's been ignored (needs `USE_FULL_SCREEN_INTENT`, a
      Play policy question for the maintainer).
- [ ] Tests for each alert path, including DND on and off, before any CamAPS FX alert is
      silenced.

## Phase 3: more sources

- [ ] Deliveroo, Uber Eats and Just Eat order notifications: suggest a pre-bolus when the
      rider is nearby.
- [ ] Glooko history (boluses, carbs) for the timeline.
- [ ] Deep links into CamAPS FX's Boost and Ease-off screens, if it ever has them (v1.4
      exposes only its launcher).
- [ ] Optional Accessibility reader for IOB and Boost/Ease-off time left.

## Not in the MVP

- Wear OS: tile, complication and the takeaway prompt on the watch.

## Decisions needing review

- Meal presets are grams of carbs (20, 40, 45), copied to the clipboard.
- CamAPS FX shortcuts give up after 3 taps or 20 seconds, and treat any screen with a field
  but no carbs field as a form not to touch.
- With three unlabeled tabs, CamAPS FX shortcuts take the first as home and the second as
  meal, as on v1.4.
- Shortcuts don't repeat within a minute (the maintainer's ask): any meal blocks the next
  meal, and Boost and Ease-off each block themselves.
- Meal shortcuts tap the Carbohydrates dialog's Confirm only up to 60 g (the maintainer's
  limit), and only once the field shows exactly the grams typed.
- Repeat interval for alerts is 30 minutes.
- minSdk 35, as in snoozemo.
- Signal loss warns after 15 minutes without a reading, repeats every 30 minutes, and stops
  after 2 hours (CamAPS FX is likely off on purpose by then; the app still says so). The
  check is an inexact alarm allowed while idle, so it can land a few minutes late.
- Urgent low is below 3.0 mmol/L (54 mg/dL) or under the sensor's range. It sounds on every
  new reading until OK; swiping it away counts as OK. OK quiets it for 15 minutes.
- Launcher shortcuts are 20 g, 40 g, 45 g and Boost: launchers show about four, so Ease-off
  and Other stay on the widget and in the app.
