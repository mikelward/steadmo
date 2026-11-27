# Steadmo

**Status:** MVP · **Date:** 2026-11-27 · **Platform:** Android 15+
**Application ID:** `app.steadmo`

One place for someone with type 1 diabetes on an insulin pump and CGM to see glucose, trend
and loop state, and to act on them quickly. Built first for Ypsopump + Dexcom G7 looped by
CamAPS FX. The integration research is in `docs/EXPLORATION.md`; mocks are in
`docs/mocks.html`.

## 1. Goals and non-goals

### Goals

1. The current value, a standard trend arrow and the reading's age, at a glance: app,
   status bar and (later) a full-page widget.
2. Alerts that say what is happening and when it will matter: rate, projection, action.
3. One tap into CamAPS FX for a meal bolus, Boost or Ease-off.

### Non-goals

- Dosing. Steadmo never delivers insulin or confirms anything in CamAPS FX.
- Replacing CamAPS FX or Dexcom alarms.
- Cloud sync or accounts.
- Wear OS, for the MVP.

### Disclaimer

On first open, and again whenever its wording changes, Steadmo shows a disclaimer: use at
your own risk, no warranty, made for the developer's family and not tested for anyone
else's circumstances, not a medical device and not approved by any regulator, no treatment
decisions from it, and it never doses. Continue stays disabled until the user ticks "I have
read this and accept the risk"; Leave closes the app. The home screen links back to it.

### Appearance

The app follows the system light or dark setting, with Material You colors. Range colors
(low, in range, high) are the same in both.

## 2. Key decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **CamAPS FX is the only source for the MVP** | The Dexcom G7 app can't run alongside CamAPS FX on the same phone, and CamAPS FX already receives the G7's readings. Other sources wait (`TODO.md`). |
| D2 | **Read, never dose** | No public CamAPS FX or Ypsopump API exists; automating the confirm tap would bypass a medical-device safety step. Actions copy the amount and open CamAPS FX. |
| D3 | **Read CamAPS FX's "My diabetes info" notification** with a `NotificationListenerService` | It shows the value, unit, Auto mode state and a trend arrow, updates in real time, and needs no network. It is a custom layout, so its views are inflated and their text read in layout order. |
| D4 | **Compute the trend in Steadmo** | CamAPS FX draws its arrow as an image, with no text to read. Steadmo fits its own slope to the readings, so the arrow works the same everywhere Steadmo shows it. The CamAPS image is logged so the two can be compared (`TODO.md`). |
| D5 | **Logic in a plain-Kotlin `:core`** | Parser, trend and alerts are tested on the JVM without an SDK. |

## 3. Reading CamAPS FX

- The listener accepts packages starting `com.camdiab.fx_alert` (mmol/L, mg/dL and HX builds).
- Text comes from the notification's inflated content view, in layout order; the
  extras are a fallback. On a Pixel the views read
  `CamAPS FX | Auto mode | On | 11.2 | mmol/L`.
- The value is the first text that is a number with one decimal (mmol/L) or an integer
  next to `mg/dL`, from 1 to 35 mmol/L. A leading `<` or `>` marks a bound.
- A post within 4 minutes of the last reading is the same reading: dropped if unchanged,
  replacing it if changed. A day of readings is kept in `files/readings.csv`.
- The app shows what it read ("Show CamAPS FX notification") so parsing can be checked on
  a new phone or CamAPS FX version.

## 4. Trend

### 4.1 Arrows

The least-squares slope over the 20 minutes before the latest reading, needing two or
more readings at least 5 minutes apart. Thresholds are the standard CGM ones:

| Arrow | Rate | mmol/L in 30 min |
|---|---|---|
| Flat → | under 1 mg/dL/min (0.056 mmol/L/min) | under 1.7 |
| Slight ↗ ↘ | 1–2 mg/dL/min | 1.7–3.3 |
| Single ↑ ↓ | 2–3 mg/dL/min | 3.3–5.0 |
| Double ⇈ ⇊ | 3 mg/dL/min (0.17 mmol/L/min) or more | over 5.0 |

