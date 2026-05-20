# Connect IQ Store Listing

Use this content when submitting `FtmsDataField.iq` in the Garmin Connect IQ developer portal.

## Binary

- App type: Data Field
- Upload file: `app/garminDataField/build/outputs/FtmsDataField.iq`
- Public name: FTMS Bridge Field
- Version: 1.0.0
- Category: Fitness
- Languages: English, German

## English

### Short Description

Bridge FTMS treadmill data from Android to Garmin FIT developer fields.

### Full Description

FTMS Bridge Field is a Garmin Connect IQ data field for recording treadmill and fitness machine metrics that come from the FTMS Bridge Android companion app.

The Android app connects to Bluetooth FTMS fitness equipment, reads live workout data, and forwards cleaned samples to this Garmin data field through the Garmin companion channel. The data field writes the received values as Connect IQ FIT developer fields so they can appear in the recorded activity.

Recorded fields include ascent, speed, distance, cadence or step rate, incline, power, heart rate, elapsed time, resistance, and machine type when the connected equipment provides those values.

The value shown live on the watch is configurable in the Connect IQ app settings. Choose the primary watch metric from ascent, speed, distance, power, cadence or step rate, incline, heart rate, elapsed time, or resistance.

Important: this data field requires the Android FTMS Bridge app. Install the Android app from the project release page:
https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0

Basic setup:
1. Install this Connect IQ data field on the Garmin watch.
2. Install the Android FTMS Bridge app.
3. Add the data field to an activity data screen on the watch.
4. Optionally choose the primary watch metric in Connect IQ settings.
5. Start the activity on the watch.
6. Start the treadmill or fitness machine.
7. Start the bridge in the Android app and select the fitness machine and Garmin watch.

This app does not control treadmill speed or incline. It only reads available FTMS data and records it. Native Garmin workout fields are not overwritten.

### What is New

Initial Connect IQ Store release for the FTMS Bridge data field.

## German

### Kurzbeschreibung

Überträgt FTMS-Laufbanddaten von Android in Garmin FIT Developer Fields.

### Beschreibung

FTMS Bridge Field ist ein Garmin-Connect-IQ-Datenfeld zum Aufzeichnen von Laufband- und Fitnessgerätewerten, die von der Android-Begleit-App FTMS Bridge kommen.

Die Android-App verbindet sich mit Bluetooth-FTMS-Fitnessgeräten, liest Live-Trainingsdaten und sendet bereinigte Werte über den Garmin-Companion-Kanal an dieses Datenfeld. Das Datenfeld schreibt die empfangenen Werte als Connect-IQ-FIT-Developer-Fields in die Aktivität.

Aufgezeichnet werden je nach Fitnessgerät Höhenmeter, Geschwindigkeit, Distanz, Kadenz oder Schrittfrequenz, Steigung, Leistung, Herzfrequenz, verstrichene Zeit, Widerstand und Gerätetyp.

Der live auf der Uhr angezeigte Wert ist in den Connect-IQ-Einstellungen konfigurierbar. Du kannst Höhenmeter, Geschwindigkeit, Distanz, Leistung, Kadenz oder Schrittfrequenz, Steigung, Herzfrequenz, verstrichene Zeit oder Widerstand als primären Uhrenwert wählen.

Wichtig: Dieses Datenfeld benötigt die Android-App FTMS Bridge. Installiere die Android-App über die Projekt-Release-Seite:
https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0

Einrichtung:
1. Installiere dieses Connect-IQ-Datenfeld auf der Garmin-Uhr.
2. Installiere die Android-App FTMS Bridge.
3. Füge das Datenfeld in einer Aktivität auf der Uhr zu einer Datenseite hinzu.
4. Wähle optional den primären Uhrenwert in den Connect-IQ-Einstellungen.
5. Starte die Aktivität auf der Uhr.
6. Starte das Laufband oder Fitnessgerät.
7. Starte die Bridge in der Android-App und wähle Fitnessgerät und Garmin-Uhr aus.

Diese App steuert weder Geschwindigkeit noch Steigung des Laufbands. Sie liest nur verfügbare FTMS-Daten und zeichnet sie auf. Native Garmin-Trainingsfelder werden nicht überschrieben.

### Neuigkeiten

Erste Connect-IQ-Store-Version des FTMS-Bridge-Datenfelds.

## Reviewer Notes

FTMS Bridge Field is part of a two-component bridge:

- Garmin Connect IQ data field: receives samples via `Toybox.Communications` and writes Connect IQ FIT developer fields.
- Android companion app: connects to FTMS fitness equipment over BLE and forwards live samples to the watch through Garmin Connect Mobile.

The data field requests `Communications`, `BluetoothLowEnergy`, and `FitContributor`. The direct BLE code path remains present for diagnostics and future compatibility, but the tested production path is the Android phone bridge. The app exposes one Connect IQ setting, `PrimaryMetric`, which selects the live metric displayed on the watch. It still records all available FIT developer fields. The app does not write treadmill control commands and does not change treadmill speed or incline.

Android app release:
https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0

## Submission Checklist

- Export a fresh `.iq` after any source or manifest change.
- For sideload testing, build `app/garminDataField/build/outputs/FtmsBridgeField-fr970.prg`.
- Upload `app/garminDataField/build/outputs/FtmsDataField.iq`.
- Upload a 500x500 store icon from `docs/store-assets/connect-iq/icon-500.png`.
- Add at least one watch screenshot.
- Paste English and German descriptions.
- Add reviewer notes.
- Submit for Garmin review.
