# Third-party notices

## Sounds — wyzowl/sfx-kit (CC0 1.0)

`app/src/main/res/raw/sfx_*.wav` come from https://github.com/wyzowl/sfx-kit and are
dedicated to the public domain under CC0 1.0. Mapping:

| File | Source | Used for |
| --- | --- | --- |
| `sfx_pop.wav` | pop | consecutive-set pop (pitch raised 1.1× per set) |
| `sfx_success.wav` | cinematic hit | streak and weekly-goal celebration |
| `sfx_scan.wav` | scan sweep | camera shutter |

`sfx_click.wav`, `sfx_whoosh.wav` and `sfx_penalty.wav` were removed with the click SFX and
Penalty Mode.

## Font — Inter (SIL Open Font License 1.1)

`app/src/main/res/font/inter_*.ttf` are static instances (400/500/600/700/900) of the
variable font from https://github.com/google/fonts/tree/main/ofl/inter, generated with
fontTools. Inter is © The Inter Project Authors and licensed under the SIL Open Font
License 1.1. The font files may be bundled and redistributed with the app; they may not be
sold on their own.

## AndroidX ExifInterface 1.3.7

`androidx.exifinterface:exifinterface:1.3.7`, Apache 2.0. Used to read the camera JPEG
orientation before the can photo is centre-cropped into a square.

ML Kit Text Recognition and AndroidX Palette are no longer part of the app: the cabinet is
filled manually, so there is no OCR model and no colour-extraction dependency to bundle.

## Brand references

The cabinet lists 300+ real Monster Energy flavours by name and approximate can colour purely
for identification of cans the user owns. The app is not affiliated with, endorsed by or
sponsored by Monster Beverage Corporation. No Monster Energy artwork, logo or typeface is
bundled — every can in the cabinet is drawn procedurally in Compose.

## App logo

`app/src/main/res/drawable-nodpi/logo.png` and the launcher mipmaps are generated from the
project owner's own logo file by `tools/make_brand_assets.py`, which removes the white
background and produces every density. No third-party artwork is involved.

## Lottie player, lottie-web (MIT License)

`web/vendor/lottie.min.js` is lottie-web 5.12.2 from
https://github.com/airbnb/lottie-web, MIT licensed. It renders the bundled
`Fire.json`, `Trophy.json` and `scanner.json` animations in the browser.
