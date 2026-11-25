# FTMS Datafield

[Deutsch](README.de.md)

FTMS Datafield bridges Bluetooth FTMS fitness equipment to Garmin Connect IQ data fields.

The project contains two parts:

- an Android bridge app that connects to FTMS equipment, stores sessions locally, and forwards live samples to a Garmin watch through the Garmin Connect IQ companion channel
- a Garmin Connect IQ data field source project that receives bridge samples and writes custom FIT developer fields

The bridge reads the equipment and nothing else, with one exception: for the equipment type "Indoor cycling" it can set a target power through the FTMS Control Point `0x2AD9` when you ask for one, and sets the last one again when a session starts. Treadmills and cross trainers are never written to, and no vendor control commands are sent.

## Download v1.0.0

Version 1.0.0 provides the Android bridge as a debug-signed APK:

- [ftms-bridge-android-v1.0.0-debug.apk](https://github.com/oberstrike/ftms-datafield/releases/download/v1.0.0/ftms-bridge-android-v1.0.0-debug.apk)
- [SHA256SUMS.txt](https://github.com/oberstrike/ftms-datafield/releases/download/v1.0.0/SHA256SUMS.txt)
- [Release page](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0)

Important: v1.0.0 currently publishes the Android APK. The Garmin Connect IQ data field is being prepared for Connect IQ Store review; until it is approved, it can be built and sideloaded from source.

## Install The Android App

1. Open the [v1.0.0 release page](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.0.0) on your Android phone.
2. Download `ftms-bridge-android-v1.0.0-debug.apk`.
3. If Android blocks the APK, allow installation from unknown sources for the browser or file manager you used.
4. Install the APK.
5. Open `FTMS Bridge`.
6. Grant the requested Bluetooth permissions.

Android may warn that this is a debug or unknown-source app. That is expected for v1.0.0 because the APK is not distributed through Google Play yet.

## Use The Bridge

You need both the Android app and the Garmin Connect IQ data field on the watch.

1. Install or sideload the Connect IQ data field on the Garmin watch.
2. On the watch, open a treadmill or indoor activity.
3. Edit the activity data screens and add the FTMS data field.
4. Start the activity on the watch.
5. Start the treadmill belt.
6. Open the Android app.
7. Select the FTMS treadmill and the Garmin watch.
8. Tap `Start`.
9. Keep the Android app running during the session.
10. Tap `Stop` in the Android app when you are done.

The app stores sessions locally and shows history, charts, and diagnostic logs. The watch records received values as Connect IQ FIT developer fields.

### Equipment type

On the `Devices` tab you choose what the selected equipment is: `Treadmill`, `Indoor cycling` or `Cross trainer`. Equipment that names its type while advertising is preselected when you pick it; you can change the choice while the bridge is stopped.

- During a session the dashboard opens on the charts, each with its latest value above it, followed by the live values, the `Sources` card and the status. Without a session it shows the status and the last session.
- Start, pause and stop are in a bar fixed above the navigation, with the state of the bridge and the time of the running session. On the dashboard the bar is always there and names the equipment and the watch before the start, and whatever keeps the bridge from starting; on the other tabs it appears while a session runs.
- The dashboard has its own set of live values and charts for each type. An indoor bike starts with power, cadence, speed, heart rate, distance and resistance, without pace, incline and ascent. `Settings > Dashboard` edits the set for each type.
- Only what belongs to the type is offered and shown: no pace on a bike, no resistance on a treadmill, and the cadence is called `Step rate` (spm) on a treadmill and `Cadence` (rpm) otherwise.
- During a session the dashboard leaves out what the equipment and the watch have not delivered so far: no tile, no chart and no row under `Sources` for a heart rate that nobody measures. It appears as soon as the first value arrives.
- Each chart has a value axis on round numbers, the time since the start of the session below it, and the latest value above it, with average and maximum where they say something. Rest a finger on a chart to read the value at that time. The distance is a live value only: as a chart it would be a line that just rises.
- Each session is stored with its type. The history shows the type, leaves out values that do not apply to it, and can be filtered by type once more than one type is stored. Sessions from before this version get their type from the data they contain.
- The treadmill power estimate is applied to treadmills only.
- If the equipment sends another type of data than the one selected, the dashboard says so.

### Target power for an indoor bike

With the type `Indoor cycling`, the dashboard shows a `Target power` card while the bike is connected:

- The card stands directly below the power chart, whose latest value is the power the bike measures. Without a power chart the card shows the measured power itself, next to the target.
- Tap the target to type it as a number. Only a value within the range the bike reports can be confirmed. The target is sent exact to the watt, also to a bike that reports steps of, say, 5 W: such a bike may hold 168 W all the same (a Wattbike Proton does). Only if the bike refuses a target between its steps does the app keep to those steps from then on.
- The buttons move the target in two step sizes: 1 W (the bike's own step once it refused a target between its steps) and the step size from the settings. Where both are the same there is one pair of buttons. Without a target, the first step starts from the power the bike currently measures.
- Tap a preset to set that target.
- The power chart on the dashboard draws the target as a thin stepped line next to the measured power.
- The card shows whether the bike confirmed or refused the target, and the range of targets the bike reports.
- When a paused session is resumed, the app sends its target again, so it holds after the break even if the bike let go of it in between.
- A target set elsewhere is taken over, if the bike announces it (FTMS Fitness Machine Status, "Target Power Changed"): on the bike itself or by a watch that controls the bike. The card then says that the bike reported it, and the app sends nothing on its own, also not after a pause, until you set a target in the app again. Whoever sets the target is in charge; a watch that controls the bike overrides what other apps send. FTMS has no way to ask a bike for its current target, so a change made while the app is not connected goes unnoticed.
- The app's target is set again after a reconnect within the session. A new session starts without a target: the app sends nothing until you set one, and shows a target as soon as the bike reports one. The target you last set in the app on this bike is filled in when you tap the target to type one.
- To stop the app from setting a target, tap the target and choose `No target`. The app then sends nothing more; the bike keeps what it was last given, so use its own controls to leave its power mode.
- `Settings > Indoor cycling` holds the presets (up to 8, from 10 to 2000 W; the defaults are 100, 150, 200 and 250 W) and the step size (5, 10 or 25 W).

The bike must offer the FTMS Control Point with "Set Target Power". If it does not, the card says so.

The bike holds the target by its own measurement. If you show the power of pedals instead (see below), that value can differ from the target by a few percent; the app does not correct the target for it.

### Values from the watch

The watch sends its heart rate to the phone, and with it the power and cadence it has for the running activity. That includes sensors paired with the watch, such as a chest strap or power meter pedals. The app does not connect to these sensors itself.

- During a session the dashboard card `Sources` shows the value of the equipment and of the watch side by side for heart rate, power and cadence, as far as one of the two delivers it. Tap one to use it. The same choice is under `Settings > Data sources`, kept separately for each equipment type.
- The default is heart rate from the watch, power and cadence from the equipment.
- The chosen value is used for the display, the history and the values sent to the watch.
- If the chosen source delivers nothing for 5 seconds, the value stays empty; the heart rate of the watch stays for 15 seconds, because it changes slowly and the watch's messages can take seconds to arrive. The app does not switch to the other source by itself.
- Both values are stored with the session. For a session that contains both, the history shows the average of each, the difference, and a chart with both lines.
- The treadmill power estimate counts as a value of the equipment.
- The watch values are shown while the equipment sends data.

Power and cadence need this version of both the Android app and the data field. With an older data field the watch sends heart rate only.

### Running sensor for the watch

On a treadmill the watch estimates pace and distance of its activity from arm movement, and a Connect IQ data field is not allowed to replace them. With `Settings > Bridge behavior > Running sensor for the watch` switched on, the phone offers the treadmill's values to the watch as a Bluetooth foot pod instead. A watch takes pace, distance and step rate from a paired foot pod.

**A watch does not use the phone as a sensor and through Garmin Connect at once.** It recognises the sensor as the phone it is paired with, and it keeps one connection to a device. With a Forerunner 970 the sensor does not connect while the phone connection of the watch is on, and while the sensor is connected, the data field and the app no longer reach each other. There are two ways around that.

#### With a relay on a computer: sensor and data field together

A computer in between takes the phone's sensor and offers it to the watch under its own Bluetooth address. The watch then has two different devices, and both connections work at once:

    treadmill --FTMS--> phone (bridge app) --foot pod--> computer --foot pod--> watch

The relay is `tools/footPodRelay/foot_pod_relay.py`. It needs Linux with BlueZ and a Bluetooth adapter that can be central and peripheral at once, and it has to run within Bluetooth range of phone and watch.

    python3 -m venv .venv-foot-pod-relay
    .venv-foot-pod-relay/bin/pip install -r tools/footPodRelay/requirements-foot-pod-relay.txt
    .venv-foot-pod-relay/bin/python tools/footPodRelay/foot_pod_relay.py

1. Start the relay. It offers a sensor named `FTMS Bridge` and waits for the phone's sensor.
2. In the app, switch the setting on and choose the type `Treadmill`.
3. Once only: on the watch, add a new sensor of the type foot pod (on a Forerunner under `Sensors & Accessories`) and pick the relay. In its settings, set speed and distance to `Indoor`, the calibration factor to 100 and switch automatic calibration off. Otherwise the watch scales the treadmill's values. Remove an entry of the phone itself from the watch's sensors, or the watch connects to the phone directly.
4. Start the activity on the watch. The session starts (see "Starting and stopping with the watch"), the phone offers its sensor, the relay passes it on.

Tested with a Forerunner 970 (firmware 19.23) and BlueZ 5.82. On the first attempt the watch's firmware crashed eight seconds after the sensor was added while measurements were already flowing; later attempts, with the watch connecting before the session started, ran without a problem. The cause is not known.

#### Without a relay: sensor instead of the data field

1. On the watch, switch the phone connection off.
2. In the app, switch the setting on, choose the type `Treadmill` and start the bridge. In this mode the bridge starts without reaching the data field. The status card on the dashboard shows `Running sensor: waiting for the watch`.
3. Once only: add the sensor on the watch as described above. The phone is listed with an ID made of its Bluetooth address, not with a name.
4. The status card shows `connected to the watch`. Start the treadmill activity on the watch.
5. After the run, stop the bridge or switch the setting off, and switch the phone connection of the watch on again.

During that time the data field receives no values (incline, ascent, the Connect IQ fields), and the app receives no heart rate, power or cadence from the watch. The app still records its own session completely.

#### What the sensor sends

- The phone is visible as a sensor only while the bridge runs with the type `Treadmill`.
- It sends speed, total distance and step rate once a second. While the bridge is paused it sends standstill, and when the treadmill delivers nothing, it sends nothing.
- The step rate is the treadmill's own. Treadmills with the FitShow protocol report no step rate through FTMS; for them the app works it out from the step count in their vendor packets. If a treadmill offers neither, the sensor sends 0, and the watch records that instead of its own step rate, which also sets values the watch derives from it, such as its running power, to 0.
- Incline and ascent have no place in a foot pod.
- Android needs the permission to make the phone visible to nearby devices. It belongs to the same group as the Bluetooth permissions the app already has.

### Correcting the distance on the watch

If you keep the data field instead, the activity keeps the watch's own estimate of the distance. At the end of a treadmill activity a Garmin watch offers `Calibrate & Save`: enter the distance the app shows for the session (`Last session` on the dashboard, or the history). The watch then stores that distance and uses it to improve its estimate for later runs.

### Starting and stopping with the watch

With `Settings > Bridge behavior > Follow the activity on the watch` (on by default) you do not need `Start` and `Stop` in the app, steps 8 and 10: the session follows the activity that contains the data field.

- Starting the activity on the watch starts the session. If the app is opened while the activity already records, the session starts within a few seconds.
- Stopping the activity on the watch pauses the session, resuming it resumes the session. Auto pause on the watch does not pause the session.
- Saving or discarding the activity ends the session.
- The app has to be open on the phone when the activity starts, with treadmill and watch selected, unless it waits in the background (see below). The watch needs the data field from this version; an older data field does not report the activity.
- Without a phone session the data field connects to the treadmill itself. When the session starts, it hands the connection over to the phone and shows `PWAIT` until the phone has data, which can take a few seconds.
- `Start`, `Pause` and `Stop` in the app keep working. What you do there stays until the state of the activity on the watch changes again.
- A session that already runs when the activity starts simply continues. For a session that covers exactly the activity, leave `Auto start` off and let the watch start it.

#### Waiting in the background

Android hands the watch's messages to the app only while the app is on screen or runs a foreground service. With `Settings > Bridge behavior > Wait for the watch in the background` (off by default) the app keeps such a service after you have opened it once, so the session starts with the activity without opening the app again.

- While it waits, the notification `FTMS Bridge is waiting` is shown. `Stop waiting` there ends it until you open the app the next time.
- The app does not search for or connect to the treadmill while it waits. It connects once the activity starts.
- After a session the app waits again, for the next activity.
- Waiting ends when the phone restarts or Android closes the app; open the app once to take it up again. Phones with strict battery management may close the app after some time unless you exempt it there.
- It needs the Bluetooth permission and a selected treadmill and watch. Tested in the emulator with a simulated activity, not yet on a phone with a watch.

### Auto stop

With `Settings > Bridge behavior > Auto stop` (on by default) a treadmill session ends by itself once the belt has stood still for 30 seconds, or the treadmill has been out of reach for 90 seconds. The session then ends at the moment the belt stopped, so the wait does not count as session time.

- A session whose belt has not moved yet is left alone, so you can start it before you step on.
- While the watch reports an activity of its own, the watch decides when the session ends and a break on the belt does not end it. If the watch has not been heard from for 20 seconds, the stop of the belt ends the session again.
- Sessions on an indoor bike or cross trainer do not end by themselves, because standing still there is just a break. They end when the activity on the watch is saved or discarded, or with `Stop` in the app.

### Distance of a session

A session counts its distance and ascent from its own start. What the equipment already showed when the session began is left out, and so is what is covered while the session is paused. If the equipment is reset during a session, the session keeps what it has counted and continues from there.

### Auto start

With `Settings > Bridge behavior > Auto start` switched on, step 8 is not needed: while the app is open, the bridge starts as soon as the belt of the selected treadmill moves. The watch must already be selected and show the data field as installed.

- Auto start works only while the app is on screen. It does not start the bridge with the phone locked or the app in the background.
- While it waits, the app is connected to the treadmill. Most treadmills accept one connection only, so other apps cannot connect to it during that time.
- An indoor bike or cross trainer counts as moving as soon as it reports speed or cadence.
- After a session ends, auto start waits until the belt has stopped. If the treadmill is switched off while the belt still moves, it waits until the treadmill has been off for a minute, and 30 minutes at most.

## Supported FTMS Data

The parser supports these standard FTMS characteristics:

- Indoor Bike Data `0x2AD2`
- Treadmill Data `0x2ACD`
- Cross Trainer / Elliptical Data `0x2ACE`

The current Android bridge has been tested primarily with the Darwin TM70 Touch / `FS-BC11B7` treadmill. Indoor cycling has been ridden with a Wattbike Proton and a Forerunner 970: the session follows the activity on the watch, the bike confirms the app's target power and holds it exact to the watt, and heart rate, power and cadence from the watch arrive about every 2 seconds. That the app follows a target power set by the watch has been tested against a simulated bike only. The running sensor for the watch has been tested with a Forerunner 970: it connects and receives the values while its phone connection is off. How the watch uses them in the recorded activity is not yet confirmed.

## Garmin Data Fields

The Connect IQ project writes custom FIT developer fields such as:

- `hm_gelaufen`
- `hm_gelaufen_summe`
- `hm_quelle`
- `ftms_power`
- `ftms_speed`
- `ftms_distance`
- `ftms_cad_step`
- `ftms_incline`
- `ftms_machine`

The `hm_*` fields are not written for an indoor bike, which has no ascent.

`hm_quelle` means:

- `0`: no source
- `1`: direct FTMS Positive Elevation Gain field
- `2`: calculated from distance delta times positive incline
- `3`: ascent supplied by the Android bridge

The data field does not overwrite Garmin native distance, speed, power, or elevation fields. Garmin Connect shows these values as Connect IQ fields/charts depending on device and Garmin Connect support.

## Troubleshooting

### Android cannot find the treadmill

- Make sure Bluetooth and location/Bluetooth permissions are enabled.
- Disconnect Zwift, Kinomap, FitShow, nRF Connect, or other apps from the treadmill first.
- Start the treadmill belt; some machines advertise or stream more reliably while moving.
- Keep the phone close to the treadmill console.

### Garmin watch does not appear

- Make sure the Garmin Connect app is installed and paired with the watch.
- Open Garmin Connect once before scanning in FTMS Bridge.
- If the Devices screen says Garmin Connect is missing or too old, use the install or update button there, then return to FTMS Bridge.
- Confirm the Connect IQ data field is installed on the watch.
- Refresh Garmin devices in the Android app.

### No live data appears

- Start the watch activity before starting the bridge.
- Confirm the Android app shows the treadmill as connected.
- Check the Logs screen in the Android app.
- Stop and restart the bridge after disconnecting other BLE apps from the treadmill.

### Distance or cadence looks wrong

Some treadmills report FTMS distance only in coarse or stale increments. The bridge smooths distance from speed/time between raw FTMS updates. Cadence is only shown when the treadmill or an additional supported sensor reports cadence/step-rate data.

## Developer Setup

Requirements:

- JDK 17 or newer (builds are verified with JDK 21)
- Android SDK with platform 37.1
- Node.js and npm
- Garmin Connect IQ SDK for Garmin data-field builds
- A Garmin Connect IQ developer key for `.prg` or `.iq` builds

Clone and install dependencies:

```bash
git clone https://github.com/oberstrike/ftms-datafield.git
cd ftms-datafield
npm install
```

Run Android/shared checks:

```bash
./gradlew :app:shared:jvmTest \
  :app:androidApp:testDebugUnitTest \
  :app:androidApp:lintDebug \
  :app:androidApp:assembleDebug
```

The debug APK is written to:

```text
app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Build The Garmin Data Field

Install the Garmin Connect IQ SDK and set your developer key:

```bash
export GARMIN_DEVELOPER_KEY="$HOME/garmin-dev/ciq.key"
```

Build the Forerunner 970 data field:

```bash
DEVICE=fr970 ./app/garminDataField/scripts/build.sh
```

Export the Connect IQ Store package:

```bash
./app/garminDataField/scripts/export-iq.sh
```

Store listing copy and the submission checklist are in [docs/connect-iq-store-listing.md](docs/connect-iq-store-listing.md).

Build output:

```text
app/garminDataField/build/outputs/FtmsBridgeField-fr970.prg
```

Copy the `.prg` file to the watch:

```text
GARMIN/APPS/
```

More IntelliJ and Garmin SDK notes are in [INTELLIJ.md](INTELLIJ.md).

## Repository Layout

```text
app/androidApp/       Android bridge app
app/garminDataField/  Garmin Connect IQ data field
app/shared/           Kotlin Multiplatform FTMS parser, smoothing, storage models
docs/                 Connect IQ Store submission docs and assets
tools/ftmsProbe/      Linux FTMS validation probe
```

Useful technical references:

- [Changelog](CHANGELOG.md)
