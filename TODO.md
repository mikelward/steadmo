# TODO

## Phase 1: MVP (CamAPS FX only)

- [x] Repo scaffold matching the sibling Android apps (AGENTS.md, SPEC.md, hook, CI).
- [x] Read CamAPS FX's "My diabetes info" notification; parse value, unit, Auto mode.
- [x] Trend arrows from the readings, standard CGM thresholds; arrow as status bar icon.
- [x] Fast rise and fall, low-soon and high-soon alerts.
- [x] Home screen: value, arrow, age, Auto mode, 3-hour graph, Boost, Ease-off and meal
      buttons that open CamAPS FX.
- [ ] Try on a real phone: check the parsed text and the arrow against CamAPS FX's own.
- [ ] Map CamAPS FX's arrow image (logged as a pixel hash) to `TrendArrow`, and show
      both if they disagree.
- [ ] Read Boost and Ease-off state, if the notification shows it.
- [ ] Persist alert repeat times across process restarts.

## Phase 2: glanceable surfaces

- [ ] Full-page home screen widget (Glance): value, arrow, graph, Auto mode, Boost,
      Ease-off, 20/40/45/Other.
- [ ] Wear OS tile and complication.
- [ ] Launcher shortcuts and Quick Settings tiles for the meal presets.
- [ ] CI screenshot tests (Roborazzi), as in the sibling apps.

## Phase 3: more sources

- [ ] Deliveroo, Uber Eats and Just Eat order notifications: suggest a pre-bolus when the
      rider is nearby, mirrored to the watch.
- [ ] Glooko history (boluses, carbs) for the timeline.
- [ ] Deep links into CamAPS FX's meal, Boost and Ease-off screens, if it has them.
- [ ] Optional Accessibility reader for IOB and Boost/Ease-off time left.

## Decisions needing review

- Meal presets are grams of carbs (20, 40, 45), copied to the clipboard.
- Repeat interval for alerts is 30 minutes.
- minSdk 35, as in snoozemo.
