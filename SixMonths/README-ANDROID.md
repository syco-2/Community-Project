# Six Months

A private calisthenics training app for exactly one user. All data lives on the device in
Room + DataStore; nothing is uploaded and there is no backend.

**Version 1.4.** The app ships with an **empty plan**. Every exercise, every training day
and every set is created by you during onboarding and can be edited at any time. The
Monster Energy cabinet fills up one hand-named can photo at a time.

## What this build contains

- Seven-step onboarding, Day Editor with smart autocomplete, persistence, resume after kill
- Home dashboard, **round-robin circuit player**, set logging, strict streak rules,
  one-reminder-a-day notifications
- Weekly progression review with explicit confirmation, deterministic Coach Review
- **Edit plan** as a real seven-day overview (no more onboarding loop) plus a deep
  **Plan analysis**: skill freshness, joint frequency and daily volume graded out of 10,
  a staged report and a one-tap **Improve Plan** that rewrites the week in Room
- Local music tab (Media3 + SAF) that keeps playing in the background, stats, JSON
  backup/restore, future message, six-month cycle
- **Monster Energy cabinet**: 300+ real flavours, manual CameraX photo + typed flavour name,
  scanner overlay while the JPEG is written, square crop, 2D cabinet with silhouettes and
  drop/burst animation — no ML, no OCR
- **Design system**: supplied logo with the white background removed, branded splash,
  floating glass pill tab bar, borderless cards with depth, squircle radii, Inter

## Toolchain (pinned)

| Component | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 8.7 (via included wrapper) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.20 |
| Compose BOM | 2024.09.02 |
| Compose Compiler | `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.0 plugin) |
| KSP | 2.0.20-1.0.25 |
| Room | 2.6.1 |
| DataStore | 1.1.1 |
| WorkManager | 2.9.1 |
| Media3 | 1.4.1 |
| CameraX | 1.3.4 |
| AndroidX ExifInterface | 1.3.7 |
| kotlinx-serialization-json | 1.7.1 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |
| applicationId | `com.sixmonths.app` |
| versionName / versionCode | `1.4` / `6` |

Libraries the spec did not pin live in `gradle/libs.versions.toml`: `androidx.core:core-ktx`
1.13.1, `activity-compose` 1.9.2, `lifecycle` 2.8.4, `navigation-compose` 2.8.0.

## Build and install

Requirements: JDK 17, Android SDK platform 34, `ANDROID_HOME` (or `local.properties` with
`sdk.dir`) and `adb` for installation.

```bash
./gradlew assembleDebug
./build_and_install.sh          # builds, then adb install -r
./build_and_install.sh <serial> # same for a specific device
```

`adb install -r` updates the app in place and does **not** clear app data.

## Keeping progress across updates

- The package name stays `com.sixmonths.app` and the debug signing key is unchanged, so a new
  build installs over the old one instead of creating a second app.
- Room is now **version 6**, `exportSchema = true`, schemas committed under `app/schemas/`:
  - `MIGRATION_1_2` creates the `monsters` table.
  - `MIGRATION_2_3` adds `monsters.speciesId` (default `''`) and its index.
  - `MIGRATION_3_4` creates `monster_can_species`, the Room table with the 40 flavours.
  - `MIGRATION_4_5` deliberately empties `monsters` once: the retired automatic-scanner
    captures are stale. Plan, sessions, set logs and streak are not touched.
  - `MIGRATION_5_6` adds `set_logs.planExerciseId` (default `''`), so duplicating an
    exercise no longer makes two plan rows share one log slot.
  - Otherwise migrations stay additive, and destructive fallback is not enabled — an
    unhandled future upgrade fails loudly instead of wiping data.
- Existing DataStore keys are unchanged; new keys read their defaults after the update.
- JSON backups are `backupSchemaVersion 4` (adds the exact plan-row id to set logs).
  Version 1–3 files still import; missing fields keep their defaults.
- Re-running onboarding or editing the plan never resets the streak. Only the explicit
  actions after the future-message reveal start a new cycle.

## Project layout

```
app/src/main/java/com/sixmonths/app/
  SixMonthsApp.kt              Application, service locator, SFX init
  MainActivity.kt              Single activity, start route, theme and SFX wiring
  di/ServiceLocator.kt         Manual DI (no Hilt)
  core/                        Categories, units, day states, dates, haptics
  data/entity/                 Room entities incl. Monster and MonsterCanSpecies
  data/dao/                    Room DAOs
  data/repository/             Plan, workout, monster, catalog, backup repositories
  data/prefs/                  DataStore Settings + SettingsRepository
  domain/                      Streak, coach review, plan analyzer, workout circuit, cycle
  domain/monsters/             Can flavour catalog + deterministic typed-name matcher
  notifications/               Reminder worker + scheduler
  playback/                    Media3 service, music controller, SoundFx
  ui/theme/                    Dark theme, squircle shapes, Inter typography
  ui/components/               Cards, shimmer, number picker, Lottie FX, aurora, logo
  ui/icons/                    Custom energy-can and headphones navigation vectors
  ui/navigation/               Routes, floating glass tab bar, shared-element NavHost
  ui/screens/                  onboarding, home, train, cabinet, plan editor/analysis,
                               profile, day editor, workout, stats, review, settings,
                               music, future
