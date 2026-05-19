# FTMS Höhenmeter Data Field für Garmin Connect IQ

Projektvorlage für ein Garmin Connect IQ **Data Field**, das sich per BLE mit FTMS-Fitnessgeräten verbindet und Werte in eigene FIT Developer Fields schreibt.

Unterstützte FTMS-Quellen in dieser Vorlage:

- Indoor Bike Data `0x2AD2`
- Treadmill Data `0x2ACD`
- Cross Trainer / Elliptical Data `0x2ACE`

Primärer Anzeige-Wert auf der Uhr: **Höhenmeter gelaufen**.

## Download v1.0.0

Version 1.0.0 stellt die Android-Bridge als debug-signierte APK bereit:

- [ftms-bridge-android-v1.0.0-debug.apk](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0)
- [SHA256SUMS.txt](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0)

Die APK ist für Sideloading gedacht. Android muss die Installation aus unbekannten Quellen für den verwendeten Browser oder Dateimanager erlauben.

Wichtig: Dieses Release enthält nur die Android-App. Garmin-/Connect-IQ-Artefakte werden in v1.0.0 noch nicht als Download veröffentlicht; das Datenfeld kann weiterhin aus dem Quellcode gebaut und sideloaded werden.

Zusätzlich werden als FIT Developer Fields geschrieben:

- `hm_gelaufen`
- `hm_gelaufen_summe`
- `hm_quelle`
- `ftms_power`
- `ftms_speed`
- `ftms_distance`
- `ftms_cad_step`
- `ftms_incline`
- `ftms_machine`

`hm_quelle` bedeutet:

- `0` = keine Quelle
- `1` = direktes FTMS Positive Elevation Gain Feld
- `2` = berechnet aus Distanzdelta × positiver Steigung

## Zielgeräte

Das Manifest enthält diese Forerunner-Targets:

- `fr970` — Forerunner 970
- `fr57047mm` — Forerunner 570 47 mm
- `fr57042mm` — Forerunner 570 42 mm
- `fr170`
- `fr170m`
- `fr70`
- `fr965`
- `fr955`

Für eure neueste Forerunner-Uhr ist der wahrscheinlichste Build-Target aktuell `fr970`.

## Projekt öffnen

1. Garmin Connect IQ SDK installieren.
2. VS Code Extension **Monkey C** installieren.
3. Ordner öffnen.
4. In VS Code als Device `fr970` auswählen.
5. Bauen oder starten.

CLI-Beispiel:

```bash
DEVICE=fr970 npx nx build garminDataField
```

## Data Field auf der Uhr nutzen

1. Auf der Uhr eine Aktivität öffnen, zum Beispiel:
   - Laufband
   - Indoor Bike
   - Ellipsentrainer / Cardio / Fitnessgerät
2. Datenfelder bearbeiten.
3. Connect IQ Feld auswählen.
4. **FTMS Höhenmeter** hinzufügen.
5. Aktivität starten.
6. FTMS-Gerät einschalten.
7. Das Feld scannt automatisch nach Geräten mit FTMS Service `0x1826`.

## Wichtige Einschränkung

Diese Vorlage schreibt **custom FIT Developer Fields**. Sie überschreibt nicht Garmins native Distanz, Geschwindigkeit, Power oder native Höhenmeter. In Garmin Connect erscheinen die Werte im Connect-IQ-/IQ-Datenbereich beziehungsweise als IQ-Charts, abhängig vom Gerät und von Garmin Connect.

## Architektur

```text
FTMS Fitnessgerät
  ↓ BLE FTMS Service 0x1826
FtmsBleClient
  ↓ Notifications
FtmsParser
  ↓ FtmsSample
AscentTracker
  ↓ Höhenmeter direkt oder berechnet
FtmsFitWriter
  ↓ FIT Developer Fields
Garmin Activity FIT File
```

## Relevante Dateien

```text
app/garminDataField/source/FtmsBleClient.mc       BLE Scan, Pairing, Subscribe
app/garminDataField/source/FtmsParser.mc          FTMS Parser für Bike/Treadmill/Elliptical
app/garminDataField/source/AscentTracker.mc       Höhenmeter-Logik
app/garminDataField/source/FtmsFitWriter.mc       FIT Developer Fields
app/garminDataField/source/FtmsDataFieldView.mc   SimpleDataField Anzeige + 1-Hz-Schreiben
app/garminDataField/resources/fitcontributions/fitcontributions.xml
                              Garmin-Connect-Anzeige für IQ-Felder
app/garminDataField/manifest.xml App-Typ, Geräte, Permissions
```

## Nächste sinnvolle Erweiterungen

- App Settings für Zielgerät/Name statt „erstes FTMS-Gerät“.
- Geräteauswahl für Fitnessstudios mit mehreren FTMS-Geräten.
- Optionales Feld für Resistance.
- Separates Power Data Field, falls ihr Power statt Höhenmeter primär anzeigen wollt.
- Debug-Screen oder Companion-Watch-App für Rohpakete.
- Kompatibilitätsmodus für Geräte, die FTMS nicht ganz sauber implementieren.

## IntelliJ IDEA

You can use IntelliJ IDEA as the editor. See `INTELLIJ.md` for setup and the included build scripts in `app/garminDataField/scripts/`.
