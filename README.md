# Memory · Tag & Nacht

Ein Java-Swing-Spiel: Memory bei Tag, geheime Entscheidungen bei Nacht (Bei sich bleiben / Kartenhaus / Stehlen).

## Starten (ohne Installation, ohne Datenbank)

Voraussetzung: **JDK 17 oder neuer** (`java -version` im Terminal prüfen).

```bash
java MemoryGame.java
```

Ergebnisse werden dann nur im Arbeitsspeicher gehalten (Hinweis unten auf der Startseite).

## Mit SQLite (Ergebnisse bleiben gespeichert)

1. SQLite-JDBC-Treiber herunterladen: https://github.com/xerial/sqlite-jdbc/releases → `sqlite-jdbc-<version>.jar`
2. Neben `MemoryGame.java` legen und starten:

```bash
java -cp sqlite-jdbc-3.46.1.3.jar MemoryGame.java
```

Beim ersten Start wird `memory.db` angelegt und befüllt (Schwierigkeitsstufen + 24 Karten inkl. PNG-Bilddaten als BLOB).

**IntelliJ / Eclipse:** Datei in ein neues Projekt kopieren, das JAR als Library hinzufügen, `MemoryGame.main` starten.

## Regeln testen (Konsole)

```bash
java MemoryGame.java --test
```

Spielt alle 9 Kombinationen der Ergebnismatrix durch und zeigt, wer wie viele Paare vorher/nachher hat.

## Tabellen

| Tabelle | Spalten |
|---|---|
| `karte` | id, symbol, bild (BLOB), kategorie, pair_id, wert, farbe |
| `spieler` | id, name, bisherige_punkte, anzahl_spiele |
| `spielergebnis` | id, spieler_id, punkte, benoetigte_zeit_sek, anzahl_versuche, datum |
| `schwierigkeitsstufe` | id, bezeichnung, anzahl_kartenpaare |

## Getroffene Festlegungen (im Team besprechen)

- **Tag:** 2 Spieler abwechselnd. Paar gefunden → gehört dir, du bist nochmal dran. Kein Paar → der andere ist dran.
- **Punkte** = Summe der Kartenwerte deiner Paare (Wert 1–5, sichtbar als Punkte auf der Karte).
- **Stehlen:** bis zu 2 Paare, welche genau ist auf der Startseite wählbar (wertvollste / billigste / zufällig).
- **Reihenfolge der Nacht:** Kartenhaus-Paar wird zuerst gutgeschrieben (kann also in derselben Nacht gestohlen werden), dann wird gleichzeitig gestohlen.
- **Spielende:** nach max. Zyklen oder wenn nach einer Nacht ein Spieler 0 Paare hat und der andere alle.
- **Ergebnis** wird am Spielende pro Spieler gespeichert.