tools/                         Brand asset generator, catalog helpers
```

## The five tabs

1. **Home** — today, streak, weekly ring, reminders, shortcuts.
2. **Train** — today's session, plan overview, weekly progression review.
3. **Monster** — the 2D energy-can display cabinet, with a custom can icon (single-line
   label, long-press any collected can to remove it).
4. **Music** — local SAF playlist with a headphones icon; playback survives a locked phone.
5. **Profile** — stats, Coach Review, future message, settings and backup.

## Workout player: round-robin circuit

- Exercises are grouped into three blocks in this order: **Skill → Planche → Training**.
  Planche is detected from the name (`planche`, `protraction`, `maltese`) even when it is
  filed as Push, so it always leads its own block.
- One set of each exercise per round, then the next round. 3 × A, 3 × B becomes
  `A1 B1 C1 · A2 B2 C2 · A3 B3 C3`. Exercises with fewer sets drop out of later rounds.
- The state is derived from the set logs, so reopening a session resumes at the first set
  without a log. The player shows the round, the block badge and the next exercise.

## Monster Energy cabinet (manual mode)

- `app/src/main/assets/monster_cans.json` holds 300+ real flavours (`id`, `name`, `color`,
  `keywords`, `slot`). On first use it is seeded into the Room table
  `monster_can_species` (insert-or-ignore, idempotent).
- Flow: tap **Add can** → CameraX capture → the JPEG is rotated by EXIF, centre-cropped to a
  square and scaled to 720 px → a dialog asks for the flavour name with live suggestions from
  the catalog → the photo drops into its slot. `CanNameMatcher` is deterministic token/alias
  matching; a name nobody ships becomes a custom can with its own slot.
- The cabinet shows 2D cans drawn in Compose: uncollected slots are dark silhouettes with no
  labels; collected slots show **your cropped photo** and the flavour name.
- Unlock animation: spring bounce into place, 14-particle burst in the can's colour, the pop
  SFX and a medium haptic.
- No ML Kit, no Palette, no OCR and no network. The INTERNET permission is still removed.

## Edit plan and plan analysis

- **Edit plan** opens the seven-day overview and taps through to the Day Editor. The state
  chip cycles Rest → Training → Optional. It never re-enters onboarding.
- **Analyze plan** runs a staged report (`CoachAudit`) and grades three checks out of 10:
  - *Skill freshness*: handstand and planche must lead their session with long rest. A
    skill block inside a circuit is the critical warning.
  - *Joint frequency*: consecutive loaded days. More than three push days without rest
    warns, and the report shows the longest run.
  - *Volume*: sets per muscle group per day. More than 12 push or pull sets in one day
    warns and is capped by the auto-fix.
- The report is card-based: a readiness score, the graded checks, pros, risks and a verdict
  with concrete advice.
- **Improve Plan** rewrites the Room data in one transaction: skills move to the front with
  three minutes of rest, volume is capped, seven training days become a rest day. A success
  overlay lists exactly what changed.
- Thresholds are fixed and pure Kotlin (`CoachAudit`, `PlanAutoFix`), so the same plan always
  produces the same report. No network, no randomness.

## Design system

- **Logo**: the supplied artwork with the white background removed, generated at every
  density by `python tools/make_brand_assets.py`. It is used for the launcher icon (dark
  squircle background), the cold-start splash (`splash_background` + Android 12 system
  splash), the welcome screen and the dashboard header.
- **Squircles**: shapes are 10 / 14 / 18 / 24 / 30 dp; nothing uses the default Android scale.
- **Cards**: no hairline borders. Cards sit on `#0B0F14` at `#151A22` with a 14 dp shadow.
- **Tab bar**: a floating translucent pill with side and bottom margins, 30 dp corners,
  22 dp shadow, which slides away when scrolling down and springs back when scrolling up.
