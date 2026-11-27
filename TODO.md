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
- [ ] Persist alert repeat times across process restarts.

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

- [ ] Full-page home screen widget (Glance): value, arrow, graph, Auto mode, Boost,
      Ease-off, 20/40/45/Other.
- [ ] Launcher shortcuts and Quick Settings tiles for the meal presets.

## Phase 3: more sources

- [ ] Deliveroo, Uber Eats and Just Eat order notifications: suggest a pre-bolus when the
      rider is nearby.
- [ ] Glooko history (boluses, carbs) for the timeline.
- [ ] Deep links into CamAPS FX's meal, Boost and Ease-off screens, if it has them.
- [ ] Optional Accessibility reader for IOB and Boost/Ease-off time left.

## Not in the MVP

- Wear OS: tile, complication and the takeaway prompt on the watch.

## Decisions needing review

- Meal presets are grams of carbs (20, 40, 45), copied to the clipboard.
- Repeat interval for alerts is 30 minutes.
- minSdk 35, as in snoozemo.
