PROMPT FÜR DIE KI (Kopiere das exakt so):

Wir haben drei Aufgaben zu erledigen. Das Design der Cards muss repariert werden, die instructions.md muss geupdatet werden, und der GitHub-Push muss endlich mit dem richtigen Account funktionieren, ohne den Hauptaccount auf diesem PC zu stören. Arbeite die Punkte exakt in dieser Reihenfolge ab.

Aufgabe 1: Das Card-Design reparieren (Logo & Layout)
Das aktuelle Layout der App-Karten ist falsch. Das Logo darf nicht einfach unter dem Namen im Textfluss hängen. Setze das Design exakt so um:

Das Logo (App-Icon) muss genau auf der Trennlinie zwischen dem oberen Thumbnail (Screenshot) und dem unteren Content-Bereich sitzen (also halb über dem Bild, halb über dem Content).

Nutze dafür position: absolute auf dem Logo-Container. Er muss horizontal zentriert sein (left: 50%; transform: translateX(-50%);).

Das Logo muss einen eigenen Hintergrund haben, abgerundete Ecken (z.B. border-radius: 16px) und einen weichen Schatten, damit es sich abhebt.

Der Content-Bereich darunter braucht genug Abstand nach oben (padding-top), damit der Name (z.B. "SixMonths") nicht vom Logo verdeckt wird.

WICHTIG: Direkt unter dem Namen kommt eine kurze, knackige Beschreibung. Füge dafür der data/apps.js das neue Feld shortDescription hinzu und schreibe für SixMonths einen passenden kurzen Satz rein (keine Emojis, keine Em Dashes).

Aufgabe 2: instructions.md updaten
Schreibe diese neue Card-Struktur (Logo schwebt auf der Trennlinie mit abgerundeten Ecken, Name darunter, kurze Beschreibung darunter) in die instructions.md unter einem neuen Punkt "App-Card Design". Das muss für alle zukünftigen Apps in diesem Projekt zwingend so umgesetzt werden.

Aufgabe 3: GitHub Push mit dem richtigen Account (syco-2)
Wir haben ein Problem: Der PC ist global mit dem Account "spoonfulyourkitchen" angemeldet. Dieser Account muss für ein anderes Projekt unberührt bleiben. Wir stellen dieses Repository jetzt auf den Account "syco-2" um. Nutze dafür einen projektbezogenen SSH-Key:

Generiere einen neuen SSH-Key speziell für dieses Projekt:
ssh-keygen -t ed25519 -C "syco-2-community" -f ~/.ssh/id_ed25519_syco2 -N ""

Füge den neuen Key zum SSH-Agenten hinzu:
ssh-add ~/.ssh/id_ed25519_syco2

Lies den öffentlichen Schlüssel aus:
cat ~/.ssh/id_ed25519_syco2.pub

STOPPE HIER und gib mir den Inhalt des öffentlichen Schlüssels aus. Ich werde ihn in meinem GitHub-Account "syco-2" unter Settings -> SSH and GPG keys hinterlegen. Warte auf mein "OK".

Sobald ich das OK gebe, änderst du die Remote-URL des Repositories auf SSH:
git remote set-url origin git@github.com:syco-2/Community-Project.git

Committe die Änderungen am Design (git add ., git commit -m "Fixed Card Layout and added SSH auth") und führe den Push aus:
git push -u origin main

REGELN (Halte dich strikt an instructions.md):

Keine Kommentare im Code, die Funktionen erklären.

Keine Emojis im UI.

Keine Em Dashes.

Kein AI-Slop.

Führe Aufgabe 1 und 2 aus und stoppe dann bei Aufgabe 3, Schritt 4, um mir den SSH-Key zu geben. Warte auf meine Bestätigung, bevor du pushst.