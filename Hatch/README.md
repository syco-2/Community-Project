# Hatch

Hatch is a small simulator about one creature that grows while you look after it. It starts
as an egg, hatches, sprouts and grows up, and every stage changes how it moves and how it
looks. There is no account, no server and no connection needed after it is installed.

## Care

Hunger, energy and mood all drift on their own. Feeding raises hunger and lifts the mood a
little. Playing costs energy and lifts the mood a lot. Sleeping refills energy while the
creature sits still. Points come from care, and enough points move the creature to its next
stage, which plays a short change animation.

The numbers keep drifting while the app is closed. A creature left alone for a day comes
back hungry, which is the whole point of a simulator.

## Animations are data

The motion is not written into the interface. It lives in `assets/animations.json`: clips
with a duration, a loop flag and tracks for three parts, the body, the shadow and the ring.
Each track is a list of keyframes with a normalised time `t` and transform values, either
`x`, `y`, `rot`, `sx`, `sy` or `opacity`. A small player samples the track, smooths between
two keyframes and writes the result to the part.

The clips are `idle`, `eat`, `play`, `sleep`, `evolve` and `sad`. The browser reads the file
with `fetch`, the Android build imports the same file, so both play the same timing without
a second copy of the motion. Changing a curve means editing one JSON file.

## Interface

The screen is one stage with the creature, three bars and three actions. The switch at the
top moves the whole app between German and English and stays where it was left.

State is stored on the device, in localStorage in the browser and in AsyncStorage on the
phone. Nothing is sent anywhere.

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
