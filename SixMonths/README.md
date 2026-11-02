# SixMonths

SixMonths is a private calisthenics training app for one person. Every exercise, every
training day and every set is created by the user during onboarding and can be edited at
any time. The app ships with an empty plan.

Inspired by syco.

## What the app does

- Seven step onboarding: welcome, training days, plan, reminders, music, future message,
  coach review
- Home with the current day, streak, freezes, session count, weekly ring and next reminder
- Plan for all seven weekdays with the state Training, Optional or Rest
- Day Editor with exercise name, label, category, sets, reps or seconds, notes and form tip
- Workout player with the round robin circuit: Skill, Planche, Training. One set of each
  exercise per round, a stopwatch with a thirty second lead in for timed work, and a rep
  counter for rep work
- Strict streak rules with two freeze tokens, longest streak and total sessions
- Stats with totals, a twelve week heatmap and the recent sessions
- Monster Energy cabinet with 154 catalogue flavours, own photos, name matching and a
  silhouette grid
- Local music playlist with play, pause, previous, next and seek
- Future message that unlocks 182 days after the plan phase started
- Weekly progression review with explicit confirmation per row
- Plan analysis with three graded checks and an Improve Plan action
- JSON backup export and import

There is no login, no account and no backend. Nothing leaves the device.

## Web app

The browser version lives in this folder and starts at `index.html`.

```
index.html
web/theme.css          design tokens, cards, buttons, tab bar
web/core.js            state, domain rules, router, UI helpers
web/lottie.js          Lottie mount and overlay
web/vendor/lottie.min.js  Lottie player
web/data/              can catalogue, Fire.json, Trophy.json, scanner.json
web/img/               logo and thumbnail
web/screens/           onboarding, dashboard, train, day editor, workout, stats,
                       profile, settings, plan, progression, monsters, music, future
```

Run it from the repository root with any static file server, for example:

```
python -m http.server 1234
```

Then open `http://localhost:1234/SixMonths/`. The library card opens the same route.

### Data per user

All data is stored in the browser of the person using the app:

- `localStorage` key `sixmonths.v1` for settings, plan, exercises, sessions, set logs,
  streak and the can metadata
- IndexedDB database `sixmonths` for can photos and music files

Both stores are bound to the origin and the browser profile. Every visitor gets a separate
copy on their own device, and nothing is uploaded. Two people who share the same browser
profile share the same data, because the app has no login by design. A private window or a
different browser profile starts with an empty app.

## APK

The Android build is included in this folder:

```
SixMonths/SixMonths.apk
```

The APK and the web app share the same features, the same design and the same local data
model. The APK stores its data in Room and DataStore instead of localStorage.

## Design

- Dark interface with `#0B0F14` background, `#151A22` cards and `#2F80ED` accent
- Inter with four weights
- Squircle radii, borderless cards with depth
- Floating glass tab bar
- Animated aurora background with three drifting clouds
- Lottie animations for the streak flame, the weekly trophy and the can scanner

## Technical notes

- The APK is Kotlin with Jetpack Compose, Room and DataStore. Build details are in
  `README-ANDROID.md`.
- The web app is plain HTML, CSS and JavaScript without a build step.
- The Lottie player is bundled under `web/vendor`, the animations are loaded from
  `web/data`.

## Browser differences

- Camera capture is replaced by the file picker.
- Notifications are not scheduled. The reminder settings are stored.
- Music plays through the browser from local files, without a background media service.
- Haptics and sound effects are not available in the browser.
- The plan analysis and coach review are deterministic rule sets, simplified compared with
  the Kotlin version.
