# Plan: Schlafanalyse

Ziel: WakeUp soll nachts mit dem Handy auf dem Nachttisch den Schlaf aufzeichnen und morgens
einen Bericht zeigen – Schnarchzeit, mögliche Atemaussetzer, Unruhe, Schlafdauer/-qualität.
Alles läuft **offline auf dem Gerät**; es werden keine Audiodaten hochgeladen.

> **Wichtig vorab:** Ein Handy ersetzt kein Schlaflabor. Schnarchen und Geräusche lassen sich
> gut messen, Atemaussetzer nur als *Verdacht*, Schlafphasen nur grob. Die App wird das im
> Bericht klar so benennen und bei auffälligen Werten zum Arztbesuch raten.

---

## 1. Welche Daten das Handy liefern kann

| Sensor / Quelle | Was sich daraus ableiten lässt | Brauchbarkeit (Handy auf dem Nachttisch) |
|---|---|---|
| **Mikrofon** | Schnarchen, Atemgeräusche, Atempausen, Husten, Sprechen im Schlaf, Bewegungsgeräusche (Decke rascheln), Umgebungslärm (dB) | ⭐⭐⭐ Hauptsensor. Funktioniert bis ca. 1–2 m Abstand gut. |
| **Beschleunigungssensor** | Körperbewegungen (Aktigraphie) | ⭐ nur wenn das Handy **auf der Matratze** liegt. Auf dem Nachttisch misst er praktisch nichts. Optionaler Modus. |
| **Lautsprecher + Mikrofon als Sonar** (18–20 kHz) | Brustkorbbewegung → Atemfrequenz, Bewegungen, kontaktlos | ⭐⭐ technisch möglich (macht z. B. „Sleep as Android“), aber stark geräteabhängig, manche Handys filtern Ultraschall. Experimentell, späte Phase. |
| **Lichtsensor** | Wann es dunkel wurde (Licht aus), Morgenlicht | ⭐⭐ einfacher Zusatz für „Zubettgehzeit“. |
| **Bildschirm an/aus, Entsperren** | Einschlafzeit (letzte Handynutzung), nächtliches Aufwachen mit Handy | ⭐⭐⭐ zuverlässig, kostet nichts. |
| **Ladezustand** | Handy angesteckt = wahrscheinlich im Bett | ⭐⭐ Hinweis für Auto-Start-Vorschlag. |
| **Wecker-Daten der App** | Aufwachzeit, Anzahl Schlummern, Dauer bis Aufgabe gelöst | ⭐⭐⭐ schon vorhanden – guter Indikator für „wie wach war ich“. |
| **Health Connect** (optional, wenn Smartwatch vorhanden) | Herzfrequenz, SpO₂ (Sauerstoffsättigung), Schlafphasen der Uhr | ⭐⭐⭐ würde die Atemaussetzer-Erkennung massiv verbessern (Sauerstoffabfälle). Nur lesen, nur mit Zustimmung. |
| Barometer, Temperatur, Luftfeuchte | Raumklima | ✗ in kaum einem Handy als Raumsensor vorhanden bzw. durch Gerätewärme verfälscht. |

**Fazit:** Mikrofon + Bildschirm-/Licht-Ereignisse + Wecker-Daten reichen für eine
aussagekräftige Analyse von **Schnarchen, Geräuschen, Unruhe und Schlafzeiten**. Für
belastbare **Atemaussetzer**- und **Schlafphasen**-Aussagen fehlen dem Handy
Sauerstoffsättigung und Hirnströme – hier hilft nur eine Smartwatch (Health Connect) oder ein Arzt.

---

## 2. Was analysiert werden kann – und wie gut

### 2.1 Schnarchzeit ✅ gut machbar
- **Erkennung:** Audio in 1-s-Fenstern klassifizieren. Schnarchen hat ein typisches Muster:
  Geräuschereignisse von 0,3–3 s, rhythmisch alle 2–8 s (Atemzyklus), Energie vor allem
  zwischen 60 und 1000 Hz.
