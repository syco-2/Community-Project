# Puddle

Puddle is a party game app for a group and one phone. You add the names of everyone
playing once, then pick a game. Three to twenty players, nothing to install, nothing to
print, no account, and it works without a connection.

## Impostor

Everyone gets the same secret word except the impostors, who see a hint about the word
instead of the word. You pass the phone around so each person reads their card in private,
then the group talks for as long as you set, describes the word without giving it away,
and votes on who was faking it.

Before the round you pick the categories. Ten are in the app: Kitchen, Food, Travel,
Animals, Places, Situations, Jobs, Sports, Tech and Nature. Any number of them can be on at
once, and the round only draws from the ones you selected. Together they hold 240 words,
so the same round rarely comes twice. One to three impostors can be in play, the discussion
timer runs from one to four minutes, and the score carries across rounds: the group gets a
point each time it catches an impostor, the impostor gets two for walking free.

## Werewolf

A village against a hidden pack. Roles are dealt in secret: werewolves, a seer, a doctor
and villagers. The phone works as the moderator, so nobody has to sit out.

Night falls and the werewolves see their pack, then choose one player to remove. The seer
wakes and reads one player's role. The doctor wakes and protects one player. Morning
arrives and the app says who died, or that someone was saved.

Then the day: the timer runs, everyone argues, and the group votes one player out. The app
reveals what that player was. Villagers win when the last werewolf is out. Werewolves win
when they are as many as the rest of the village. Werewolf count and the seer and doctor
can be turned off in the setup.

## How it plays

The hub is two tiles. Tapping one takes you to the player list, where a name is typed in
and added with the plus button, up to twenty. A name can be removed again by tapping its
cross. Under three players the start button stays closed. Everything after that is one tap
at a time: pass, reveal, hide, talk, vote, result.

The impostor word never stays on screen. The reveal is a single card, and the button under
it clears the card before the phone changes hands. The timers run on the wall clock, so
they keep counting correctly when the app is in the background. Pausing freezes the
remaining time, resuming picks it up again.

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
