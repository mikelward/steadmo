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

On first open, and again whenever its wording changes (`Disclaimer.VERSION`, now 2),
Steadmo shows a disclaimer: use at your own risk, no warranty, made for the developer's
family and not tested for anyone else's circumstances, not a medical device and not
approved by any regulator; it can be wrong or out of date, its alerts can be late or
missing, and its trends are its own estimates; no treatment decisions from it, and it never
doses. Continue stays disabled until the user ticks "I have
read this and accept the risk"; Leave closes the app. The home screen links back to it.

### Units

Readings are stored and computed in mmol/L. ⋮ → Settings → Units shows them in mmol/L (one
decimal, the default) or mg/dL (whole numbers) everywhere: the app, graph labels, widget,
status notification, Live Update chip and alert text. Rates are per minute in the same
unit (0.01 mmol/L, 0.1 mg/dL). The choice is kept on the device and redraws the status
notification and widgets at once.

### About

⋮ → About shows the version, that Steadmo is a companion display and not a medical device,
and its licence: Steadmo and every library it uses are Apache 2.0, and the licence text is
bundled (`res/raw/license.txt`, a copy of `LICENSE`). A Source code button opens the
GitHub repository.

### Appearance

The app follows the system light or dark setting, with Material You colors. Range colors
(low, in range, high) are the same in both.

## 2. Key decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **CamAPS FX is the only source for the MVP** | The Dexcom G7 app can't run alongside CamAPS FX on the same phone, and CamAPS FX already receives the G7's readings. Other sources wait (`TODO.md`). |
| D2 | **Read, never dose** | No public CamAPS FX or Ypsopump API exists; automating the confirm tap would bypass a medical-device safety step. Actions copy the amount and open CamAPS FX. CamAPS FX shortcuts (§4.6), which the user turns on, also tap through to the meal entry and enter the carbs, or open Boost or Ease-off's dialog: CamAPS FX exposes no screen but its launcher (checked on v1.4), and the user asked for it. The only confirm they press is the Carbohydrates dialog's, which enters up to 60 g into the bolus calculator; Next and delivering stay manual. |
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

### 4.6 CamAPS FX shortcuts

CamAPS FX opens only at its launch screen, so Steadmo's buttons can't open its meal,
Boost or Ease-off screens directly. With CamAPS FX shortcuts on, an accessibility service
does the navigation, from the app's buttons and the widget's:

- Off until the user turns on "Steadmo CamAPS FX shortcuts" in Android's Accessibility
  settings. Until then, or until they tap Not now, a home screen card suggests it. ⋮ → Settings → CamAPS FX shortcuts explains what they do and their risks first, and
  links there.