- **Stufe 1 (heuristisch):** Lautstärke-Hüllkurve + Frequenzband-Energie + Periodizität
  (Autokorrelation). Ohne Zusatzbibliothek, gut testbar.
- **Stufe 2 (KI-Modell):** Googles **YAMNet** (TensorFlow Lite, ~4 MB, offline) erkennt
  521 Geräuschklassen, u. a. *Snoring*, *Breathing*, *Cough*, *Speech*, *Gasp*. Die Heuristik
  bleibt als Vorfilter, damit das Modell nur bei Geräusch läuft (Akku).
- **Ausgabe:** Schnarchminuten gesamt, Anteil an der Schlafzeit, Lautstärke (leise/mittel/laut,
  dB), Zeitleiste, optional kurze Hörproben der lautesten Stellen (nur mit Opt-in, werden nach
  X Tagen gelöscht).
- **Grenze:** Schnarcht der Partner im selben Bett, kann das Handy die Personen nicht trennen
  (Hinweis im Bericht; Abstand/Ausrichtung zum eigenen Kopf hilft etwas).

### 2.2 Atemaussetzer (Apnoe-Verdacht) ⚠️ nur als Verdacht
- **Muster:** regelmäßiges Schnarchen/Atmen → **Stille ≥ 10 s** (medizinische Mindestdauer
  einer Apnoe) → **lautes Luftschnappen/Schnarch-Knall** beim Wiederatmen. Dieses
  Dreier-Muster ist akustisch recht charakteristisch.
- **Ausgabe:** Anzahl verdächtiger Ereignisse, „Ereignisse pro Stunde“ (angelehnt an den
  AHI: < 5 unauffällig, 5–15 leicht, 15–30 mittel, > 30 stark), Zeitpunkte mit Hörprobe.
- **Grenzen (ehrlich im Bericht):**
  - Hypopnoen (flachere Atmung ohne kompletten Aussetzer) sind akustisch kaum erkennbar.
  - Zentrale Apnoen ohne Schnarchen (keine Geräusche vorher/nachher) werden übersehen.
  - Leises Atmen kann bei 1 m Abstand unter der Mikrofon-Wahrnehmung liegen → falsche Pausen.
    Deshalb zählt nur eine Pause **zwischen zwei hörbaren Atemphasen**.
  - Kein SpO₂ → keine Aussage über Sauerstoffabfall (außer mit Smartwatch über Health Connect).
- **Empfehlung:** Wenn mehrere Nächte > 15 Ereignisse/h zeigen → deutlicher Hinweis, das beim
  Hausarzt/Schlafmediziner abklären zu lassen (Polygraphie). Keine Diagnose.

### 2.3 Atemfrequenz ⚠️ teilweise
- Bei hörbarem Atmen/Schnarchen aus der Periodizität der Hüllkurve (z. B. 14 Atemzüge/min).
- Bei stiller Atmung nur per Sonar (Phase 3) oder Smartwatch.

### 2.4 Unruhe / Bewegung ✅ machbar
- Bewegungsgeräusche (Rascheln, Bett knarzt: kurz, breitbandig, nicht periodisch) aus dem
  Audio; zusätzlich Beschleunigungssensor im „Handy auf der Matratze“-Modus.
- Ausgabe: unruhige Minuten, Bewegungsereignisse pro Stunde, Zeitleiste.

### 2.5 Schlafdauer, Einschlafzeit, Aufwachen ✅ gut machbar
- **Zeit im Bett:** Start der Aufzeichnung bis Wecker ausgeschaltet.
- **Einschlafzeit (Latenz):** letzte Handynutzung / Licht aus bis zur ersten längeren
  ruhigen Phase (≥ 15 min ohne Bewegungsgeräusche, gleichmäßige Atmung/Schnarchen).
- **Wachphasen:** Bildschirm an, längere Bewegungsphasen, Sprechen, Licht an (Toilette).
- **Schlafeffizienz:** geschätzte Schlafzeit ÷ Zeit im Bett.

