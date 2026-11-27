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
out in the app; Steadmo's notification times out after 15.

### 4.2 Alerts

In priority order, each kind at most every 30 minutes:

1. **Low soon**: falling, and projected below 3.9 within 20 minutes.
2. **Falling fast**: a double down arrow.
3. **High soon**: a single or double up arrow, and projected above 10.0 within 15 minutes.
4. **Rising fast**: a double up arrow.

## 5. Privacy

Glucose data stays on the device. The app has no `INTERNET` permission.

## 6. Modules

`:core` (plain Kotlin JVM): `CamapsParser`, `ReadingLog`, `TrendCalculator`,
`AlertPolicy`. `:app`: the listener, repository, notifications and Compose UI.
