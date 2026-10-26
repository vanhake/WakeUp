# WakeUp

Android-Wecker im Stil von Alarmy: Der Wecker lässt sich nicht im Halbschlaf wegdrücken,
sondern nur durch eine Aufgabe beenden.

## Funktionen

- **Mehrere Wecker** mit Uhrzeit, Name/Label und Wochentagen (Werktags / Wochenende / Täglich
  oder frei wählbar; ohne Tage = einmalig).
- **Aufgaben zum Ausschalten**
  - **Rechnen** – Leicht / Mittel / Schwer, 1–10 Aufgaben. Falsche Antwort ⇒ neue Aufgabe.
  - **Schütteln** – 10–150 Schüttler.
  - **Barcode / QR-Code** – in den **Einstellungen** (Zahnrad) beliebig viele Codes mit Namen
    speichern (z. B. „Zahnpasta im Bad“) und pro Wecker einen davon auswählen. Mit Taschenlampe;
    als Notausgang gibt es 6 schwere Matheaufgaben, falls der Code nicht greifbar ist.
- **Schlummern begrenzt** – maximale Anzahl (0–10) und Dauer (1–30 Min) pro Wecker. Während ein
  Wecker schlummert, kann er in der App weder ausgeschaltet noch bearbeitet werden.
- **Kein Schummeln**: keine Ausschalten-Aktion in der Benachrichtigung, Zurück-Taste blockiert,
  die App leitet beim Klingeln immer zum Weckbildschirm. Bei Mathe/Schütteln wird es leiser,
  solange du die Aufgabe bearbeitest – nach 30 s ohne Eingabe ist es wieder laut und die
  Aufgabe beginnt von vorn.
- Weckton, Lautstärke (wird beim Klingeln erzwungen) und Vibration pro Wecker.
- Klingelt auch über dem Sperrbildschirm und nach einem Neustart (auch vor dem Entsperren).
- **Schlafanalyse** (Mond-Symbol): zeichnet die Nacht über das Mikrofon auf – automatisch
  (*Aus* / *Bei Wecker* / *Immer*, ab einer Startzeit) oder manuell – und zeigt morgens einen
  Bericht: Schlafdauer, Einschlafzeit, Wachphasen, Unruhe, Schnarchzeit, Atemaussetzer-Verdacht,
  Atemfrequenz, Husten, Sprechen im Schlaf, Störgeräusche, „Licht aus“ und einen Schlafwert.
  Geräusche erkennt die App mit Rhythmus-Analyse und dem KI-Modell YAMNet (offline); liegt das
  Handy auf der Matratze, hilft der Bewegungssensor. Hörproben werden nach 24 Stunden gelöscht.
  Alles bleibt auf dem Handy; keine medizinische Diagnose.
  Details: [docs/SCHLAFANALYSE.md](docs/SCHLAFANALYSE.md).
- **Smart Wake** pro Wecker: klingelt bis zu 10/20/30 Min früher, wenn die Schlafanalyse eine
  leichte Schlafphase (Bewegung) erkennt.
- Dunkles, schlichtes UI mit großen, weit auseinanderliegenden Buttons.

## Bauen & installieren

Jeder Push baut per GitHub Actions eine Debug-APK (Tab **Actions** → letzter Lauf →
Artefakt `WakeUp-debug-apk`). Lokal mit installiertem Android SDK:

```sh
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # Unit-Tests
```

Beim ersten Start die angezeigten Berechtigungen erlauben (Benachrichtigungen, exakte Wecker,
Vollbild-Anzeige, „Über anderen Apps einblenden“ – ohne Letzteres öffnet sich der Wecker bei
entsperrtem Handy nicht von selbst, sondern nur über die Benachrichtigung). Für die Barcode-Aufgabe wird die Kamera benötigt, für die Schlafanalyse das Mikrofon.

## Versionierung

Die Version wird automatisch aus der Git-Historie berechnet: `versionName` = `<Basis>.<Anzahl Commits>`
(z. B. `1.1.3`), `versionCode` = Anzahl Commits. Jeder Commit erzeugt so eine höhere Version, die
sich über die installierte App drüberinstallieren lässt. Für größere Sprünge die Basisversion
`baseVersion` in `app/build.gradle.kts` anheben. Die APK heißt `WakeUp-<Version>-debug.apk`,
die aktuelle Version steht in der App unter Einstellungen → Über.

## Technik

Kotlin, Jetpack Compose (Material 3), `AlarmManager.setAlarmClock` für exakte Wecker,
Foreground-Service für Ton/Vibration, CameraX + ML Kit (offline) für Barcodes,
`AudioRecord` + eigene Signalverarbeitung (FFT, Rhythmus-Erkennung) und TensorFlow Lite mit
[YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) (Apache 2.0)
für die Schlafanalyse.
Mindestens Android 8.0 (API 26).
