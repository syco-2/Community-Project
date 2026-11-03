# Hatch

Hatch is a simulator about creatures you raise, with a small economy built around them. You
start with one egg, three seeds and eighty coins. From there the loop is care, coins, food,
cosmetics and a collection, and every part of it is local.

## Care

Hunger, energy and mood fall on their own, also while the app is closed. Opening the app
recounts the time that passed, so a creature left alone overnight comes back hungry. Food
lifts hunger, play lifts mood and costs energy, sleep refills energy. Every action runs a
short animation and adds experience to the active creature.

Experience is kept per creature. Four stages follow the experience: egg, hatchling, sprout
and adult. A stage change plays its own clip. Switching between creatures does not reset
anything, each one keeps its own stage.

## Economy

Coins come from three sources: the three daily tasks, a short timing game, and the bonus for
finishing all three tasks. The tasks are picked from a pool by the date, so the set changes
each day and stays the same during the day. Progress is counted while you play, a finished
task is collected with one tap.

The timing game is called Gather. A marker sweeps across a bar and a tap scores how close it
was to the middle, three taps per run, energy for each run. It is the repeatable way to earn
coins.

Spending happens in the pantry and in the shop. The pantry holds six foods with different
prices and different effects, from a seed for four coins to a crystal for forty five. The
shop sells six skins, which recolour the creature, and five further creatures with their own
shape accent, name and idle motion.

## Streak

A care action on a new day continues the streak. A day without care ends it. The streak is
shown on the nest and on the task screen.

## Animations are data

The motion is not written into the interface. It lives in `shared/animations.json`: thirteen
clips with a duration, a loop flag and tracks for four parts, the body, the shadow, the ring
and a spark. Each track is a list of keyframes with a normalised time `t` and values for
`x`, `y`, `rot`, `sx`, `sy` or `opacity`. A small player samples the track, smooths between
two keyframes and writes the result to the part.

The clips are `idle`, `sway`, `bob`, `eat`, `play`, `sleep`, `sad`, `evolve`, `love`, `coin`,
`tap`, `hatch` and `wake`. All content, including food, skins, creatures, tasks and both
language tables, sits in `shared/` and is copied by `tools/sync-shared.mjs` into the web
assets and the app data folder. The browser and the phone read the same numbers, so a curve
only has to be changed once.

## Interface

Four tabs sit at the bottom: Nest, Pantry, Shop and Today. Everything that is a list is a
card, on every screen, with the same padding, radius and line weight. The nest is one stage
with the creature, three bars, three actions and a small card grid. Feeding opens a sheet
with the food you own instead of a second screen.

## Storage

State is stored on the device, in localStorage in the browser and in AsyncStorage on the
phone. There is no login, no account, no server and no connection needed after install.

## Download

The Android build is published as a GitHub release. This link always points to the latest
version and starts the download right away:

```
https://github.com/syco-2/Community-Project/releases/latest/download/Hatch.apk
```

All releases:

```
https://github.com/syco-2/Community-Project/releases
```

## Source

The source code is not part of this repository. This repository holds this description
only, the APK is attached to the release. The web version and the Android source stay
with the author.

Inspired by syco.
