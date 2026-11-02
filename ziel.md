PROMPT FÜR DIE KI (Kopiere das exakt so):

Hör auf zu raten. Die React Native App für SixMonths existiert bereits vollständig im Ordner SixMonths im Hauptverzeichnis. Du musst keinen Code erfinden oder auf mich warten. Du nimmst den vorhandenen Code und setzt jetzt alle folgenden Punkte exakt um. Es gibt keine Ausreden mehr.

Schritt 1: Die APK verschieben und verlinken
Die fertige APK liegt auf diesem absoluten Pfad auf meinem System:
D:\Community\SixMonths\app\build\outputs\apk\debug\app-debug.apk
Kopiere diese Datei in den Projektordner SixMonths und benenne sie in SixMonths.apk um.
Passe die data/apps.js so an, dass der APK-Button für SixMonths exakt auf SixMonths/SixMonths.apk verlinkt. Der Button muss in der UI sichtbar sein.

Schritt 2: Die 1-zu-1 Web-App aus dem React Native Code bauen
Lies den kompletten React Native Code im Ordner SixMonths. Deine Aufgabe ist es, diesen Code 1-zu-1 in eine Web-App zu übersetzen (z.B. in React, wenn das dein Setup ist).

Keine Unterschiede: Jedes UI-Element, jede Funktion und jedes Design-Detail muss identisch mit der APK sein.

Lokal: Ersetze alle nativen Speicher-Methoden (wie AsyncStorage) durch Web-Standards (localStorage oder IndexedDB). Kein Server, kein Login.

Rückweg: Die Seite muss einen funktionierenden "Zur Library" Button haben.

Schritt 3: Die Library-Karte reparieren
In der data/apps.js für SixMonths muss stehen:

Name: "SixMonths"

Inspired by: "syco" (exakt so, damit "Inspired by syco" angezeigt wird)

Die anderen Felder bleiben leer.

Der Web-App-Button verlinkt auf "SixMonths/".

Der APK-Button verlinkt auf "SixMonths/SixMonths.apk".

Schritt 4: Der GitHub Push (PFLICHT)
Ich habe auf GitHub nachgesehen. Das Repository ist noch leer. Führe jetzt zwingend folgende Befehle im Terminal aus, um alles hochzuladen:

git init

git add .

git commit -m "SixMonths Web-App and APK release"

git branch -M main

git remote add origin https://github.com/syco-2/Community-Project.git

git push -u origin main

REGELN (Halte dich strikt an instructions.md):

Keine Kommentare im Code, die Funktionen erklären.

Keine Emojis im UI.

Keine Em Dashes.

Kein AI-Slop (keine "Welcome"-Texte, keine Slogans, keine Feature-Listen).

Führe Schritt 1 bis 4 jetzt aus. Lies die vorhandenen Dateien. Wenn du fertig bist, gib mir eine kurze, sachliche Bestätigung, dass die Web-App gebaut wurde, die APK verlinkt ist und der Push erfolgreich war.