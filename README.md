# Memory · Tag & Nacht

Gruppe 11 · Ein Browser-Spiel (HTML, CSS, JavaScript): Memory bei Tag, geheime Entscheidungen bei Nacht.

## Starten

Keine Installation, keine Datenbank, kein Build-Schritt nötig.

**Variante A:** `index.html` doppelklicken – das Spiel öffnet sich im Browser.

**Variante B (VS Code):** Ordner öffnen, Rechtsklick auf `index.html` → *Open with Live Server*.

Getestet in Chrome, Firefox und Safari.

## Spielprinzip

**Tag:** Zwei Spieler decken abwechselnd je zwei Karten auf. Passt ein Paar, gehört es dem Spieler und er ist nochmal dran. Passt es nicht, ist der andere am Zug. Nach einer eingestellten Anzahl Memory-Runden wird es Nacht.

**Nacht:** Jeder Spieler wählt geheim eine von drei Aktionen:

| Aktion | Vorteil | Risiko |
|---|---|---|
| Auf Nummer sicher | Deine Paare sind geschützt | Du gewinnst nichts dazu |
| 1 Paar erhalten | Du erhältst 1 Paar | Gegner stiehlt: du verlierst 2 Paare |
| 2 Paare vom Gegner stehlen | Du nimmst dem Gegner 2 Paare | Gegner verteidigt: du bekommst nichts. Gegner stiehlt auch: du verlierst 2 Paare |

Danach wird es wieder Tag. Das Spiel läuft, bis ein Spieler keine Paare mehr hat oder es im Menü beendet wird.

## Getroffene Festlegungen

- **Gewertet werden Paare**, nicht Punkte. Wer am Ende die meisten Paare hat, gewinnt. Bei Gleichstand ist es unentschieden.
- **Stehlen:** immer 2 zufällige Paare des Gegners. Hat er weniger, werden alle genommen. Alle Paare sind gleich viel wert, deshalb ist keine Auswahl nötig.
- **Reihenfolge der Nacht:** Das erhaltene Paar wird zuerst gutgeschrieben, danach wird gleichzeitig gestohlen. Ein frisch erhaltenes Paar kann also in derselben Nacht gestohlen werden.
- **Nacht 1 ist Schonfrist.** Ab Nacht 2 verliert sofort, wer nach der Auflösung 0 Paare hat. Landen beide bei 0, ist es unentschieden.
- **Warnung:** Wer nur noch 2 Paare oder weniger hat, wird in der Nacht gewarnt.
- **Zeit** wird pro Spieler gezählt, und zwar nur, solange er am Zug ist.

## Technik

| Schicht | Umsetzung |
|---|---|
| Oberfläche (Presentation) | HTML und CSS, Spielfeld und Screens werden per JavaScript gerendert |
| Logik (Application) | JavaScript: Paarerkennung, Computer-Gegner, Auflösung der Nachtphase |
| Daten (Data) | `localStorage` des Browsers: Highscores, getrennt nach Schwierigkeit |

Es gibt keinen Server und kein Back-End. Alles läuft im Browser des Spielers.

Die Kartenmotive sind als SVG direkt im Code gezeichnet, es werden keine Bilddateien geladen. Die Schriften kommen von Google Fonts. Ohne Internetverbindung läuft das Spiel trotzdem, nur mit Standardschriften.

## Highscores

Ergebnisse werden im Browser gespeichert (`localStorage`), getrennt nach Schwierigkeitsstufe. Sie bleiben beim Schließen erhalten, liegen aber nur in diesem einen Browser. In einem privaten Fenster oder bei blockierten Website-Daten speichert das Spiel nichts und weist darauf hin.

Gespeichert wird erst, wenn ein Spiel zu Ende gespielt oder im Menü gewertet wird. Abgebrochene Spiele kommen nicht in die Liste, der Computer-Gegner ebenfalls nicht.

## Bedienung

- **Taste P** oder der Pause-Button hält das Spiel an und verdeckt das Spielfeld.
- Über **Menü** geht es zurück zur Startseite, zum Levelwechsel, oder man beendet das Spiel mit Wertung.