Arrows are vector drawables, so the same image is the status bar icon, the
notification's large icon and the in-app arrow. A reading older than 10 minutes greys
out in the app, and so does an Auto mode report older than 10 minutes, which also says how
old it is. Steadmo's notification disappears 15 minutes after its reading.

### 4.2 Alerts

Only for a reading under 2 minutes old: a replayed reading is history, and its
projection would be about a time already past. Each alert disappears 15 minutes after its
reading, or sooner when a newer reading no longer calls for it. In priority order, each kind at most every 30 minutes:

1. **Low soon**: falling, and projected below 3.9 within 20 minutes.
2. **Falling fast**: a double down arrow.
3. **High soon**: a single or double up arrow, and projected above 10.0 within 15 minutes.
4. **Rising fast**: a double up arrow.

### 4.3 Live Update

On Android 16 and later, Steadmo's status notification becomes a Live Update while the
latest reading is out of range (below 3.9 or above 10.0) or a "Low soon" alert is
showing. The status bar chip shows the value next to the arrow icon. The promoted card
reads "Low: 3.6 mmol/L", says how long it has been out of range, and shows a progress bar
on a 2–14 scale (red, green, amber) with the value marked. The notification is demoted
once a reading is back in range and no low is on the way. It needs
`POST_PROMOTED_NOTIFICATIONS`; the user can turn it off in Settings. On Android 15 the
same notification posts unpromoted.

### 4.5 Home screen widget

A resizable widget, from 2x1 to a full page (4x5 by default):

- At every size: the value, coloured by range, the trend arrow, the reading's time and
  rate, and Auto mode. A reading over 10 minutes old greys out and says so.
- From about 200 dp tall: the 3-hour graph, drawn to a bitmap.
- From about 320 dp tall: Boost, Ease-off, 20 g, 40 g, 45 g and Other. They open CamAPS
  FX through a UI-less activity, which copies the meal amount first, as the app's buttons
  do.
- Before the disclaimer is accepted, it only offers to open Steadmo.

It redraws on each new reading, and once more 10 minutes later so a stale value greys out
without waiting for the system's 30-minute refresh.

### 4.4 CamAPS FX's notification

Steadmo reads CamAPS FX's "My diabetes info" notification, so the user keeps it on but can
minimize it (Silent, then Minimize) to avoid seeing glucose twice. Steadmo knows the
channel's importance from the listener's ranking and shows a "Minimize CamAPS FX's
notification" card, with a button to that channel's settings, until it is minimized.

## 5. Privacy

Glucose data stays on the device. The app has no `INTERNET` permission. The only way
anything leaves is a bug report the user sends themselves (§6).

## 6. Debug log and bug reports

Steadmo uses the shared `mikelward/androidlog` log, as the sibling apps do:

- `SteadmoLog` records each step: the listener connecting, each CamAPS FX post and what
  was parsed from it, readings added or dropped, the trend, alerts shown, held back or
  cleared, the status notification and Live Update, and the CamAPS FX buttons. Lines go to
  logcat and to a file in the app's cache, which keeps earlier runs. A crash is recorded
  before the process dies, and why earlier processes ended (ANR, crash, update) is pinned
  at the top of the next run's log.
- **Report a bug** (bottom of the home screen) builds a report: app version, device and
  Android version, access and permission state, CamAPS FX's package and channel, the
  latest reading and trend, the last CamAPS FX notification as read, this run's log and
  earlier runs. It attaches a screenshot of the screen, copies the text to the clipboard,
  and opens the share sheet, so the user picks where it goes. If no app takes it, a toast
  says it was copied.
- The report includes glucose values; that is the point of it, and it goes only where the
  user sends it.

## 6. Modules

`:core` (plain Kotlin JVM): `CamapsParser`, `ReadingLog`, `TrendCalculator`,
`AlertPolicy`, `SteadmoLog`. `:app`: the listener, repository, notifications, bug report
and Compose UI.
