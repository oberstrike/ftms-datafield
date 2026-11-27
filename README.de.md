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

Die App hat vier Tabs: `Dashboard`, `Verlauf`, `Geräte` und `Einstellungen`. Das Dashboard hat keine Titelzeile, der Platz gehört den Werten. Die Einstellungen führen ihre Seiten unter den Überschriften `Bridge`, `Anzeige`, `Diagnose` und `Über die App` auf; eine Seite nennt sich in der Titelzeile, der Pfeil daneben führt zurück. Die Logs sind eine Seite unter `Einstellungen > Logs`. Die Zurück-Taste schließt zuerst, was in einem Tab offen ist (eine Einstellungsseite, eine Session im Verlauf), führt dann zum Dashboard und verlässt die App von dort.

### Gerätetyp

Im Tab `Geräte` wählst du, was das ausgewählte Gerät ist: `Laufband`, `Indoor Cycling` oder `Crosstrainer`. Nennt ein Gerät seinen Typ beim Advertising, ist er bei der Auswahl vorbelegt; ändern kannst du ihn, solange die Bridge gestoppt ist.

- Während einer Session beginnt das Dashboard mit den Diagrammen, jedes mit seinem aktuellen Wert darüber; darunter folgen die Live-Werte, die Karte `Quellen` und der Status. Ohne Session zeigt es den Status und die letzte Session.
- Start, Pause und Stopp sitzen in einer Leiste fest über der Navigation, zusammen mit dem Zustand der Bridge und der Zeit der laufenden Session. Im Dashboard ist die Leiste immer da und nennt vor dem Start Gerät und Uhr sowie das, was einen Start verhindert; in den anderen Tabs erscheint sie, solange eine Session läuft.
- Das Dashboard hat für jeden Typ eine eigene Auswahl an Live-Werten und Diagrammen. Ein Indoor Bike beginnt mit Leistung, Kadenz, Geschwindigkeit, Herzfrequenz, Distanz und Widerstand, ohne Pace, Steigung und Höhenmeter. Unter `Einstellungen > Dashboard` passt du die Auswahl je Typ an.
- Angeboten und gezeigt wird nur, was zum Typ gehört: keine Pace auf dem Bike, kein Widerstand auf dem Laufband, und die Kadenz heißt auf dem Laufband `Schrittfrequenz` (spm), sonst `Kadenz` (rpm).
- Während einer Session lässt das Dashboard weg, was Gerät und Uhr bisher nicht geliefert haben: keine Kachel, kein Diagramm und keine Zeile unter `Quellen` für eine Herzfrequenz, die niemand misst. Sie erscheint, sobald der erste Wert ankommt.
- Jedes Diagramm hat eine Werteachse mit runden Zahlen, darunter die Zeit seit Beginn der Session und darüber den aktuellen Wert, dazu Durchschnitt und Maximum, wo sie etwas aussagen. Lege einen Finger auf ein Diagramm, um den Wert zu diesem Zeitpunkt abzulesen. Die Distanz gibt es nur als Live-Wert: Als Diagramm wäre sie eine Linie, die nur steigt.
- Jede Session wird mit ihrem Typ gespeichert. Der Verlauf zeigt den Typ, lässt unpassende Werte weg und lässt sich nach Typ filtern, sobald mehr als ein Typ gespeichert ist (siehe [Verlauf](#verlauf)). Sessions aus älteren Versionen erhalten ihren Typ aus den enthaltenen Daten.
- Die Leistungsschätzung für das Laufband gilt nur für Laufbänder.
- Sendet das Gerät Daten eines anderen Typs als gewählt, weist das Dashboard darauf hin.

### Verlauf

Der Tab `Verlauf` listet die gespeicherten Sessions nach Monat, die neueste zuerst.

- Oben stehen die Summen: Anzahl der Sessions, Distanz, Dauer und, wo ein Gerät steigen kann, der Anstieg. Mit `Woche`, `Monat`, `Jahr` und `Gesamt` wählst du, ob sie für die laufende Woche, den laufenden Monat, das laufende Jahr oder für alle Sessions gelten. Die Woche beginnt mit dem Wochentag, mit dem sie in der Region des Telefons beginnt.
- Jeder Monat nennt in seiner Überschrift, was seine Sessions zusammen ergeben. Eine Zeile zeigt Tag und Uhrzeit, die drei wichtigsten Werte des Gerätetyps (Laufband: Distanz, Dauer, Ø Pace; Indoor Bike: Dauer, Ø Leistung, Distanz; Crosstrainer: Distanz, Dauer, Ø Geschwindigkeit) und die durchschnittliche Herzfrequenz, falls eine aufgezeichnet wurde. Eine Session, die nicht regulär endete, ist als `unterbrochen` markiert.
- Ein Tipp auf eine Zeile öffnet die Session auf einer Seite: oben die Kennzahlen, darunter die Diagramme, dann `Alle Werte`, der Vergleich `Gerät und Uhr` und die `Technischen Angaben` (Status, Gerät, Uhr, Pakete, Sendungen, letzter Fehler). Die drei Abschnitte lassen sich auf- und zuklappen; die technischen Angaben sind anfangs zu.
- Die Diagramme einer Session reichen alle über die ganze Session und zeigen ohne Berührung den Durchschnitt. Lege einen Finger auf eines, dann zeigen alle den Wert zu diesem Zeitpunkt.
- Welche Diagramme eine Session zeigt und in welcher Reihenfolge, legst du unter `Einstellungen > Verlauf` je Gerätetyp fest: anhaken, abwählen und mit den Pfeilen verschieben. Ein Diagramm, für das eine Session keine Werte hat, bleibt dort weg.
- Halte eine Zeile gedrückt, um die Session zu markieren; danach markiert ein Tipp weitere oder nimmt eine Markierung zurück. Die Kopfzeile zählt die markierten Sessions, ihr Menü bietet `Session löschen` oder `Sessions löschen` an und fragt vor dem Löschen nach. Das Kreuz oben links oder die Zurück-Taste hebt die Markierung auf; sie endet auch, wenn du den Filter wechselst oder den Tab verlässt. Eine Session, die gerade aufzeichnet, lässt sich nicht markieren.
- Ohne Markierung löscht das Menü oben rechts in der Liste den ganzen Verlauf und in einer Session diese Session. Zurück zur Liste führt der Pfeil oben links oder die Zurück-Taste.

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

### Geschwindigkeit und Steigung eines Laufbands

Ein Laufband wird nur gelesen, solange du nichts anderes einstellst. Mit `Einstellungen > Laufband > Laufband aus der App steuern` (standardmäßig aus) zeigt das Dashboard beim Typ `Laufband` die Karte `Laufband steuern`:

- Die Karte hängt am Laufband, nicht an einer Session: Sie ist da, sobald die App mit dem Laufband verbunden ist und es solche Vorgaben annimmt, und ihre Tasten gehen, sobald das Band läuft. Damit das auch ohne Session klappt, verbindet sich die App mit dem gewählten Laufband, solange sie geöffnet ist. Am Ende einer Session verschwindet die Karte für einige Sekunden, bis diese Verbindung wieder steht.
- Sie zeigt Geschwindigkeit und Steigung, wie das Laufband sie meldet, und daneben das Ziel, solange das Band noch auf dem Weg dorthin ist.
- Die Geschwindigkeit hat zwei Tastenpaare: den kleinsten Schritt des Laufbands (meist 0,1 km/h) und den großen Schritt aus den Einstellungen (0,5 oder 1 km/h). Die Steigung ändert sich um den Schritt des Laufbands.
- Tippe auf eine Schnellwahl, um Geschwindigkeit oder Steigung direkt zu setzen. Macht eine Schnellwahl das Band um mehr als 3 km/h schneller, fragt die App vorher nach; das lässt sich in den Einstellungen abschalten. Eine Schnellwahl außerhalb dessen, was das Laufband annimmt, ist ausgegraut.
- Liegt die neue Geschwindigkeit mehr als 0,5 km/h entfernt, führt die App das Band in kleinen Schritten dorthin, statt nur das Ziel zu senden (`Einstellungen > Laufband > Übergang`, Standard `Sanft`): Das Tempo des Wechsels baut sich auf, hält und klingt vor dem Ziel aus, und gebremst wird zügiger als beschleunigt. `Sanft` ändert höchstens 0,4 km/h pro Sekunde nach oben und 0,6 nach unten (von 6 auf 9,2 km/h in rund 10 Sekunden), `Sehr sanft` 0,25 und 0,4. Mit `Direkt` fährt das Laufband in seinem eigenen Tempo zum Ziel. Die Karte zeigt währenddessen `Sanfter Übergang auf …`.
- Unter `Einstellungen > Laufband` legst du die Schnellwahlen an und entfernst sie: je bis zu 8, für die Geschwindigkeit von 0,5 bis 25 km/h in Schritten von 0,1 km/h (Standard 6, 8, 10 und 12 km/h), für die Steigung von 0 bis 40 % in Schritten von 0,5 % (Standard 0, 1, 2 und 5 %).

Ein Band trägt einen Menschen, deshalb gelten hier strengere Regeln als bei der Zielleistung eines Bikes:

- Die App startet und stoppt das Band nie. Sie sendet keine Geschwindigkeit unter 0,5 km/h, und solange das Band steht, sind die Tasten gesperrt. Gestartet und gestoppt wird an der Konsole.
- Die App sendet nur, was du antippst: nichts beim Verbinden, nichts nach einem Verbindungsabbruch, nichts beim Fortsetzen einer Pause, und keinen Wert ein zweites Mal. Die Werte eines Übergangs gehören alle zu dem einen Tipp, der ihn ausgelöst hat. Die einzige Ausnahme schaltest du selbst ein: das Folgen eines Workouts der Uhr (siehe unten).
- Ein Übergang läuft dem Band nie davon: Meldet das Laufband eine Geschwindigkeit, die mehr als 0,6 km/h hinter dem zuletzt gesendeten Wert liegt, wartet die App. Folgt das Band 10 Sekunden lang nicht, wird es an der Konsole verstellt oder schweigt das Laufband länger als 5 Sekunden, bricht die App den Übergang ab und setzt ihn nicht fort.
- Was du zuletzt eingestellt hast, vergisst die App, sobald die Verbindung endet, das Band steht, das Laufband den Wert ablehnt oder nicht antwortet, oder das Band den Wert erreicht hat und wieder davon abweicht (jemand hat die Konsole benutzt). Die Tasten gehen dann von dem aus, was das Laufband meldet.
- Meldet das Laufband länger als 5 Sekunden keine Geschwindigkeit, sendet die App nichts.

Das Laufband muss den FTMS Control Point mit „Set Target Speed“ oder „Set Target Inclination“ anbieten; was es davon nicht anbietet, fehlt auf der Karte, und ohne beides erscheint sie nicht. Getestet mit einem Darwin TM 70 (FitShow-Modul), bisher mit einzelnen Befehlen von einem Rechner aus und in der App, samt Übergängen, an einem simulierten Laufband.

### Dem Workout der Uhr folgen

Läuft auf der Uhr ein Workout mit Tempo-Zielen, kann die App die Geschwindigkeit des Laufbands Schritt für Schritt danach einstellen (`Einstellungen > Laufband > Dem Workout der Uhr folgen`, standardmäßig aus). Das Datenfeld meldet der App dafür, in welchem Schritt die Uhr ist und welcher folgt. Die Uhr bleibt die Quelle: Schrittanzeige, Lap-Taste und das Ergebnis in Garmin Connect ändern sich nicht.

- Es braucht eine laufende Session, die Laufband-Steuerung (oben) und ein Band, das läuft. Das Band startest und stoppst du weiter an der Konsole.
- Die App sendet einmal, wenn die Uhr einen Schritt mit Tempo beginnt, und zwar die Mitte der Tempo-Spanne (6:30–6:32 min/km ergibt 9,2 km/h). Läuft das Band schon so schnell, sendet sie nichts. Ein Schritt ohne Tempo lässt das Band in Ruhe.
- Zwischen den Schritten sendet sie nichts: Was du von Hand oder an der Konsole einstellst, bleibt bis zum nächsten Schritt.
- Sie verlangt nie mehr als die eingestellte Höchstgeschwindigkeit (Standard 12 km/h), sendet nichts, solange die Aktivität auf der Uhr nicht aufzeichnet, und ein kurzer Ton am Telefon kündigt an, dass das Band schneller wird (abschaltbar).
- Die Karte `Laufband steuern` zeigt, was gilt und was kommt: `Folgt dem Workout der Uhr: jetzt 6,0 km/h · in 0:42 9,2 km/h`.
- Wie viel von einem Schritt noch übrig ist, sagt die Uhr einer App nicht. Das Datenfeld schickt deshalb mit, wie lange der Schritt nach dem Aktivitäts-Timer der Uhr schon läuft, und wiederholt das alle 10 Sekunden; die App zählt dazwischen weiter. So stimmt die Restzeit auch, wenn die App mitten im Schritt dazukommt oder eine Meldung verloren geht. Mit einem älteren Datenfeld zählt die App ab der Meldung, die den Schritt angekündigt hat, und liegt um deren Verspätung daneben. Ein Schritt, der mit der Lap-Taste endet, hat keine Restzeit.

`Übergang zwischen den Schritten` legt fest, wie das Band von einem Tempo zum nächsten kommt:

- `Aus`: keine eigene Beschleunigung. Die App sendet zu Beginn des Schritts das Tempo, und das Laufband beschleunigt und bremst, wie es das selbst tut.
- `Sanft`: Jeder Wechsel beginnt mit dem Schritt und wird sanft geführt, wie oben beschrieben.
- `Intelligent` (Standard): sanft, wo Zeit dafür ist, und direkt, wo der Schritt, in den der Wechsel fällt, kurz ist (einstellbar, Standard unter 1:00 min). Vor einem schnelleren Schritt beschleunigt das Band so rechtzeitig, dass es zu dessen Beginn auf Tempo ist; dafür nimmt es höchstens die Hälfte des laufenden Schritts. Gebremst wird erst, wenn der langsamere Schritt begonnen hat. Vorbereiten lässt sich nur ein Schritt, der auf einen Schritt mit fester Dauer folgt, nicht einer nach der Lap-Taste.
- `Dauer eines sanften Wechsels`: `Wie oben` nimmt das Tempo aus `Übergang`; mit 5 bis 30 Sekunden dauert jeder sanfte Wechsel etwa so lange, egal wie groß er ist. Schneller als das Laufband selbst wird er dadurch nicht.

Noch nicht an der Uhr erprobt: Garmin beschreibt nicht, in welchen Einheiten eine Uhr Tempo und Dauer eines Schritts meldet. Die App liest sie so, wie andere Entwickler sie beobachtet haben (Meter pro Sekunde, Sekunden), und verwirft, was nicht dazu passt. Lass beim ersten Workout das Folgen aus: Die Karte zeigt dann trotzdem `Workout der Uhr (wird nicht befolgt): jetzt … · in … …`, und du siehst, ob Tempo und Restzeit zu dem passen, was die Uhr anzeigt.

### Workout-Ansicht

Solange die Uhr in einem Workout ist, gibt es dafür eine eigene Ansicht im Querformat, gedacht für das Telefon auf der Ablage des Laufbands: groß, dunkel und aus der Bewegung lesbar.

- Links der Schritt, in dem die Uhr ist: ein Ring, der sich mit dem Schritt füllt, darin die Restzeit und das Tempo, das der Schritt verlangt. Ein Lauf-Schritt ist gelbgrün, eine Erholung blau. Ein Schritt ohne feste Dauer hat einen gestrichelten Ring und zählt hoch; endet er mit der Lap-Taste, steht das dabei. Pausiert die Aktivität auf der Uhr, bleibt die Zeit stehen und über ihr steht `Pausiert`.
- Darunter der Schritt, der folgt, mit Dauer und Tempo. Beschleunigt das Band schon vor dessen Beginn (Übergang `Intelligent`), wird die Karte hervorgehoben und sagt `Band zieht schon an für`.
- Rechts vier Werte. Ein Tipp auf einen Wert öffnet die Auswahl, was das Feld zeigt: Geschwindigkeit, Pace, Ø Pace, Herzfrequenz, Distanz, verstrichene Zeit, Steigung, Anstieg, Schrittfrequenz oder Leistung. Wählst du einen Wert, den ein anderes Feld schon zeigt, tauschen die beiden die Plätze. Die Auswahl bleibt gespeichert.
- Startet die Uhr die Session (siehe `Mit der Uhr starten und stoppen`), wechselt die App sofort in diese Ansicht und dreht den Bildschirm dafür selbst ins Querformat, auch bei gesperrter Drehung. Läuft auf der Uhr noch kein Workout, zeigt der Ring so lange die Zeit der Session (`Freier Lauf`); mit dem ersten Schritt eines Workouts wechselt er auf dessen Restzeit. War die App beim Start nicht im Vordergrund, steht die Ansicht da, sobald du sie öffnest.
- Bei einer Session, die du in der App startest, kommt die Ansicht von selbst, sobald die Uhr in einem Workout ist und das Telefon quer liegt.
- Das `X` oben links führt zurück ins Dashboard. Danach bleibt sie für diese Session zu, bis du sie über die Karte oben im Dashboard wieder öffnest (`Workout der Uhr läuft` oder `Workout-Ansicht`); ein Workout auf der Uhr bringt sie außerdem zurück, wenn das Telefon einmal hochkant war und wieder quer liegt.
- Ist `Einstellungen > Laufband > Laufband aus der App steuern` an und nimmt das Laufband Befehle an, stellst du das Band auch von hier:
  - Links und rechts unten neben dem Ring stehen `−` und `+` für die Geschwindigkeit. Ein Tipp ist der kleinste Schritt des Laufbands (meist 0,1 km/h).
  - Zeigt eines der vier Felder die Steigung, hat es rechts einen Pfeil nach oben und einen nach unten. Ein Tipp ist der kleinste Schritt des Laufbands. Solange das Laufband noch dorthin fährt, steht die verlangte Steigung zwischen den Pfeilen.
  - Hältst du eine Taste gedrückt, zählt sie nach knapp einer halben Sekunde von selbst weiter, und die Schritte addieren sich: bei der Geschwindigkeit etwa 0,7 km/h je Sekunde, bei der Steigung ein Schritt alle 0,4 Sekunden. Einmal Halten macht das Band höchstens 3 km/h schneller; für mehr loslassen und neu drücken. Langsamer ist nicht begrenzt.
  - Eine von Hand gestellte Geschwindigkeit steht im Ring als `von Hand` an der Stelle von `Ziel`. Sie gilt bis zum nächsten Schritt des Workouts; folgt die App dem Workout, stellt sie dann wieder dessen Tempo.
  - Steht das Band, sind die Tasten blass und tun nichts: gestartet und gestoppt wird an der Konsole.
- Die Ansicht braucht eine laufende Session mit einem Laufband, aber weder die Laufband-Steuerung noch das Folgen: Auch wer das Band selbst bedient, sieht Schritt und Restzeit. Ohne die Steuerung hat sie keine Tasten für das Band.
- Die Systemleisten sind ausgeblendet und kommen mit einem Wisch vom Rand zurück. Die Schriftgröße des Systems ändert die Ansicht nicht, weil sie den Bildschirm schon ganz ausnutzt.

Woher die Schritte kommen und was daran noch nicht an der Uhr erprobt ist, steht im Abschnitt davor. Die Ziffern sind in Barlow Condensed gesetzt (SIL Open Font License, siehe `app/androidApp/licenses/BarlowCondensed-OFL.txt`).

### Werte der Uhr

Die Uhr sendet ihre Herzfrequenz an das Telefon und dazu die Leistung und Kadenz, die sie für die laufende Aktivität hat. Dazu gehören Sensoren, die mit der Uhr gekoppelt sind, etwa ein Brustgurt oder Leistungsmesser-Pedale. Die App verbindet sich nicht selbst mit diesen Sensoren.

- Während einer Session zeigt die Dashboard-Karte `Quellen` für Herzfrequenz, Leistung und Kadenz den Wert des Geräts und den der Uhr nebeneinander, soweit einer von beiden ihn liefert. Tippe auf einen, um ihn zu verwenden. Dieselbe Auswahl steht unter `Einstellungen > Datenquellen`, getrennt für jeden Gerätetyp.
- Voreingestellt ist die Herzfrequenz von der Uhr, Leistung und Kadenz vom Gerät.
- Der gewählte Wert gilt für die Anzeige, den Verlauf und die an die Uhr gesendeten Werte.
- Liefert die gewählte Quelle 5 Sekunden lang nichts, bleibt der Wert leer; die Herzfrequenz der Uhr bleibt 15 Sekunden stehen, weil sie sich langsam ändert und die Nachrichten der Uhr Sekunden brauchen können. Die App wechselt nicht von selbst zur anderen Quelle.
- Beide Werte werden mit der Session gespeichert. Enthält eine Session beide, zeigt der Verlauf den Durchschnitt je Quelle und die Abweichung.
- Die Diagramme für Herzfrequenz, Leistung und Kadenz zeigen beide Quellen, im Dashboard und im Verlauf: die gewählte Quelle in Schwarz (auf dunklem Bildschirm in Weiß), die andere in Rot und etwas dünner, beide in der Legende benannt. Hinter dem Durchschnitt der gewählten Quelle steht der der anderen in Klammern und in Rot, etwa `Ø 148 (150) spm`; genauso stehen diese Durchschnitte in einer Session unter `Alle Werte`. Im Verlauf gilt als gewählt, was jetzt für diesen Gerätetyp eingestellt ist.
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

#### Mit einem Relais auf einem zweiten Telefon

Dasselbe Relais als Android-App, für ein übriges Telefon, das in der Nähe des Laufbands bleibt: FTMS Relay (`app/relayApp`).

    Laufband --FTMS--> Telefon (Bridge-App) --Laufsensor--> zweites Telefon (FTMS Relay) --Laufsensor--> Uhr

1. `relayApp-debug.apk` auf dem zweiten Telefon installieren, öffnen, `Relay starten` antippen und „Geräte in der Nähe" erlauben. Das zweite Telefon darf keines sein, mit dem die Uhr gekoppelt ist.
2. `Hintergrundbetrieb erlauben` antippen, falls die App es anbietet. Auf Telefonen, die selbst entscheiden, welche Apps starten dürfen (Honor, Huawei, Xiaomi), der App zusätzlich in den Systemeinstellungen den automatischen Start erlauben; sonst kommt das Relais nach einem Neustart des Telefons nicht von selbst zurück, und du musst die App einmal öffnen.
3. Weiter mit den Schritten 2 bis 4 oben; an der Uhr das zweite Telefon als Laufsensor auswählen.

Die App zeigt, ob sie das Telefon mit der Bridge gefunden hat, ob eine Uhr die Messwerte empfängt und wie viele durchgegangen sind. Den Sensor der Bridge erkennt sie am Modell, das dieser meldet; andere Laufsensoren in Reichweite, etwa einen Brustgurt, lässt sie in Ruhe. Ihre Ereignisse schreibt sie nach `files/logs/relay.log` im Datenverzeichnis der App.

Getestet mit einem Honor 70 (Android 14) und einem Rechner, der Bridge und Uhr gespielt hat: Jeder Messwert kam unverändert an, etwa 60 ms später. Eine Forerunner 970 hat das Telefon als Laufsensor gefunden und sich beim Hinzufügen mit ihm gekoppelt (Android fragt auf dem zweiten Telefon womöglich nach, ob gekoppelt werden soll). Ein Android-Telefon ist unter einer zufälligen Bluetooth-Adresse sichtbar, die bei jedem Start des Relais eine neue ist; die gekoppelte Uhr hat es unter neuer Adresse wiedergefunden. Ein vollständiges Training über das zweite Telefon steht noch aus.

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
- Eine Session, die die Uhr gestartet hat, führt die Uhr: Die App zeigt dafür weder in der App noch in ihrer Benachrichtigung `Pause` und `Stop`, du pausierst und beendest sie an der Uhr. Hat die App 20 Sekunden lang nichts von der Uhr gehört, kommen die Schaltflächen zurück, damit sich eine Session auch dann beenden lässt, wenn die Uhr außer Reichweite ist.
- Bei einer Session, die du in der App gestartet hast, funktionieren `Pause` und `Stop` in der App weiter. Was du dort tust, bleibt bestehen, bis sich der Zustand der Aktivität an der Uhr wieder ändert.
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

Die aktuelle Android-Bridge wurde vor allem mit dem Darwin TM70 Touch / `FS-BC11B7` Laufband getestet. Indoor Cycling wurde mit einem Wattbike Proton und einer Forerunner 970 gefahren: Die Session folgt der Aktivität der Uhr, das Bike bestätigt die Zielleistung der App und hält sie wattgenau, und Herzfrequenz, Leistung und Kadenz der Uhr kommen etwa alle 2 Sekunden an. Dass die App einer von der Uhr gesetzten Zielleistung folgt, wurde bisher nur mit einem simulierten Bike getestet. Der Laufsensor für die Uhr wurde mit einer Forerunner 970 getestet: Sie verbindet ihn und bekommt die Werte, solange ihre Telefonverbindung aus ist. Wie die Uhr die Werte in der aufgezeichneten Aktivität verwendet, ist noch nicht bestätigt.

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

Die `hm_*`-Felder werden bei einem Indoor Bike nicht geschrieben, da es keine Höhenmeter gibt.

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
- Öffne `Einstellungen > Logs` in der Android-App.
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
  :app:androidApp:assembleDebug \
  :app:relayApp:testDebugUnitTest \
  :app:relayApp:lintDebug \
  :app:relayApp:assembleDebug
```

Die Debug-APKs werden hier erstellt:

```text
app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
app/relayApp/build/outputs/apk/debug/relayApp-debug.apk
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
app/relayApp/         Android-Relay-App, die den Laufsensor von einem zweiten Telefon aus weiterreicht
app/shared/           Kotlin-Multiplatform-FTMS-Parser, Glättung, Storage-Modelle
docs/                 Connect-IQ-Store-Unterlagen und Assets
tools/ftmsProbe/      Linux-FTMS-Validierungsprobe
```

Nützliche technische Referenzen:

- [Changelog](CHANGELOG.md)
