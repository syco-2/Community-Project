# Puddle

Puddle is a party game app for a group and one phone. You add the names of everyone
playing once, then pick a game. Three to twenty players, nothing to install, nothing to
print, no account, and it works without a connection.

## Games

**Impostor.** Everyone gets the same secret word except one player. That player sees
"Impostor" and a hint about the word instead. You pass the phone around so each person
reads their card in private, then the group talks for as long as you set, tries to
describe the word without giving it away, and votes on who was faking it. One to three
impostors per round, six word packs, and the score runs across rounds: the group gets a
point each time it catches an impostor, the impostor gets two points for walking free.

**Most Likely To.** A statement comes up, the group points at whoever fits it best, and
the next card is one tap away. Eighteen statements, reshuffled when the deck runs out.

**Would You Rather.** Two options, no third way out. The group argues, picks, and moves
on to the next card.

**Truth or Dare.** Pick a truth or a dare first, then the card follows. Fourteen of each,
separate decks, and a switch if the group wants the other kind.

**Bomb.** A random category and twenty seconds. Everyone says one thing that fits and
passes the phone on. Whoever holds it when the timer runs out is out.

## How it plays

The hub is a grid of five games. Tapping one takes you to the player list, where a name
is typed in and added with the plus button, up to twenty. A name can be removed again by
tapping its cross. Under three players the start button stays closed. Everything after
that is one tap at a time: pass, reveal, hide, talk, vote, result.

The impostor word never stays on screen. The reveal is a single card, and the button
under it clears the card before the phone changes hands. The discussion timer runs on the
wall clock, so it keeps counting correctly when the app is in the background. Pausing
freezes the remaining time, resuming picks it up again.

The name list and the settings are stored on the device. The web version writes to
localStorage, the Android version writes to AsyncStorage. Nothing is sent anywhere, there
is no login, no server, and the app does not need the internet after it is installed.

## Download

The Android build is published as a GitHub release. This link always points to the latest
version and starts the download right away:

```
https://github.com/syco-2/Community-Project/releases/latest/download/Puddle.apk
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