### 2.6 Schlafphasen (leicht/tief/REM) ❌ nur sehr grob
- Echte Phasen brauchen EEG. Aus Bewegung + Atemregelmäßigkeit lässt sich nur „ruhig /
  unruhig / wach“ schätzen. Statt Pseudo-Genauigkeit zeigt die App diese drei Stufen.
- Mit Smartwatch-Daten (Health Connect) können deren Phasen übernommen werden.

### 2.7 Weitere Auswertungen ✅
- **Umgebungslärm:** Lautstärkeverlauf, laute Störereignisse (Verkehr, Hund, Partner) mit
  Zeitpunkt – „3 Störungen > 50 dB, eine davon mit anschließendem Aufwachen“.
- **Husten, Sprechen im Schlaf:** Zählung über YAMNet-Klassen.
- **Regelmäßigkeit:** Abweichung der Zubett-/Aufstehzeiten über die Woche („sozialer Jetlag“).
- **Aufwachqualität:** Schlummer-Anzahl und Zeit bis zur gelösten Aufgabe aus den
  vorhandenen Weckerdaten, korreliert mit Schlafdauer/Schnarchen.
- **Schlafwert (0–100):** gewichtete Kombination aus Dauer, Effizienz, Unruhe, Wachphasen,
  Regelmäßigkeit; Schnarchen/Apnoe-Verdacht separat ausgewiesen, nicht versteckt im Score.

### 2.8 Passt perfekt zum Wecker: Smart Wake ✅
- Optional pro Wecker ein **Weckfenster** (z. B. 20 min vor der Weckzeit). Erkennt die Analyse
  in diesem Fenster eine unruhige/leichte Phase (Bewegung, Atmung unregelmäßig), klingelt der
  Wecker früher – spätestens aber zur eingestellten Zeit. Die Aufgaben-Logik bleibt unverändert.

---

## 3. Technische Umsetzung (Android)

### 3.1 Aufzeichnung
- Neuer `SleepTrackingService` als Foreground-Service vom Typ **`microphone`**
  (Berechtigungen `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`).
- **Einschränkung von Android:** Ein Mikrofon-Service darf nicht aus dem Hintergrund gestartet
  werden. Start daher per Button **„Schlaf aufzeichnen“** in der App (oder Schnellkachel /
  Benachrichtigung „Gute Nacht?“, wenn das Handy abends geladen wird). Ein vollautomatischer
  Start ist nicht erlaubt.
- **Stopp:** automatisch, sobald der Wecker per Aufgabe ausgeschaltet wird (Hook in
  `AlarmService`), sonst manuell.
- `AudioRecord` mit 16 kHz mono PCM; Verarbeitung im Stream, **keine Dauer-Aufnahme auf
  Speicher**. Nur bei Opt-in werden einzelne Ereignisse (z. B. 10 s um einen Apnoe-Verdacht)
  als komprimierte Clips gespeichert.
- Akku: ca. 3–8 % pro Nacht ohne Modell, etwas mehr mit YAMNet (läuft nur bei Geräusch).
  Hinweis „Handy ans Ladegerät“ beim Start.

### 3.2 Verarbeitungs-Pipeline
```
AudioRecord (16 kHz)
  └─ Frames 32 ms → RMS/dB, Bandenergie (FFT), Zero-Crossing-Rate
       └─ 1-s-Fenster: Geräusch ja/nein (adaptive Schwelle über Grundrauschen)
            ├─ Periodizität (Autokorrelation 2–8 s) → Atmung/Schnarchen
            ├─ [Phase 2] YAMNet auf 0,96-s-Fenstern, nur bei Geräusch
            └─ Ereignis-Detektoren: Schnarchen, Pause ≥ 10 s, Luftschnappen,
               Bewegung, Husten, Sprechen, Lärm
  └─ Minuten-Zusammenfassung → SleepSession
Zusatzsignale: Bildschirm an/aus, Licht, Beschleunigung (Matratzen-Modus)
```
Die DSP-Teile (dB, Band-Energie, Periodizität, Ereignislogik) werden als reine Kotlin-Klassen
ohne Android-Abhängigkeit geschrieben → per Unit-Test mit synthetischen Signalen prüfbar
(analog zu `MathProblemsTest`).