- The service sees CamAPS FX's packages only (its config's `packageNames`), and can't
  perform gestures. It acts only within 20 seconds of a Steadmo button, only while CamAPS
  FX is the app in front (it never reads another app's screen), and only on views that are
  on screen.
- `CamapsTask` in `:core` decides each step from the screen's views. In CamAPS FX v1.4
  (from the maintainer's phone):
  - Meal: the meal tab (the second of three unlabeled icon tabs), the Carbs "---" cell,
    then it types the grams into the Carbohydrates dialog. Up to 60 g, on a dialog with
    one field, carbs on it and nothing about insulin anywhere (ids included), once the
    field shows exactly those grams, it taps that dialog's only Confirm, which enters them into
    the bolus calculator; over 60 g it stops and the user confirms. The user always
    checks the bolus and taps Next in CamAPS FX.
  - Other: the same way to the Carbohydrates dialog, then it stops with the field open
    for the user to type the grams. It types nothing, so no repeat guard applies.
  - Boost or Ease-off: the home tab if needed, then the button reading exactly "Boost" or
    "Ease-off", which opens CamAPS FX's own dialog for the user to set and confirm.
- A carbs field is one marked as carbs by its hint or id, by the label beside it when it
  is that row's only field, or as the only field on a dialog about carbs. A field with
  anything about insulin, units, bolus or dose beside it, or on its dialog, is never one.
- It never taps a button that reads like confirming, delivering, saving, Auto mode or a
  setting, nor a slider or switch, nor anything on a screen with a field (forms are
  where confirm buttons are), except the carbs dialog's Confirm above, and presses
  nothing after its last step. Boost and Ease-off
  are tapped only on an exact match, never "Stop Boost" or similar.
- No repeats: within a minute of a meal shortcut (any grams), or of the same Boost or
  Ease-off shortcut, the button only opens CamAPS FX and says why, so a double tap or a
  second meal can't enter carbs twice. The time is kept across restarts. It is cleared
  only when a shortcut fails before typing anything; once grams are typed, a later
  failure keeps it, and says the grams were typed but not confirmed.
- Unsure means stop: two candidate fields or buttons, a form with no carbs field, or not
  there within 3 taps or 20 seconds. It says so in a toast; a meal's grams are always
  copied to paste instead.
- Each screen it acts on is logged as its structure, with every digit masked, so its
  matching can be tuned from a bug report without recording glucose or doses.

### 4.7 Signal loss

Separate from glucose out of range: when no reading has come from CamAPS FX for 15
minutes (three missed readings), Steadmo posts "No glucose for 15 min" on the alerts
channel. The cause can be the sensor's signal, Bluetooth, CamAPS FX itself, or Steadmo's
notification access; when access is off, the warning says so. It repeats every 30 minutes
for 2 hours, and the next reading clears it. Each reading sets an inexact alarm, allowed
while idle, for 15 minutes later; the app re-arms it on start. It adds to CamAPS FX's
own signal loss alarm and never replaces it.

Alerts go on one channel, "Alerts". When the user has set it below Default (no sound) or
off, the home screen says so with a button to the channel's settings, as it does when
notifications are off.

### 4.8 Urgent low

Below 3.0 mmol/L (54 mg/dL), or under the sensor's range, each fresh reading sounds
Steadmo's urgent low alarm on its own "Urgent low" channel: an alarm sound with alarm
usage and the alarm category, so Do Not Disturb lets it through wherever it lets alarms
through, and insistent, so it repeats until acknowledged. OK (or swiping it away) quiets it
for 15 minutes; a reading at or above 3.0 clears it and the acknowledgement. The channel
asks to bypass Do Not Disturb, which Android honours only with notification policy
access. If it sounds for 5 minutes without OK or Treated, the next
reading makes it full screen, over the lock screen and turning the screen on, with
Treated and OK. That needs `USE_FULL_SCREEN_INTENT`; when Android hasn't granted it, the
home screen offers its settings page. It adds to CamAPS FX's and the sensor's own urgent
low alarms and never replaces them.

### 4.9 Launcher shortcuts

A long press on Steadmo's icon offers 20 g, 40 g, 45 g and Boost. They run through the
widget's UI-less activity, so they behave exactly like the widget's buttons: copy the
grams, open CamAPS FX (straight to the screen with CamAPS FX shortcuts on), and open
Steadmo instead until the disclaimer is accepted. They are published as dynamic shortcuts
on start, so they follow the debug build's package.

### 4.10 Treated

Low alerts (low soon, falling fast) and the urgent low alarm offer "Treated". It records a
hypo treatment in Steadmo only (CamAPS FX doesn't hear of it), kept for a day:
- a low-coloured mark on the app's and widget's graphs;
- the low alerts it answers are cleared, and the urgent low alarm quieted as OK does;
- rise alerts (rising fast, high soon) hold off for 45 minutes, since the treatment
  explains the rise;
- 15 minutes later, "Recheck your glucose" with the current value.

### 4.11 Quick Settings tiles

- **Glucose**: the value and unit as its label, the trend and age ("Rising · 3 min ago") as
  its subtitle, and the trend arrow as its icon. It redraws each time the shade opens, so
  the age is current. Over 10 minutes old, or before the first reading, it shows no value
  and is unavailable (greyed). Tapping opens Steadmo.
- **Meal**: like the Other button, opens CamAPS FX's carbs box (with CamAPS FX shortcuts on)
  through the widget's UI-less activity, so the disclaimer gate applies.

### 4.12 Delivery pre-bolus prompts

The notification listener also sees Deliveroo, Uber Eats, Just Eat (and Menulog) and
DoorDash. When one says the rider is nearby (nearly there, around the corner, approaching,
arriving or minutes away under 5 minutes) and not that the food has arrived, Steadmo posts "Food
nearly here" on a Reminders channel, with Bolus, which opens CamAPS FX's carbs box like
Other. "On its way", "picked up" or "out for delivery" don't prompt: the maintainer wants
the prompt when the rider is close, not when the order sets off. At most one per app per
hour, only for posts under 15 minutes old (not old ones replayed on reconnect); it expires
after 45 minutes. It is for a pre-bolus only: Steadmo doesn't try to detect unannounced
meals from a rise. What the delivery app says is never stored or logged; the last one is
kept in memory for a bug report the user sends.

### 4.15 Navigation

⋮ opens About (a dialog) and the Settings and Disclaimer pages. Settings lists Units, Meal
reminder and CamAPS FX shortcuts, each with its current value, and each opens its dialog.
Back closes a dialog, and from a page returns home; only home's back leaves the app. On first
run, back on the disclaimer leaves, as Leave does. The open page and dialog survive rotation.
Dialog content scrolls when it doesn't fit.

### 4.13 Meal reminder

Each day at a set time (on by default at 18:00; ⋮ → Settings → Meal reminder turns it off or changes
the time), "Time to bolus for dinner?" on the Reminders channel, with Bolus (CamAPS FX's
carbs box, like Other), In 30 min and In 60 min. Its alarms go off while the phone is idle.
With "Alarms & reminders" (`SCHEDULE_EXACT_ALARM`, which the user grants) they're on time;
without it they're inexact, and Android may deliver them up to an hour late. While it's not
allowed, the Meal reminder dialog says so, with Allow. Granting it re-arms the reminders, a
pending snooze and a pending recheck.
Revoking it stops Steadmo and cancels its exact alarms, so app start restores the daily and
calendar reminders, a pending snooze (if under 2 hours overdue) and a pending recheck. The signal-loss check and the recheck after Treated use exact alarms too when
allowed. Steadmo doesn't use `USE_EXACT_ALARM`, which Play keeps for alarm clock and calendar
apps. App start re-arms the daily alarm,
each day's reminder sets the next, and a time zone or clock change sets it again, so it
stays at 18:00 local. Turning it off cancels any snooze and clears a shown reminder.

### 4.14 Calendar meals

Off until turned on in ⋮ → Settings → Meal reminder, which asks for calendar access. Steadmo reads the
next day's events, on the device, for timed events named for a meal (breakfast, brunch,
lunch, dinner, supper, meal) or a restaurant booking (reservation, restaurant, "table for
N"), which Gmail adds from booking emails. 15 minutes before the soonest, it posts "Dinner
with Sam at 19:00. Bolus before you eat?" on the Reminders channel, with Bolus, In 30 min
and In 60 min. It rescans every 3 hours (WorkManager), on start and after each reminder.
A reminder that's due but hasn't gone off (a delayed alarm, or an event added late) goes
off at once, until the meal starts; each event reminds once. A snooze keeps the event's
name. Only the event's title, start and ID are kept, in the pending alarm and in the list of
events already reminded. It works whether or not
the daily reminder is on.

### 4.4 CamAPS FX's notification

Steadmo reads CamAPS FX's "My diabetes info" notification, so the user keeps it on but can
minimize it (Silent, then Minimize) to avoid seeing glucose twice. Steadmo knows the
channel's importance from the listener's ranking and shows a "Minimize CamAPS FX's
notification" card, with a button to that channel's settings, until it is minimized.

## 5. Privacy

Glucose data stays on the device. Delivery apps' notifications are read on the device and
not stored; so is the calendar, when calendar meals are on. The app has no `INTERNET` permission. CamAPS FX shortcuts
reads CamAPS FX's screens only while filling a meal, and logs them with digits masked. The only way
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
