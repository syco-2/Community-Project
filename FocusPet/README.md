# FocusPet

FocusPet turns weekly goals into daily time blocks and grows a small creature from the time you
keep. The loop is short: pick what the week should contain, place a block on a day, run the
focus timer, answer two questions, watch the pet react.

## Goals and planning

Onboarding takes four steps: the pet appears, you pick goals from a list, you pick the window of
the day that usually fits, and the first block is placed. Every goal carries a suggestion for how
often it fits in a week. Goals can be changed later from the plan screen.

The plan screen holds the week. A day strip selects the day, blocks are cards with a goal, a start
time and an end time. A block is added from a sheet with the goal grid, the day, the two times and
a duration picker, or taken from a suggestion that looks at how much of each weekly goal is already
planned and finds a free slot. Blocks can be reordered by dragging the handle, which swaps their
times, and a block that moves keeps a lower reward factor instead of a penalty. Times and the day
can be changed in the same sheet. A block can be stored as a template, and six templates come with
the app.

## Focus and evaluation

Start on a block and the focus screen opens: a countdown ring, the pet in a calm pose, the clock
and a progress line. Pause and resume are one tap. Finishing opens the result sheet, where the
block is marked done, partly or not done, and the effort is rated easy, okay or hard. A note is
optional.

Rewards follow the measured time, not the plan. The timer decides the minutes, a partly finished
block pays a reduced share, an early finish pays less, and a moved block pays less again. A block
that is not done pays nothing and is not punished. Experience moves the pet through four stages,
coins go to the shop. A day that ends with a finished block continues the streak, and two missed
days per week are covered by grace.

## Pet

The pet has four stages, egg, hatchling, sprout and guardian, three skills and a mood that follows
the day. Every action has its own clip: planning, dragging, dropping, starting, pausing, finishing,
claiming a quest, opening the shop, a stage change and a streak. The animations are data, in
`shared/animations.json`: twenty three clips with keyframes for the body, the shadow, the ring, the
spark and the aura. One player samples the same file in the browser and on the phone.

## Coins, quests and shop

Three daily quests are drawn from a pool of twelve by the date, so they change each day and stay
stable during it. Progress counts focused minutes, finished blocks, one goal in minutes, the
evening note, the coin chase and the number of planned blocks. A surprise per day offers coins,
experience, a shop discount, double coins on the next block or a free chase.

The shop sells five fur colours, six accessories, five backdrops and four effects. Each item is a
fixed price in coins, nothing is random and nothing expires. The coin chase is a thirty second game
that opens after the first finished block of the day: a coin moves across the field, a tap counts.

## Review

The evening sheet stores a mood and a note, once per day, and pays a few coins. The week report
reads every block and every session of the week: completion rate, focused minutes, the best day and
one next step per goal, lower it when less than half was kept, raise it when almost everything was
kept, hold it otherwise.

## Accessibility and storage

The interface has one dark theme, a language switch for English and German, large tap targets and
a reduced motion rule in the browser. Everything stays on the device, in localStorage in the
browser and in AsyncStorage on the phone. There is no login, no account, no server and no
connection needed after install.

## Download

The Android build is published as a GitHub release. This link always points to the latest version
and starts the download right away:

```
https://github.com/syco-2/Community-Project/releases/latest/download/FocusPet.apk
```

All releases:

```
https://github.com/syco-2/Community-Project/releases
```

## Source

The source code is not part of this repository. This repository holds this description only, the
APK is attached to the release. The web version and the Android source stay with the author.

Inspired by syco.
