# Impostr

Impostr is a local party game for three to twelve people and one phone. Everyone gets
the same German word, except one player. The imposter only gets the category and has to
blend in while the group gives hints, argues and votes.

Inspired by Splash.

## Status

The web app is complete. The React Native source for Android is complete and builds
with Expo. Version 1.1.0 is available as an APK.

## What the app does

- Setup for 3 to 12 players with editable names
- One imposter, or two from six players
- 1000 German words in eight decks, all free
- Pass and reveal flow with a card that flips on tap
- Discussion timer for 2, 3 or 5 minutes, pause and resume, random first speaker
- One accusation per round, then the word and both sides are revealed
- Local score across rounds on the same device
- Word browser with spoiler gate, deck filter and search
- English interface with a complete German switch
- Reduced motion setting plus system reduced motion support

## Data and privacy

There is no login, no account, no backend and no analytics. Names, scores and settings
stay on the device in local storage. The web app works offline after it is loaded and the
Android app has no runtime network dependency.

## Download

[Impostr 1.1.0 APK](https://github.com/syco-2/Community-Project/releases/download/impostr-v1.1.0/Impostr.apk)

## Run the web app

Open `Impostr/index.html` in a browser. It is plain HTML, CSS and JavaScript with no build
step. A local server is only needed if the browser blocks local scripts.

## Run the Android app

The Expo project is in `Impostr/app`. It reads words, translations, tokens and game logic
from `Impostr/shared`.

```
cd Impostr/app
npm install
npx expo start
```

## Source

The complete source and shared content live with the project. The release repository
holds this description and the APK as a release asset.