### 3.3 Datenmodell
Gespeichert wird wie bisher per `JsonFile`, eine Datei pro Nacht (`sleep/2026-10-26.json`):
```kotlin
data class SleepSession(
    val start: Long, val end: Long,
    val minutes: List<SleepMinute>,   // ~480 Einträge pro Nacht
    val events: List<SleepEvent>,     // Schnarch-Episoden, Apnoe-Verdacht, Lärm, Wachphasen …
    val alarmId: Int?, val snoozeCount: Int, val dismissDurationSec: Int?,
)
data class SleepMinute(val avgDb: Float, val maxDb: Float, val snoreSec: Int,
                       val movement: Int, val state: RestState /* RUHIG, UNRUHIG, WACH */)
data class SleepEvent(val type: EventType, val start: Long, val durationMs: Long,
                      val peakDb: Float, val clipFile: String?)
```
~30–50 KB pro Nacht als JSON – unkritisch. Ältere Nächte werden auf Tageswerte verdichtet.

### 3.4 Oberfläche
- Startseite: Button „Schlaf aufzeichnen“, laufend: Uhrzeit + „Aufzeichnung läuft“ (dunkel,
  minimal hell).
- **Morgenbericht** nach dem Ausschalten des Weckers: Schlafwert, Dauer, Schnarchzeit,
  Apnoe-Verdacht/h, Zeitleiste (ruhig/unruhig/wach + Schnarchen + Ereignisse), Hörproben.
- **Verlauf:** Wochen-/Monatstrends (Dauer, Schnarchen, Ereignisse/h, Regelmäßigkeit).
- Einstellungen: Mikrofon-Empfindlichkeit, Hörproben an/aus + Aufbewahrung, Matratzen-Modus,
  Smart-Wake-Fenster, Health-Connect-Verknüpfung, „Alle Schlafdaten löschen“.

### 3.5 Datenschutz
- Verarbeitung ausschließlich auf dem Gerät, keine Internet-Berechtigung nötig.
- Standard: **keine Audiospeicherung**, nur abgeleitete Zahlen.
- Hörproben nur mit ausdrücklicher Zustimmung, automatische Löschung, Export nur manuell.

---

## 4. Umsetzungsphasen

| Phase | Inhalt | Ergebnis |
|---|---|---|
| **1 – MVP** | Mikrofon-Service mit manuellem Start, automatischer Stopp beim Wecker, dB-Verlauf, heuristische Schnarch- und Bewegungserkennung, Bildschirm-/Licht-Ereignisse, Morgenbericht mit Dauer, Schnarchminuten, Lärmereignissen, Zeitleiste | Erste nutzbare Analyse |
| **2 – Atmung** | YAMNet-Klassifikation, Apnoe-Verdacht (Pause ≥ 10 s + Luftschnappen), Atemfrequenz bei hörbarer Atmung, Husten/Sprechen, Hörproben mit Opt-in | Schnarch-/Apnoe-Auswertung |
| **3 – Qualität & Komfort** | Ruhig/unruhig/wach-Schätzung, Schlafwert, Trends, Smart-Wake-Fenster, Matratzen-Modus | Vollständige Schlafanalyse |
| **4 – optional** | Health Connect (Herzfrequenz, SpO₂, Uhr-Schlafphasen), Sonar-Atemerkennung (experimentell) | Höhere Genauigkeit |

## 5. Validierung
- Unit-Tests der DSP-Bausteine mit synthetischen Signalen (rhythmische Rausch-Bursts =
  Schnarchen, eingefügte 15-s-Pausen = Apnoe, Einzelknacken = Bewegung).
- Ein paar eigene Testnächte mit Hörproben-Opt-in, Ereignisse manuell nachhören und
  Trefferquote prüfen; Schwellen anpassen.
