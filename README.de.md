# FTMS Datafield

[English](README.md)

FTMS Datafield verbindet Bluetooth-FTMS-Fitnessgeräte mit Garmin-Connect-IQ-Datenfeldern.

Das Projekt besteht aus zwei Teilen:

- einer Android-Bridge-App, die sich mit FTMS-Geräten verbindet, Sessions lokal speichert und Live-Werte über den Garmin-Connect-IQ-Companion-Kanal an die Uhr sendet
- einem Garmin-Connect-IQ-Datenfeld als Quellcodeprojekt, das Bridge-Werte empfängt und eigene FIT Developer Fields schreibt

Die Bridge ist als reine Daten-Bridge gedacht. Sie schreibt nicht auf den FTMS Control Point `0x2AD9` und sendet keine Vendor-Steuerbefehle an das Laufband.

## Download v1.0.0

Version 1.0.0 stellt die Android-Bridge als debug-signierte APK bereit:

- [ftms-bridge-android-v1.0.0-debug.apk](https://github.com/oberstrike/ftms-datafield/releases/download/v1.0.0/ftms-bridge-android-v1.0.0-debug.apk)
- [SHA256SUMS.txt](https://github.com/oberstrike/ftms-datafield/releases/download/v1.0.0/SHA256SUMS.txt)
- [Release-Seite](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0)

Wichtig: v1.0.0 veröffentlicht nur die Android-APK. Garmin-Uhr-Artefakte werden noch nicht als Download veröffentlicht; das Connect-IQ-Datenfeld kann aus dem Quellcode gebaut und sideloaded werden.

## Android-App installieren

1. Öffne die [v1.0.0 Release-Seite](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0) auf deinem Android-Telefon.
2. Lade `ftms-bridge-android-v1.0.0-debug.apk` herunter.
3. Falls Android die APK blockiert, erlaube die Installation aus unbekannten Quellen für den verwendeten Browser oder Dateimanager.
4. Installiere die APK.
5. Öffne `FTMS Bridge`.
6. Erteile die angeforderten Bluetooth-Berechtigungen.

Android kann warnen, dass dies eine Debug-App oder eine App aus unbekannter Quelle ist. Das ist bei v1.0.0 erwartbar, weil die APK noch nicht über Google Play verteilt wird.

## Bridge verwenden

Du brauchst sowohl die Android-App als auch das Garmin-Connect-IQ-Datenfeld auf der Uhr.

1. Installiere oder sideloade das Connect-IQ-Datenfeld auf der Garmin-Uhr.
2. Öffne auf der Uhr eine Laufband- oder Indoor-Aktivität.
3. Bearbeite die Datenseiten der Aktivität und füge das FTMS-Datenfeld hinzu.
4. Starte die Aktivität auf der Uhr.
5. Starte das Laufband.
6. Öffne die Android-App.
7. Wähle das FTMS-Laufband und die Garmin-Uhr aus.
8. Tippe auf `Start`.
9. Lass die Android-App während der Session laufen.
10. Tippe in der Android-App auf `Stopp`, wenn du fertig bist.

Die App speichert Sessions lokal und zeigt Verlauf, Diagramme und Diagnose-Logs. Die Uhr schreibt empfangene Werte als Connect-IQ-FIT-Developer-Fields.

## Unterstützte FTMS-Daten

Der Parser unterstützt diese standardisierten FTMS-Characteristics:

- Indoor Bike Data `0x2AD2`
- Treadmill Data `0x2ACD`
- Cross Trainer / Elliptical Data `0x2ACE`

Die aktuelle Android-Bridge wurde vor allem mit dem Darwin TM70 Touch / `FS-BC11B7` Laufband getestet.

## Garmin-Datenfelder

Das Connect-IQ-Projekt schreibt eigene FIT Developer Fields, zum Beispiel:

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

- `0`: keine Quelle
- `1`: direktes FTMS Positive Elevation Gain Feld
- `2`: berechnet aus Distanzdelta mal positiver Steigung

Das Datenfeld überschreibt nicht Garmins native Distanz, Geschwindigkeit, Power oder Höhenmeter. Garmin Connect zeigt diese Werte je nach Gerät und Garmin-Connect-Unterstützung als Connect-IQ-Felder oder Charts.

## Fehlerbehebung

### Android findet das Laufband nicht

- Stelle sicher, dass Bluetooth und die Bluetooth-/Standortberechtigungen aktiv sind.
- Trenne Zwift, Kinomap, FitShow, nRF Connect oder andere Apps zuerst vom Laufband.
- Starte das Laufband; manche Geräte werben oder streamen zuverlässiger, wenn das Band läuft.
- Halte das Telefon nah an die Laufbandkonsole.

### Garmin-Uhr erscheint nicht

- Stelle sicher, dass Garmin Connect installiert und mit der Uhr gekoppelt ist.
- Öffne Garmin Connect einmal, bevor du in FTMS Bridge suchst.
- Prüfe, ob das Connect-IQ-Datenfeld auf der Uhr installiert ist.
- Aktualisiere die Garmin-Geräte in der Android-App.

### Es erscheinen keine Live-Daten

- Starte die Aktivität auf der Uhr, bevor du die Bridge startest.
- Prüfe, ob die Android-App das Laufband als verbunden anzeigt.
- Öffne den Logs-Bildschirm in der Android-App.
- Stoppe und starte die Bridge neu, nachdem andere BLE-Apps vom Laufband getrennt wurden.

### Distanz oder Kadenz wirken falsch

Manche Laufbänder melden FTMS-Distanz nur grob oder verzögert. Die Bridge glättet die Distanz aus Geschwindigkeit/Zeit zwischen den rohen FTMS-Updates. Kadenz wird nur angezeigt, wenn das Laufband oder ein zusätzlicher unterstützter Sensor Kadenz-/Step-Rate-Daten liefert.

## Entwickler-Setup

Voraussetzungen:

- JDK 17
- Android SDK
- Node.js und npm
- Garmin Connect IQ SDK für Garmin-Datenfeld-Builds
- Garmin Connect IQ Developer Key für `.prg`- oder `.iq`-Builds

Repository klonen und Abhängigkeiten installieren:

```bash
git clone https://github.com/oberstrike/ftms-datafield.git
cd ftms-datafield
npm install
```

Android-/Shared-Checks ausführen:

```bash
./gradlew :app:shared:jvmTest \
  :app:androidApp:testDebugUnitTest \
  :app:androidApp:lintDebug \
  :app:androidApp:assembleDebug
```

Die Debug-APK wird hier erstellt:

```text
app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Garmin-Datenfeld bauen

Installiere das Garmin Connect IQ SDK und setze deinen Developer Key:

```bash
export GARMIN_DEVELOPER_KEY="$HOME/garmin-dev/ciq.key"
```

Alle Forerunner-970-Varianten bauen:

```bash
DEVICE=fr970 VARIANT=all ./app/garminDataField/scripts/build.sh
```

Build-Ausgabe:

```text
app/garminDataField/build/outputs/*.prg
```

Kopiere die `.prg`-Dateien auf die Uhr:

```text
GARMIN/APPS/
```

Weitere Hinweise zu IntelliJ und dem Garmin SDK stehen in [INTELLIJ.md](INTELLIJ.md).

## Repository-Struktur

```text
app/androidApp/       Android-Bridge-App
app/garminDataField/  Garmin-Connect-IQ-Datenfeld
app/shared/           Kotlin-Multiplatform-FTMS-Parser, Glättung, Storage-Modelle
docs/                 Design- und Validierungsnotizen
tools/ftmsProbe/      Linux-FTMS-Validierungsprobe
```

Nützliche technische Referenzen:

- [Android bridge design](docs/android-bridge-design.md)
- [FTMS validation guide](docs/ftms-validation.md)
- [FS-BC11B7 validation result](docs/ftms-validation-result.md)
- [Changelog](CHANGELOG.md)
