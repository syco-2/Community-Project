# instructions.md

Diese Datei gilt für jeden Auftrag in diesem Projekt. Jede KI liest sie zuerst und hält sich strikt daran.

## Code

Keine Beschreibungen im Code. Kein JSDoc, keine Kommentare, die Funktionen oder Komponenten erklären, keine Beschreibungen von Funktionen, Props oder Variablen. Code bleibt nackt.

Ausnahme: ein Platzhalter, der syco auf fehlenden Inhalt hinweist, darf als Markierung im Code stehen, zum Beispiel `<!-- SPRUCH-PLATZHALTER -->`.

## Web-App

Keine Beschreibungen in der Web-App. Keine erklärenden Texte im UI, keine Tooltips, keine Hilfetexte, keine Onboarding-Texte, keine leeren Zustände mit Erklärungen.

Keine KI-Sprüche, keine klassischen Slogans, keine generischen Werbetexte. Wo ein Spruch, Slogan oder Claim nötig wäre, bleibt die Stelle leer und wird markiert: im Code als `<!-- SPRUCH-PLATZHALTER -->`, im UI als leerer, klar markierter Bereich.

Kein AI-Slop. Keine Hero-Texte, keine Willkommens-Floskeln, keine Feature-Listen mit Emoji-Häkchen, keine Buzzwords. Design und Inhalt bleiben reduziert, ehrlich, community-nah.

Keine Emojis im UI.

## App-Card Design

Jede App-Card in der Library folgt zwingend diesem Aufbau:

- Oben das Thumbnail mit fester Höhe von 160px und voller Breite.
- Das Logo sitzt als eigener Container absolut auf der Trennlinie zwischen Thumbnail und Content-Bereich: `top: 160px`, `left: 50%`, `transform: translate(-50%, -50%)`. Es hat einen eigenen Hintergrund, `border-radius: 18px` und einen weichen Schatten.
- Der Content-Bereich beginnt unter dem Thumbnail und hat `padding-top` von mindestens 52px, damit der Name nicht vom Logo verdeckt wird.
- Direkt unter dem Namen steht die kurze Beschreibung aus dem Feld `shortDescription`. Für jede neue App wird dieses Feld in `data/apps.js` gefüllt.
- Darunter folgen die lange Beschreibung, die Zeile "Inspired by" und die Aktionen.
- Kein Logo im Textfluss und kein Logo in der Beschreibungsfläche.

## Zeichen

Keine Em Dashes und keine En Dashes, nirgends. Weder in Texten, noch in Kommentaren, noch in Labels, noch in Markdown. Immer Komma, Punkt, Doppelpunkt oder Klammern verwenden. Bindestriche in CSS-Klassennamen, Dateinamen oder Komposita (zum Beispiel loading-text, Blau-zu-Weiß) sind davon nicht betroffen.

## Funktionsumfang

Nur eine Library mit App-Einträgen.

Kein Login, keine Auth, keine Backend-Accounts, keine Rollen, kein Admin-Bereich im UI.

Keine Bewertungen, keine Kommentare, keine Likes, keine Suche, keine Filter.

Keine KI-generierten App-Beschreibungen. Beschreibungen kommen vom Betreiber, fehlende Beschreibungen bleiben leer.

Kein Cookie-Banner, keine Newsletter-Popups.

## Repo-Struktur und Projekt-Ordner

Das Repository ist `https://github.com/syco-2/Community-Project`.

Für jedes neue Projekt (jede App), das in dieser Community-Library vorgestellt wird, wird ein eigener Ordner im Repository erstellt. 

Jeder dieser Projekt-Ordner enthält eine `README.md`. 

In dieser `README.md` stehen:
- Eine größere, ausführliche Beschreibung der App.
- Bei wem die App inspiriert wurde (Inspired by).
- Der Link oder die Datei für das APK-Release der App.

In der `README.md` sind ebenfalls keine Emojis und keine Em Dashes erlaubt.

## Technische Vorgaben für die Apps

Die Apps in diesem Projekt werden sowohl als Web-App als auch als APK für Android bereitgestellt.

- Die APK muss in nativem React (React Native) umgesetzt werden.
- Die Web-App und die APK müssen von den Funktionen und dem Design her absolut identisch sein.
- Es gibt niemals einen Login. Jede App bleibt lokal auf dem Gerät (Smartphone oder Browser).
- Es werden keine Server, Backends oder Datenbanken benötigt, die Kosten verursachen. Alle Daten werden lokal (LocalStorage im Browser, AsyncStorage oder SQLite in der App) gespeichert.

## Inhalte pflegen

App-Einträge liegen in `data/apps.js` und werden ausschließlich vom Betreiber gepflegt.

Ein Eintrag ohne APK bekommt `apk: false`, dann erscheint nur der Button für die Web-App.

Projekt-Ordner liegen im Root des Repositories, zum Beispiel `SixMonths`. Die Web-App eines Projekts liegt als `index.html` in diesem Ordner und ist über den Ordnernamen erreichbar, zum Beispiel `/SixMonths/`.

## Prüfliste vor der Abgabe

- Keine Kommentare im Code, die etwas erklären.
- Kein "Welcome to", keine Floskeln, keine Hero-Tagline.
- Keine Emojis im UI und in der README.
- Keine erfundenen Slogans, nur Platzhalter.
- Keine Beschreibungen von Funktionen oder Komponenten, weder im Code noch im UI.
- Kein Text außer "loading" im Loader, kein Footer-Text, kein Impressum.
- Keine Feature-Listen, keine Onboarding-Tour.
- Keine Em Dashes und keine En Dashes.
- Jeder App-Ordner hat eine README mit Beschreibung, Inspiration und APK-Release.
- Web-App und APK sind identisch und komplett lokal ohne Server.