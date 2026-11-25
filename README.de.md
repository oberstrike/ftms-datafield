# FTMS Datafield

[English](README.md)

FTMS Datafield verbindet Bluetooth-FTMS-Fitnessgeräte mit Garmin-Connect-IQ-Datenfeldern.

Das Projekt besteht aus zwei Teilen:

- einer Android-Bridge-App, die sich mit FTMS-Geräten verbindet, Sessions lokal speichert und Live-Werte über den Garmin-Connect-IQ-Companion-Kanal an die Uhr sendet
- einem Garmin-Connect-IQ-Datenfeld als Quellcodeprojekt, das Bridge-Werte empfängt und eigene FIT Developer Fields schreibt

Die Bridge liest das Gerät nur aus, mit einer Ausnahme: Beim Gerätetyp „Indoor Cycling“ kann sie auf deinen Wunsch eine Zielleistung über den FTMS Control Point `0x2AD9` setzen, und setzt die letzte beim Start einer Session erneut. An Laufbänder und Crosstrainer wird nie geschrieben, und Vendor-Steuerbefehle werden nicht gesendet.

## Download v1.0.0

Version 1.0.0 stellt die Android-Bridge als debug-signierte APK bereit:

- [ftms-bridge-android-v1.0.0-debug.apk](https://github.com/oberstrike/ftms-datafield/releases/download/v1.0.0/ftms-bridge-android-v1.0.0-debug.apk)
- [SHA256SUMS.txt](https://github.com/oberstrike/ftms-datafield/releases/download/v1.0.0/SHA256SUMS.txt)
- [Release-Seite](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0)

Wichtig: v1.0.0 veröffentlicht aktuell die Android-APK. Das Garmin-Connect-IQ-Datenfeld wird für die Connect-IQ-Store-Prüfung vorbereitet; bis zur Freigabe kann es aus dem Quellcode gebaut und sideloaded werden.

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

### Gerätetyp

Im Tab `Geräte` wählst du, was das ausgewählte Gerät ist: `Laufband`, `Indoor Cycling` oder `Crosstrainer`. Nennt ein Gerät seinen Typ beim Advertising, ist er bei der Auswahl vorbelegt; ändern kannst du ihn, solange die Bridge gestoppt ist.

- Während einer Session beginnt das Dashboard mit den Diagrammen, jedes mit seinem aktuellen Wert darüber; darunter folgen die Live-Werte, die Karte `Quellen` und der Status. Ohne Session zeigt es den Status und die letzte Session.
- Start, Pause und Stopp sitzen in einer Leiste fest über der Navigation, zusammen mit dem Zustand der Bridge und der Zeit der laufenden Session. Im Dashboard ist die Leiste immer da und nennt vor dem Start Gerät und Uhr sowie das, was einen Start verhindert; in den anderen Tabs erscheint sie, solange eine Session läuft.
- Das Dashboard hat für jeden Typ eine eigene Auswahl an Live-Werten und Diagrammen. Ein Indoor Bike beginnt mit Leistung, Kadenz, Geschwindigkeit, Herzfrequenz, Distanz und Widerstand, ohne Pace, Steigung und Höhenmeter. Unter `Einstellungen > Dashboard` passt du die Auswahl je Typ an.
- Angeboten und gezeigt wird nur, was zum Typ gehört: keine Pace auf dem Bike, kein Widerstand auf dem Laufband, und die Kadenz heißt auf dem Laufband `Schrittfrequenz` (spm), sonst `Kadenz` (rpm).
- Während einer Session lässt das Dashboard weg, was Gerät und Uhr bisher nicht geliefert haben: keine Kachel, kein Diagramm und keine Zeile unter `Quellen` für eine Herzfrequenz, die niemand misst. Sie erscheint, sobald der erste Wert ankommt.
- Jedes Diagramm hat eine Werteachse mit runden Zahlen, darunter die Zeit seit Beginn der Session und darüber den aktuellen Wert, dazu Durchschnitt und Maximum, wo sie etwas aussagen. Lege einen Finger auf ein Diagramm, um den Wert zu diesem Zeitpunkt abzulesen. Die Distanz gibt es nur als Live-Wert: Als Diagramm wäre sie eine Linie, die nur steigt.
- Jede Session wird mit ihrem Typ gespeichert. Der Verlauf zeigt den Typ, lässt unpassende Werte weg und lässt sich nach Typ filtern, sobald mehr als ein Typ gespeichert ist. Sessions aus älteren Versionen erhalten ihren Typ aus den enthaltenen Daten.
- Die Leistungsschätzung für das Laufband gilt nur für Laufbänder.
- Sendet das Gerät Daten eines anderen Typs als gewählt, weist das Dashboard darauf hin.

### Zielleistung für ein Indoor Bike

Beim Typ `Indoor Cycling` zeigt das Dashboard die Karte `Zielleistung`, solange das Bike verbunden ist:

- Die Karte steht direkt unter dem Leistungsdiagramm, dessen aktueller Wert die vom Bike gemessene Leistung ist. Ohne Leistungsdiagramm zeigt die Karte die gemessene Leistung selbst, neben der Zielleistung.
- Tippe auf die Zielleistung, um sie als Zahl einzugeben. Bestätigen lässt sich nur ein Wert in dem Bereich, den das Bike meldet. Gesendet wird wattgenau, auch an ein Bike, das Schritte von etwa 5 W meldet: Ein solches Bike hält womöglich trotzdem 168 W (ein Wattbike Proton tut es). Erst wenn das Bike einen Wert zwischen seinen Schritten ablehnt, hält sich die App von da an an diese Schritte.
- Die Knöpfe ändern die Zielleistung in zwei Schrittweiten: 1 W (dem Schritt des Bikes, sobald es einen Wert zwischen seinen Schritten abgelehnt hat), und der Schrittweite aus den Einstellungen. Sind beide gleich, gibt es nur ein Knopfpaar. Ohne Zielleistung beginnt der erste Schritt bei der Leistung, die das Bike gerade misst.
- Tippe auf eine Schnellwahl, um diese Zielleistung zu setzen.
- Das Leistungsdiagramm im Dashboard zeichnet die Zielleistung als dünne Stufenlinie neben der gemessenen Leistung.
- Die Karte zeigt, ob das Bike die Zielleistung bestätigt oder abgelehnt hat, und welchen Bereich das Bike meldet.
- Wird eine pausierte Session fortgesetzt, sendet die App ihre Zielleistung erneut. So gilt sie nach der Pause wieder, auch wenn das Bike sie zwischendurch aufgegeben hat.
- Eine anderswo eingestellte Zielleistung wird übernommen, sofern das Bike sie meldet (FTMS Fitness Machine Status, „Target Power Changed“): am Bike selbst oder von einer Uhr, die das Bike steuert. Die Karte zeigt dann, dass das Bike sie gemeldet hat, und die App sendet von sich aus nichts mehr, auch nicht nach einer Pause, bis du in der App wieder ein Ziel setzt. Wer das Ziel setzt, hat das Sagen; eine Uhr, die das Bike steuert, überschreibt, was andere Apps senden. FTMS kennt keine Abfrage der aktuellen Zielleistung; eine Änderung, während die App nicht verbunden ist, bleibt deshalb unbemerkt.
- Nach einem Reconnect innerhalb der Session wird die Zielleistung der App erneut gesetzt. Eine neue Session beginnt ohne Zielleistung: Die App sendet nichts, bis du eine setzt, und zeigt eine an, sobald das Bike eine meldet. Die Zielleistung, die du zuletzt in der App auf diesem Bike gesetzt hast, steht als Vorschlag im Eingabefeld, wenn du auf die Zielleistung tippst.
- Soll die App keine Zielleistung mehr setzen, tippe auf die Zielleistung und wähle `Kein Ziel`. Die App sendet dann nichts mehr; das Bike behält, was es zuletzt bekommen hat, den Leistungsmodus verlässt du also über die Bedienung des Bikes.
- Unter `Einstellungen > Indoor Cycling` stehen die Schnellwahlen (bis zu 8, von 10 bis 2000 W; Standard sind 100, 150, 200 und 250 W) und die Schrittweite (5, 10 oder 25 W).

Das Bike muss den FTMS Control Point mit „Set Target Power“ anbieten. Fehlt er, steht das auf der Karte.

Das Bike hält die Zielleistung nach seiner eigenen Messung. Zeigst du stattdessen die Leistung von Pedalen an (siehe unten), kann dieser Wert um einige Prozent von der Zielleistung abweichen; die App gleicht das nicht aus.

### Werte der Uhr

Die Uhr sendet ihre Herzfrequenz an das Telefon und dazu die Leistung und Kadenz, die sie für die laufende Aktivität hat. Dazu gehören Sensoren, die mit der Uhr gekoppelt sind, etwa ein Brustgurt oder Leistungsmesser-Pedale. Die App verbindet sich nicht selbst mit diesen Sensoren.

- Während einer Session zeigt die Dashboard-Karte `Quellen` für Herzfrequenz, Leistung und Kadenz den Wert des Geräts und den der Uhr nebeneinander, soweit einer von beiden ihn liefert. Tippe auf einen, um ihn zu verwenden. Dieselbe Auswahl steht unter `Einstellungen > Datenquellen`, getrennt für jeden Gerätetyp.
- Voreingestellt ist die Herzfrequenz von der Uhr, Leistung und Kadenz vom Gerät.
- Der gewählte Wert gilt für die Anzeige, den Verlauf und die an die Uhr gesendeten Werte.
- Liefert die gewählte Quelle 5 Sekunden lang nichts, bleibt der Wert leer; die Herzfrequenz der Uhr bleibt 15 Sekunden stehen, weil sie sich langsam ändert und die Nachrichten der Uhr Sekunden brauchen können. Die App wechselt nicht von selbst zur anderen Quelle.
- Beide Werte werden mit der Session gespeichert. Enthält eine Session beide, zeigt der Verlauf den Durchschnitt je Quelle, die Abweichung und ein Diagramm mit beiden Linien.
- Die Leistungsschätzung für das Laufband zählt als Wert des Geräts.
- Die Werte der Uhr erscheinen, solange das Gerät Daten sendet.

Für Leistung und Kadenz brauchst du diese Version von Android-App und Datenfeld. Mit einem älteren Datenfeld sendet die Uhr nur die Herzfrequenz.

### Laufsensor für die Uhr

Auf dem Laufband schätzt die Uhr Tempo und Distanz ihrer Aktivität aus der Armbewegung, und ein Connect-IQ-Datenfeld darf diese Werte nicht ersetzen. Ist `Einstellungen > Bridge-Verhalten > Laufsensor für die Uhr` eingeschaltet, bietet das Telefon der Uhr die Werte des Laufbands stattdessen als Bluetooth-Laufsensor (Footpod) an. Eine Uhr übernimmt Tempo, Distanz und Schrittfrequenz von einem gekoppelten Laufsensor.

**Eine Uhr nutzt das Telefon nicht gleichzeitig als Sensor und über Garmin Connect.** Sie erkennt den Sensor als das Telefon, mit dem sie gekoppelt ist, und hält zu einem Gerät nur eine Verbindung. Bei einer Forerunner 970 verbindet sich der Sensor nicht, solange die Telefonverbindung der Uhr an ist, und solange der Sensor verbunden ist, erreichen sich Datenfeld und App nicht mehr. Dafür gibt es zwei Auswege.

#### Mit einem Relais auf einem Rechner: Sensor und Datenfeld zusammen

Ein Rechner dazwischen nimmt den Sensor des Telefons entgegen und bietet ihn der Uhr unter seiner eigenen Bluetooth-Adresse an. Die Uhr hat dann zwei verschiedene Geräte, und beide Verbindungen laufen gleichzeitig:

    Laufband --FTMS--> Telefon (Bridge-App) --Laufsensor--> Rechner --Laufsensor--> Uhr

Das Relais ist `tools/footPodRelay/foot_pod_relay.py`. Es braucht Linux mit BlueZ und einen Bluetooth-Adapter, der gleichzeitig Zentrale und Peripherie sein kann, und muss in Bluetooth-Reichweite von Telefon und Uhr laufen.

    python3 -m venv .venv-foot-pod-relay
    .venv-foot-pod-relay/bin/pip install -r tools/footPodRelay/requirements-foot-pod-relay.txt
    .venv-foot-pod-relay/bin/python tools/footPodRelay/foot_pod_relay.py

1. Relais starten. Es bietet einen Sensor namens `FTMS Bridge` an und wartet auf den Sensor des Telefons.
2. In der App die Einstellung einschalten und den Typ `Laufband` wählen.
3. Einmalig: An der Uhr einen neuen Sensor vom Typ Laufsensor hinzufügen (bei einer Forerunner unter `Sensoren und Zubehör`) und das Relais auswählen. In dessen Einstellungen Tempo und Distanz auf `Indoor` stellen, den Kalibrierungsfaktor auf 100 und die automatische Kalibrierung ausschalten. Sonst skaliert die Uhr die Werte des Laufbands. Einen Eintrag des Telefons selbst aus den Sensoren der Uhr entfernen, sonst verbindet sich die Uhr direkt mit dem Telefon.
4. Aktivität an der Uhr starten. Die Session startet (siehe „Mit der Uhr starten und stoppen"), das Telefon bietet seinen Sensor an, das Relais reicht ihn weiter.

Getestet mit einer Forerunner 970 (Firmware 19.23) und BlueZ 5.82. Beim ersten Versuch stürzte die Firmware der Uhr acht Sekunden nach dem Hinzufügen des Sensors ab, während bereits Messwerte flossen; spätere Versuche, bei denen sich die Uhr vor dem Start der Session verband, liefen ohne Problem. Die Ursache ist nicht bekannt.

#### Ohne Relais: Sensor statt Datenfeld

1. An der Uhr die Telefonverbindung ausschalten.
2. In der App die Einstellung einschalten, den Typ `Laufband` wählen und die Bridge starten. In diesem Modus startet die Bridge, ohne das Datenfeld zu erreichen. Die Statuskarte im Dashboard zeigt `Laufsensor: wartet auf die Uhr`.
3. Einmalig: Den Sensor an der Uhr hinzufügen wie oben beschrieben. Das Telefon erscheint mit einer Kennung aus seiner Bluetooth-Adresse, nicht mit einem Namen.
4. Die Statuskarte zeigt `mit der Uhr verbunden`. Laufband-Aktivität an der Uhr starten.
5. Nach dem Lauf die Bridge stoppen oder die Einstellung ausschalten und die Telefonverbindung der Uhr wieder einschalten.

In dieser Zeit bekommt das Datenfeld keine Werte (Steigung, Anstieg, die Connect-IQ-Felder), und die App bekommt weder Herzfrequenz noch Leistung oder Kadenz von der Uhr. Ihre eigene Session zeichnet die App trotzdem vollständig auf.

#### Was der Sensor sendet

- Das Telefon ist nur als Sensor sichtbar, solange die Bridge mit dem Typ `Laufband` läuft.
- Es sendet einmal je Sekunde Geschwindigkeit, Gesamtdistanz und Schrittfrequenz. Ist die Bridge pausiert, sendet es Stillstand; liefert das Laufband nichts, sendet es nichts.
- Die Schrittfrequenz ist die des Laufbands. Laufbänder mit dem FitShow-Protokoll melden über FTMS keine; für sie berechnet die App sie aus dem Schrittzähler in ihren herstellereigenen Paketen. Bietet ein Laufband beides nicht, sendet der Sensor 0, und die Uhr zeichnet das statt ihrer eigenen Schrittfrequenz auf. Damit stehen auch Werte auf 0, die die Uhr daraus ableitet, etwa ihre Laufleistung.
- Steigung und Anstieg haben in einem Laufsensor keinen Platz.
- Android braucht die Berechtigung, das Telefon für Geräte in der Nähe sichtbar zu machen. Sie gehört zur selben Gruppe wie die Bluetooth-Berechtigungen, die die App schon hat.

### Distanz an der Uhr korrigieren

Bleibst du stattdessen beim Datenfeld, behält die Aktivität die eigene Distanzschätzung der Uhr. Am Ende einer Laufband-Aktivität bietet eine Garmin-Uhr `Kalibrieren und speichern` an: Trage dort die Distanz ein, die die App für die Session zeigt (`Letzte Session` im Dashboard oder der Verlauf). Die Uhr speichert dann diese Distanz und verbessert damit ihre Schätzung für spätere Läufe.

### Mit der Uhr starten und stoppen

Mit `Einstellungen > Bridge-Verhalten > Aktivität der Uhr folgen` (standardmäßig an) brauchst du `Start` und `Stop` in der App nicht, also die Schritte 8 und 10: Die Session folgt der Aktivität, in der das Datenfeld läuft.

- Startest du die Aktivität an der Uhr, startet die Session. Öffnest du die App erst, während die Aktivität schon aufzeichnet, startet die Session innerhalb weniger Sekunden.
- Stoppst du die Aktivität an der Uhr, pausiert die Session; setzt du sie fort, läuft die Session weiter. Auto-Pause der Uhr pausiert die Session nicht.
- Speicherst oder verwirfst du die Aktivität, endet die Session.
- Beim Start der Aktivität muss die App auf dem Telefon geöffnet sein, außer sie wartet im Hintergrund (siehe unten). Laufband und Uhr müssen ausgewählt sein. Die Uhr braucht das Datenfeld dieser Version; ein älteres Datenfeld meldet die Aktivität nicht.
- Ohne Session auf dem Telefon verbindet sich das Datenfeld selbst mit dem Laufband. Startet die Session, gibt es die Verbindung an das Telefon ab und zeigt `PWAIT`, bis das Telefon Daten hat; das kann ein paar Sekunden dauern.
- `Start`, `Pause` und `Stop` in der App funktionieren weiter. Was du dort tust, bleibt bestehen, bis sich der Zustand der Aktivität an der Uhr wieder ändert.
- Läuft beim Start der Aktivität bereits eine Session, läuft sie einfach weiter. Soll die Session genau die Aktivität abdecken, lass `Automatisch starten` aus und überlass den Start der Uhr.

#### Im Hintergrund warten

Android stellt der App die Nachrichten der Uhr nur zu, solange die App zu sehen ist oder einen Vordergrunddienst betreibt. Mit `Einstellungen > Bridge-Verhalten > Im Hintergrund auf die Uhr warten` (standardmäßig aus) behält die App einen solchen Dienst, nachdem du sie einmal geöffnet hast. Die Session startet dann mit der Aktivität, ohne dass du die App erneut öffnest.

- Während sie wartet, zeigt die Benachrichtigung `FTMS Bridge wartet` das an. `Warten beenden` dort beendet es, bis du die App das nächste Mal öffnest.
- Während des Wartens sucht die App nicht nach dem Laufband und verbindet sich nicht damit. Sie verbindet sich, sobald die Aktivität startet.
- Nach einer Session wartet die App wieder, auf die nächste Aktivität.
- Das Warten endet, wenn das Telefon neu startet oder Android die App schließt; öffne die App einmal, um es wieder aufzunehmen. Telefone mit strenger Akkuverwaltung schließen die App unter Umständen nach einiger Zeit, wenn du sie dort nicht ausnimmst.
- Nötig sind die Bluetooth-Berechtigung sowie ein ausgewähltes Laufband und eine ausgewählte Uhr. Getestet im Emulator mit simulierter Aktivität, noch nicht auf einem Telefon mit Uhr.

### Automatisch stoppen

Mit `Einstellungen > Bridge-Verhalten > Automatisch stoppen` (standardmäßig an) endet eine Laufband-Session von selbst, sobald das Band 30 Sekunden stillsteht oder das Laufband 90 Sekunden nicht erreichbar ist. Die Session endet dann zu dem Zeitpunkt, an dem das Band stehen blieb; die Wartezeit zählt also nicht zur Session.

- Eine Session, deren Band sich noch nicht bewegt hat, bleibt bestehen. Du kannst sie also starten, bevor du aufs Band steigst.
- Solange die Uhr eine eigene Aktivität meldet, entscheidet die Uhr über das Ende, und eine Pause auf dem Band beendet die Session nicht. Hat sich die Uhr 20 Sekunden nicht gemeldet, beendet wieder der Stillstand des Bands die Session.
- Sessions auf Indoor Bike oder Crosstrainer enden nicht von selbst, weil Stillstand dort nur eine Pause ist. Sie enden, wenn die Aktivität an der Uhr gespeichert oder verworfen wird, oder mit `Stop` in der App.

### Distanz einer Session

Eine Session zählt Distanz und Anstieg ab ihrem eigenen Start. Was das Gerät beim Start der Session schon anzeigte, bleibt außen vor, ebenso die Strecke während einer Pause der Session. Wird das Gerät während einer Session zurückgesetzt, behält die Session das bereits Gezählte und zählt von dort weiter.

### Automatisch starten

Ist `Einstellungen > Bridge-Verhalten > Automatisch starten` eingeschaltet, entfällt Schritt 8: Solange die App geöffnet ist, startet die Bridge, sobald das Band des gewählten Laufbands läuft. Die Uhr muss bereits ausgewählt sein und das Datenfeld als installiert melden.

- Der automatische Start arbeitet nur, solange die App sichtbar ist. Bei gesperrtem Telefon oder App im Hintergrund startet die Bridge nicht.
- Während die App wartet, ist sie mit dem Laufband verbunden. Die meisten Laufbänder erlauben nur eine Verbindung, andere Apps können sich in dieser Zeit also nicht verbinden.
- Ein Indoor Bike oder Crosstrainer gilt als in Bewegung, sobald es Geschwindigkeit oder Kadenz meldet.
- Nach dem Ende einer Session wartet der automatische Start, bis das Band steht. Wird das Laufband bei laufendem Band ausgeschaltet, wartet er, bis es eine Minute aus war, höchstens aber 30 Minuten.

## Unterstützte FTMS-Daten

Der Parser unterstützt diese standardisierten FTMS-Characteristics:

- Indoor Bike Data `0x2AD2`
- Treadmill Data `0x2ACD`
- Cross Trainer / Elliptical Data `0x2ACE`

Die aktuelle Android-Bridge wurde vor allem mit dem Darwin TM70 Touch / `FS-BC11B7` Laufband getestet. Die Indoor-Cycling-Ansicht und die Zielleistung wurden bisher nur mit einem simulierten FTMS-Bike getestet, noch nicht mit einem echten. Leistung und Kadenz von der Uhr wurden nur mit simulierten Uhrenwerten getestet; welche Werte eine Uhr für welche Aktivität und welchen Sensor liefert, ist an einer echten noch nicht bestätigt. Der Laufsensor für die Uhr wurde mit einer Forerunner 970 getestet: Sie verbindet ihn und bekommt die Werte, solange ihre Telefonverbindung aus ist. Wie die Uhr die Werte in der aufgezeichneten Aktivität verwendet, ist noch nicht bestätigt.

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
- `3`: Höhenmeter von der Android-Bridge

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
- Meldet der Geräte-Bildschirm, dass Garmin Connect fehlt oder zu alt ist, nutze dort die Schaltfläche zum Installieren oder Aktualisieren und kehre dann zu FTMS Bridge zurück.
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

- JDK 17 oder neuer (Builds sind mit JDK 21 geprüft)
- Android SDK mit Plattform 37.1
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

Forerunner-970-Datenfeld bauen:

```bash
DEVICE=fr970 ./app/garminDataField/scripts/build.sh
```

Connect-IQ-Store-Paket exportieren:

```bash
./app/garminDataField/scripts/export-iq.sh
```

Store-Listing-Texte und die Einreichungs-Checkliste stehen in [docs/connect-iq-store-listing.md](docs/connect-iq-store-listing.md).

Build-Ausgabe:

```text
app/garminDataField/build/outputs/FtmsBridgeField-fr970.prg
```

Kopiere die `.prg`-Datei auf die Uhr:

```text
GARMIN/APPS/
```

Weitere Hinweise zu IntelliJ und dem Garmin SDK stehen in [INTELLIJ.md](INTELLIJ.md).

## Repository-Struktur

```text
app/androidApp/       Android-Bridge-App
app/garminDataField/  Garmin-Connect-IQ-Datenfeld
app/shared/           Kotlin-Multiplatform-FTMS-Parser, Glättung, Storage-Modelle
docs/                 Connect-IQ-Store-Unterlagen und Assets
tools/ftmsProbe/      Linux-FTMS-Validierungsprobe
```

Nützliche technische Referenzen:

- [Changelog](CHANGELOG.md)
