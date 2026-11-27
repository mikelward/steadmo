# Bolusmo: exploration

An Android app that gives someone with type 1 diabetes, using an insulin pump and CGM, one place to see and act on their data.

Target setup: **Ypsopump** + **Dexcom G7**, looped by **CamAPS FX**
(`com.camdiab.fx_alert.mmoll`), with history in **Glooko**
(`com.glooko.logbook`). Units: mmol/L.

Interactive mocks: [`mocks.html`](mocks.html).

## Main constraint

There is no public API for CamAPS FX or the Ypsopump. The Ypsopump uses heavy
encryption, and AAPS lists it as not loopable. CamAPS FX holds the pump's
Bluetooth connection. So:

* Bolusmo **reads** data from notifications, xDrip+, cloud APIs and
  (optionally) the CamAPS screen through an Accessibility service.
* Bolusmo **never delivers insulin**. Bolus, Boost and Ease-off actions open
  CamAPS FX on the right screen with the amount ready. You confirm there.
  Bolusmo never automates the confirm tap.

## Integration routes

| Source | Route | Gives | Latency | Verdict |
|---|---|---|---|---|
| Dexcom G7 | `NotificationListenerService` on the G7 app's ongoing notification | BG, trend | real time | Main route; fragile (text format) |
| Dexcom G7 | xDrip+ (companion mode or native) → local broadcast | BG, trend | real time | Good; xDrip handles the parsing |
| Dexcom G7 | Dexcom Share (unofficial cloud API) | BG, trend | ~5–10 min | Fallback and for followers |
| Dexcom G7 | Official Dexcom developer API | EGVs, events | hours outside US | Reports only |
| Dexcom G7 | Health Connect | BG records | a few hours | Reports only |
| CamAPS FX | Ongoing notification | BG, trend, maybe mode | real time | Main live loop signal |
| CamAPS FX | Accessibility service reading the screen | Auto mode, Boost/Ease-off and time left, IOB, last bolus | when the screen is shown | Powerful, brittle |
| CamAPS FX | Launch intent / deep links | Open app (and possibly a specific screen) | — | Launch works; deep links need checking |
| CamAPS FX | Bolus or Boost API | — | — | Does not exist |
| Ypsopump | Direct BLE | — | — | Not possible |
| Glooko | Unofficial web API / CSV export | Boluses, carbs, basal, CGM history | hours | History only |
| Deliveroo, Uber Eats, Just Eat | Notification listener | Order picked up / rider nearby | real time | Low risk if it misses |

## Architecture

* Kotlin, Jetpack Compose app, Jetpack Glance widget, Wear OS module.
* A **foreground service** owns a Room database of readings, events and loop state.
* **Source adapters** behind small interfaces (`GlucoseSource`,
  `LoopStateSource`, `HistorySource`, `MealSignalSource`), each with its own
  freshness and health tracking.
* **Trend and alert engine**: least-squares slope over 15 min; fast rise from
  +0.1 mmol/L/min and fast fall from −0.1 (configurable); projected time to
  3.9 and 10.0; context (IOB, last bolus, carbs logged) and one clear action
  per alert.
* **Actions** go through one `CamapsLauncher` that copies the amount to the
  clipboard and opens CamAPS FX (deep link if available, otherwise the launch
  intent). Accessibility pre-fill is optional, and confirm stays manual.
* Notifications bridge to the watch automatically. A Wear tile and complication
  read from the Data Layer API.

## Features

1. **Full-page home screen widget**: BG, trend, delta, data age, 3 h graph,
   Auto mode and Boost/Ease-off state, Boost and Ease-off buttons, big Bolus
   button, 20/40/45/Other carb shortcuts. The service pushes updates on each
   reading. The graph is drawn as a bitmap.
2. **Rate-of-change alerts** that state the rate, a projection and what to do.
3. **Takeaway pre-bolus prompt** when the rider is nearby, mirrored to the
   watch, with a suggested amount from past takeaway meals.
4. **Overview**: time in range, merged timeline, source health.
5. **Launcher shortcuts and Quick Settings tiles** for the carb presets.

## Safety

* A companion display only. CamAPS FX and Dexcom alarms stay on and stay
  authoritative.
* Show data age everywhere, and grey out readings older than 10 minutes.
* Parsers are versioned and tested against captured notification samples.

## Check on a real device

* What the CamAPS FX and G7 notifications contain.
* Whether CamAPS FX has deep links to meal, Boost or Ease-off screens.
* Whether CamAPS reads the G7 directly or through the Dexcom app on this setup.
* The current wording of Deliveroo notifications.

## First milestone

Read-only: G7 and CamAPS notification adapters, Room store, full-page widget
with graph, and rate-of-change alerts. Buttons open CamAPS FX.

## Sources

* [xDrip+ companion app mode](https://navid200.github.io/xDrip/docs/Follow/CompanionApp.html)
* [AAPS: future (possible) pump drivers (Ypsomed)](https://androidaps.readthedocs.io/en/latest/Getting-Started/Future-possible-Pump-Drivers.html)
* [Dexcom: Health Connect](https://www.dexcom.com/en-AU/how-do-i-navigate-to-health-connect?ipc=US)
* [ADCES: Dexcom G7 app](https://adces.org/education/danatech/apps-dtx/find-apps-tools/product-detail/dexcom-g7-app)
* [mylife: Dexcom G7 with myLoop](https://www.mylife-diabetescare.com/en/dexcom-g7-rollout-pat)