- Wer eine Smartwatch hat: Vergleich der Schlafzeiten/Wachphasen mit deren Daten.

## 6. Entscheidungen

1. **Aufzeichnung:** drei Optionen – *Aus*, *Bei Wecker* (nur wenn in den nächsten 16 Std. ein
   Wecker klingelt), *Immer*. Start zu einer einstellbaren Uhrzeit, Ende mit dem Wecker bzw.
   ohne Wecker zu einer einstellbaren Endzeit.
2. **Hörproben:** standardmäßig **an**, werden nach **24 Stunden** automatisch gelöscht.
3. **Keine Smartwatch** → Health Connect entfällt vorerst.
4. **Smart Wake:** pro Wecker wählbar (Aus / 10 / 20 / 30 Min).

## 7. Umsetzungsstand

Umgesetzt (Phase 1 + Teile von 2 und 3):

- Automatischer Start zur Startzeit über einen kurzen Bildschirm „Schlafaufzeichnung läuft“
  (Android lässt das Mikrofon nur aus dem Vordergrund starten), dort „Heute nicht aufzeichnen“.
  Ohne „Über anderen Apps einblenden“ kommt stattdessen eine Benachrichtigung zum Antippen.
- Manueller Start/Stopp im Bildschirm „Schlafanalyse“ (Mond-Symbol in der Weckerliste).
- Analyse ohne KI-Modell, rein heuristisch (`sleep/analysis`): Grundrauschen, Geräusch-Bursts,
  Rhythmus-Erkennung → Schnarchen / Atmen / Bewegung / Lärm, Atemaussetzer-Verdacht
  (Pause ≥ 10 s zwischen regelmäßigem Atmen, Luftschnappen), Atemfrequenz, ruhig/unruhig/wach
  pro Minute, Einschlafzeit, Wachphasen, Effizienz, Schlafwert, Störgeräusche.
- Bildschirm an/aus als Wach-Signal; Weckerklingeln wird aus der Analyse ausgeblendet.
- Morgenbericht mit Zeitleiste, Lautstärkeverlauf, Hörproben zum Abspielen; Verlauf der letzten Nächte.
- Absicherung: Zwischenstand alle 10 Min., nach einem Absturz wird die Nacht gekürzt gespeichert.

Zweiter Schritt (umgesetzt):

- **KI-Geräuscherkennung mit YAMNet** (Google, AudioSet, 521 Klassen, Apache-2.0, TensorFlow
  Lite, offline, 4 MB in `assets/`). Läuft nur auf ~1-s-Fenstern, in denen etwas zu hören war.
  Erkennt Schnarchen, Atmen, Luftschnappen, Husten, Sprechen und Musik/TV. Die Labels haben
  Vorrang vor der Heuristik: Husten und Sprechen werden gezählt (und als unruhig gewertet),
  Schnarchen ohne klaren Rhythmus wird erkannt, Sprache/TV mit Rhythmus gilt nicht mehr als
  Atmung, Luftschnappen markiert Atemaussetzer. Ohne Modell läuft die Heuristik wie bisher.
- Hörproben zusätzlich für **Husten** und **Sprechen im Schlaf**.
- **Lichtsensor**: „Licht aus“ als Zubettgehzeit; Minuten mit Licht am Abend gelten als wach.
- **Bewegungssensor**: Liegt das Handy auf der Matratze, zählen Erschütterungen als Bewegung
  (auch für Smart Wake). Auf dem Nachttisch misst er nichts und stört nicht – keine Einstellung nötig.

Bewusst weggelassen: **Sonar** (Ultraschall über den Lautsprecher die ganze Nacht). Es hängt
stark vom Handy ab, manche Geräte filtern Ultraschall, Kinder und Haustiere können ihn hören,
und Lautsprecher plus Mikrofon gleichzeitig kosten deutlich Akku. Die Schwellen der Heuristik
müssen weiterhin mit echten Nächten nachjustiert werden.