- **Typography**: Inter, instanced to 400/500/600/700/900 from the official variable font.
- **Alive background**: three desaturated colour clouds drifting on 24 s / 31 s / 38 s orbits
  behind the dashboard.

## Animation and SFX rules

- Buttons and cards use a 0.92x spring press with overshoot; taps are silent and pair the
  press with a light haptic.
- Lists stagger in 50 ms apart; the onboarding day card expands into the Day Editor through a
  shared element transition.
- Logging a set pops the checkmark to 1.3x, bursts 12 glowing particles, fires the medium thud
  and the set-completion pop. Consecutive sets raise the pop pitch by 1.1x each.
- The streak flame is the bundled `fire.json` Lottie animation; a zero streak is the same
  flame at 28% opacity.
- Three SFX ship, all 16-bit PCM WAVs: set pop, scan sweep and the celebration hit. Buttons
  make no sound.
- SFX play only for the three Lottie moments (fire, trophy, scanner) and set completion. They
  go through the media stream, not the system-sound stream, and are queued until SoundPool
  finishes loading.
- Numbers roll like a slot machine when values change (streak, stats).
- Motion follows the stored `animationsEnabled` preference; Settings holds the backup card
  only.

## Data and migration policy

- Room version 6, `exportSchema = true`, schemas committed. No
  `fallbackToDestructiveMigration`.
- Settings live in DataStore Preferences (`six_months_settings`).
- Dates used for streak logic are `java.time.LocalDate` ISO strings, never UTC timestamps.
- No INTERNET permission. Nothing leaves the device except the JSON backup you explicitly
  export through the system file picker.

## Tests

```bash
./gradlew testDebugUnitTest
```

Unit tests cover streak rules, Coach Review, weekly progression, backup validation and
schema compatibility, category suggestions, the 40-flavour catalog, the typed-name matcher,
the round-robin circuit and the deep plan report (grades, imbalance, joint warnings and
determinism). Compose UI, CameraX, notifications, media and SAF flows need a device.

## APK size

No ML model and no native OCR pipeline is bundled any more. The single universal debug APK
covers every ABI and is around 20 MB.

## Known limitations

- The build was installed and started on a connected phone: the process stayed alive with
  no entry in the crash log. The device was PIN-locked, so the full UI, camera, notification
  delivery and sound output still need an unlocked hands-on pass. The `MIGRATION_4_5` and
  `MIGRATION_5_6` upgrades also need a real old-install upgrade test.
- The manual flow depends on the typed name being close to a shipped flavour; anything else
  becomes a custom can instead of an error, and a second photo of the same flavour replaces
  the first one.
- The cabinet lists real Monster Energy flavours for identification only; the app is not
  affiliated with Monster Beverage Corporation and bundles no Monster artwork.
- Freeze tokens start at 2 and are not earned automatically; the spec defines no earning rule.
- The six-month unlock is `planPhaseStartedAt + 182 days`; it does not require a minimum
  number of sessions.
- Backup JSON stores capture URIs, not JPEG bytes; photos stay in app-private storage.
- Release builds keep R8 disabled until the app has been exercised on a device with real data.
