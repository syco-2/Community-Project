# Puddle

Puddle is a party game app for a group and one phone. You add the names of everyone
playing once, then pick a game. Three to twenty players, nothing to install, nothing to
print, no account, and it works without a connection.

Ten games sit on one hub, and the switch at the top moves the whole app between German and
English. The choice is kept, so the next evening starts where the last one ended.

## Impostor

Everyone gets the same secret word except the impostors, who see a hint about the word
instead. You pass the phone around so each person reads their card in private, then the
group talks, describes the word without giving it away, and votes on who was faking it.

Before the round you pick the categories. Ten are in the app: Kitchen, Food, Travel,
Animals, Places, Situations, Jobs, Sports, Tech and Nature. Any number of them can be on at
once, and the round only draws from the ones you selected. Together they hold 240 words,
so the same round rarely comes twice. One to three impostors can be in play, the discussion
timer runs from one to four minutes, and the score carries across rounds.

## Werewolf

A village against a hidden pack. Roles are dealt in secret, one card at a time: werewolves,
a seer, a doctor and villagers. Every player sees their own card and what it can do, so the
phone can moderate the whole night without anyone sitting out.

Night falls and everyone closes their eyes. The pack wakes and picks the player it hunts.
The seer wakes and reads one player. The doctor wakes and protects one. Morning arrives and
the app says who died, or that the doctor was there in time, and lists who is still in.
Then the day: the timer runs, everyone argues, and the group votes one player out. The vote
shows what that player really was. Villagers win when the last werewolf is out, werewolves
win when they are as many as the rest of the village. Werewolf count and the seer and the
doctor can be turned off in the setup.

## Card games

The result screen keeps a score per person: the village takes a point for catching an
impostor, an impostor takes two for walking free. The score stays for the evening and goes
back to zero when the group changes.

Most Likely To, Would You Rather, Truth or Dare, Charades and Taboo hand out one card at a
time with a next button. Truth or Dare asks for truth or dare first. Taboo shows the word
and the words that are off limits under it.

Bomb gives a category and twenty seconds. Everyone says one word that fits and passes the
phone on, and whoever holds it at zero takes the hit. Who's the Liar hands everyone the same
question except one player, who gets a different one. After the answers the group votes, and
the result shows both questions. The Chooser spins through the names and picks one.

## How it plays

Tapping a game takes you to the player list, where a name is typed in and added with the
plus button, up to twenty. Each name gets a coloured initial, so a quick vote does not mean
reading every name twice. A name can be removed again by tapping its cross. Under three
players the start button stays closed. Everything after that is one tap at a time: pass,
reveal, hide, talk, vote, result.

The secret card never stays on screen. The reveal is a single card, and the button under it
clears the card before the phone changes hands. The timers run on the wall clock, so they
keep counting correctly when the app is in the background. Pausing freezes the remaining
time, resuming picks it up again.

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

Inspired by Splash.
