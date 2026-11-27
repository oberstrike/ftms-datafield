# FTMS Datafield

[Deutsch](README.de.md)

FTMS Datafield bridges Bluetooth FTMS fitness equipment to Garmin Connect IQ data fields.

The project contains two parts:

- an Android bridge app that connects to FTMS equipment, stores sessions locally, and forwards live samples to a Garmin watch through the Garmin Connect IQ companion channel
- a Garmin Connect IQ data field source project that receives bridge samples and writes custom FIT developer fields

The bridge reads the equipment and writes to it only where you ask for it. For the equipment type "Indoor cycling" it can set a target power through the FTMS Control Point `0x2AD9`, and sets the last one again when a session starts. For a treadmill it can set speed and incline once you switch that on (`Settings > Treadmill`, off by default); it never starts or stops a belt. Cross trainers are never written to, and no vendor control commands are sent.

## Download v1.1.0

Version 1.1.0 provides both Android apps as debug-signed APKs and the data field built for the Forerunner 970:

- [ftms-bridge-android-v1.1.0-debug.apk](https://github.com/oberstrike/ftms-datafield/releases/download/v1.1.0/ftms-bridge-android-v1.1.0-debug.apk): the bridge app
- [ftms-relay-android-v1.1.0-debug.apk](https://github.com/oberstrike/ftms-datafield/releases/download/v1.1.0/ftms-relay-android-v1.1.0-debug.apk): FTMS Relay for a second phone, only needed for the running sensor (see `Running sensor for the watch`)
- [FtmsBridgeField-fr970-v1.1.0.prg](https://github.com/oberstrike/ftms-datafield/releases/download/v1.1.0/FtmsBridgeField-fr970-v1.1.0.prg): the Connect IQ data field for the Forerunner 970, to copy to the watch
- [SHA256SUMS.txt](https://github.com/oberstrike/ftms-datafield/releases/download/v1.1.0/SHA256SUMS.txt)
- [Release page](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.1.0)

Important: the data field is not in the Connect IQ Store. The `.prg` file of the release runs on the Forerunner 970 only; connect the watch to a computer and copy the file to `GARMIN/APPS/`. For other watches build it from source, see `Build The Garmin Data Field`.

## Install The Android App

1. Open the [v1.1.0 release page](https://github.com/oberstrike/ftms-datafield/releases/tag/v1.1.0) on your Android phone.
2. Download `ftms-bridge-android-v1.1.0-debug.apk`.
3. If Android blocks the APK, allow installation from unknown sources for the browser or file manager you used.
4. Install the APK.
5. Open `FTMS Bridge`.
6. Grant the requested Bluetooth permissions.

Android may warn that this is a debug or unknown-source app. That is expected because the APK is not distributed through Google Play yet.

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

The app has four tabs: `Dashboard`, `History`, `Devices` and `Settings`. The dashboard has no title bar, so its room goes to the values. The settings list their pages under the headings `Bridge`, `Display`, `Diagnostics` and `About`; a page names itself in the title bar and leads back with the arrow next to it. The logs are a page under `Settings > Logs`. The back button first closes what is open in a tab (a settings page, a session of the history), then leads to the dashboard, and leaves the app from there.

### Equipment type

On the `Devices` tab you choose what the selected equipment is: `Treadmill`, `Indoor cycling` or `Cross trainer`. Equipment that names its type while advertising is preselected when you pick it; you can change the choice while the bridge is stopped.

- During a session the dashboard opens on the charts, each with its latest value above it, followed by the live values, the `Sources` card and the status. Without a session it shows the status and the last session.
- Start, pause and stop are in a bar fixed above the navigation, with the state of the bridge and the time of the running session. On the dashboard the bar is always there and names the equipment and the watch before the start, and whatever keeps the bridge from starting; on the other tabs it appears while a session runs.
- The dashboard has its own set of live values and charts for each type. An indoor bike starts with power, cadence, speed, heart rate, distance and resistance, without pace, incline and ascent. `Settings > Dashboard` edits the set for each type.
- Only what belongs to the type is offered and shown: no pace on a bike, no resistance on a treadmill, and the cadence is called `Step rate` (spm) on a treadmill and `Cadence` (rpm) otherwise.
- During a session the dashboard leaves out what the equipment and the watch have not delivered so far: no tile, no chart and no row under `Sources` for a heart rate that nobody measures. It appears as soon as the first value arrives.
- Each chart has a value axis on round numbers, the time since the start of the session below it, and the latest value above it, with average and maximum where they say something. Rest a finger on a chart to read the value at that time. The distance is a live value only: as a chart it would be a line that just rises.
- Each session is stored with its type. The history shows the type, leaves out values that do not apply to it, and can be filtered by type once more than one type is stored (see [History](#history)). Sessions from before this version get their type from the data they contain.
- The treadmill power estimate is applied to treadmills only.
- If the equipment sends another type of data than the one selected, the dashboard says so.

### History

The `History` tab lists the stored sessions by month, the latest first.

- At the top are the sums: number of sessions, distance, duration and, where equipment can climb, the ascent. `Week`, `Month`, `Year` and `All time` choose whether they cover the running week, the running month, the running year or all sessions. The week begins on the day it begins with in the phone's region.
- Each month names in its heading what its sessions add up to. A row shows day and time, the three most telling values of the equipment type (treadmill: distance, duration, average pace; indoor bike: duration, average power, distance; cross trainer: distance, duration, average speed) and the average heart rate if one was recorded. A session that did not end the regular way is marked `interrupted`.
- A tap on a row opens the session on one page: its figures at the top, then the charts, then `All values`, the comparison `Equipment and watch` and the `Technical details` (status, equipment, watch, packets, sends, last error). The three sections fold; the technical details start folded.
- The charts of a session all span the whole session and show the average while untouched. Put a finger on one of them and all of them show their value at that time.
- Which charts a session shows, and in which order, is set per equipment type under `Settings > History`: tick, untick and move them with the arrows. A chart a session has no values for is left out there.
- Press and hold a row to mark the session; after that a tap marks further ones or takes a mark away. The title bar counts the marked sessions, and its menu offers `Delete session` or `Delete sessions` and asks before deleting. The cross at the top left or the back key clears the marks; they also end when you change the filter or leave the tab. A session that is still recording cannot be marked.
- Without marks, the menu at the top right clears the whole history in the list, and deletes the session in a session. The arrow at the top left or the back key leads back to the list.

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

### Speed and incline of a treadmill

A treadmill is only read unless you say otherwise. With `Settings > Treadmill > Steer the treadmill from the app` (off by default) the dashboard shows the card `Treadmill control` for the type `Treadmill`:

- The card goes with the treadmill, not with a session: it is there as soon as the app is connected to the treadmill and the treadmill takes such values, and its keys work as soon as the belt moves. So that this works without a session too, the app connects to the selected treadmill while it is open. At the end of a session the card is gone for a few seconds, until that connection is back.
- It shows speed and incline as the treadmill reports them, and next to them the target while the belt is still on its way there.
- The speed has two pairs of keys: the smallest step the treadmill takes (mostly 0.1 km/h) and the large step from the settings (0.5 or 1 km/h). The incline changes by the treadmill's step.
- Tap a preset to set speed or incline directly. If a preset makes the belt faster by more than 3 km/h, the app asks first; this can be switched off in the settings. A preset outside of what the treadmill takes is greyed out.
- If the new speed is more than 0.5 km/h away, the app leads the belt there in small steps instead of sending only the target (`Settings > Treadmill > Transition`, default `Gentle`): the pace of the change builds up, holds and fades out before the target, and slowing down is brisker than speeding up. `Gentle` changes at most 0.4 km/h per second upwards and 0.6 downwards (from 6 to 9.2 km/h in about 10 seconds), `Very gentle` 0.25 and 0.4. With `Direct` the treadmill goes to the target at its own pace. Meanwhile the card shows `Easing to …`.
- Under `Settings > Treadmill` you add and remove the presets: up to 8 each, for the speed from 0.5 to 25 km/h in steps of 0.1 km/h (default 6, 8, 10 and 12 km/h), for the incline from 0 to 40 % in steps of 0.5 % (default 0, 1, 2 and 5 %).

A belt carries a person, so the rules are stricter here than for the target power of a bike:

- The app never starts or stops the belt. It sends no speed below 0.5 km/h, and while the belt stands the keys are locked. Starting and stopping is done on the console.
- The app only sends what you tap: nothing on connecting, nothing after a lost connection, nothing when a pause ends, and no value a second time. The values of a transition all belong to the one tap that started it. The only exception is one you switch on yourself: following a workout on the watch (see below).
- A transition never runs ahead of the belt: if the treadmill reports a speed more than 0.6 km/h behind the value last sent, the app waits. If the belt does not follow for 10 seconds, is moved on the console, or the treadmill is silent for more than 5 seconds, the app gives the transition up and does not pick it up again.
- The app forgets what you last set as soon as the connection ends, the belt stops, the treadmill refuses the value or does not answer, or the belt reached the value and left it again (someone used the console). The keys then start from what the treadmill reports.
- If the treadmill reports no speed for more than 5 seconds, the app sends nothing.

The treadmill has to offer the FTMS Control Point with "Set Target Speed" or "Set Target Inclination"; what it does not offer is missing on the card, and without both the card does not appear. Tested with a Darwin TM 70 (FitShow module), so far with single commands from a computer and in the app, transitions included, against a simulated treadmill.

### Following the workout on the watch

While a workout with pace targets runs on the watch, the app can set the speed of the treadmill to it step by step (`Settings > Treadmill > Follow the watch's workout`, off by default). For that the data field tells the app which step the watch is in and which one follows. The watch stays the source: its step display, the lap key and the result in Garmin Connect do not change.

- It needs a running session, the treadmill control (above) and a belt that moves. You still start and stop the belt on the console.
- The app sends once when the watch begins a step with a pace: the middle of the pace range (6:30–6:32 min/km gives 9.2 km/h). If the belt already runs that fast, it sends nothing. A step without a pace leaves the belt alone.
- Between steps it sends nothing: what you set by hand or on the console stays until the next step.
- It never asks for more than the maximum speed in the settings (default 12 km/h), sends nothing while the activity on the watch is not recording, and a short tone on the phone announces that the belt gets faster (can be switched off).
- The card `Treadmill control` shows what applies and what comes: `Following the watch's workout: now 6.0 km/h · in 0:42 9.2 km/h`.
- A watch does not tell an app how much of a step is left. The data field therefore sends how long the step has lasted by the watch's activity timer, and repeats that every 10 seconds; the app counts on in between. So the remaining time is right even if the app joins in the middle of a step or a message is lost. With an older data field the app counts from the message that announced the step and is off by however late that came. A step that ends with the lap key has no remaining time.

`Transition between steps` decides how the belt gets from one pace to the next:

- `Off`: no acceleration of the app's own. The app sends the pace when the step begins, and the treadmill speeds up and slows down the way it does by itself.
- `Gentle`: every change begins with its step and is led gently, as described above.
- `Smart` (default): gentle where there is time for it, direct where the step the change falls into is short (adjustable, default below 1:00 min). Before a faster step the belt speeds up early enough to be at pace when the step begins; it takes at most half of the running step for that. It slows down only once the slower step has begun. Only a step that follows a step of fixed length can be prepared, not one after the lap key.
- `Length of a gentle change`: `As above` takes the pace from `Transition`; with 5 to 30 seconds every gentle change takes about that long, however large it is. That does not make it faster than the treadmill itself.

Not yet tried on a watch: Garmin does not describe in which units a watch reports the pace and the length of a step. The app reads them the way other developers have observed them (metres per second, seconds) and discards what does not fit. Leave following off for the first workout: the card then still shows `Workout on the watch (not followed): now … · in … …`, and you see whether pace and remaining time match what the watch displays.

### Workout view

While the watch is in a workout there is a view of its own in landscape, meant for the phone on the shelf of the treadmill: large, dark and readable in motion.

- On the left the step the watch is in: a ring that fills as the step passes, with the remaining time and the pace the step asks for inside. A run is yellow-green, a recovery blue. A step without a fixed length has a dashed ring and counts up; if it ends with the lap key, it says so. While the activity on the watch is paused, the time stands still and `Paused` stands above it.
- Below it the step that follows, with its length and pace. If the belt already speeds up before that step begins (transition `Smart`), the card is highlighted and says `Belt already speeds up for`.
- On the right four values. A tap on one opens the choice of what the field shows: speed, pace, average pace, heart rate, distance, elapsed time, incline, ascent, step rate or power. If you choose a value that another field already shows, the two change places. The choice is kept.
- When the watch starts the session (see `Starting and stopping with the watch`), the app switches to this view at once and turns the screen to landscape for it, even with rotation locked. While no workout runs on the watch yet, the ring shows the time of the session (`Free run`); with the first step of a workout it changes to that step's remaining time. If the app was not in the foreground at the start, the view is there as soon as you open it.
- In a session you start in the app, the view comes by itself as soon as the watch is in a workout and the phone lies on its side.
- The `X` at the top left leads back to the dashboard. After that it stays closed for this session until you open it again from the card at the top of the dashboard (`Workout on the watch is running` or `Workout view`); a workout on the watch also brings it back once the phone has been upright and lies on its side again.
- With `Settings > Treadmill > Steer the treadmill from the app` switched on and a treadmill that takes commands, you set the belt from here too:
  - At the bottom left and right of the ring are `−` and `+` for the speed. A tap is the treadmill's smallest step (usually 0.1 km/h).
  - If one of the four fields shows the incline, it has an arrow up and an arrow down on its right. A tap is the treadmill's smallest step. While the treadmill is still on its way, the incline that was asked for stands between the arrows.
  - A key that is held goes on stepping by itself after just under half a second, and the steps add up: about 0.7 km/h per second for the speed, one step every 0.4 seconds for the incline. One hold makes the belt faster by 3 km/h at most; for more, let go and press again. Slower is not limited.
  - A speed set by hand stands in the ring as `by hand` in the place of `Target`. It holds until the next step of the workout; if the app follows the workout, it then sets that step's pace again.
  - While the belt stands, the keys are pale and do nothing: starting and stopping is done on the console.
- The view needs a running session with a treadmill, but neither the treadmill control nor following: whoever operates the belt by hand still sees the step and the remaining time. Without the control it has no keys for the belt.
- The system bars are hidden and come back with a swipe from the edge. The font size of the system does not change the view, because it already uses the whole screen.

Where the steps come from, and what about them has not been tried on a watch yet, is in the section before. The digits are set in Barlow Condensed (SIL Open Font License, see `app/androidApp/licenses/BarlowCondensed-OFL.txt`).

### Values from the watch

The watch sends its heart rate to the phone, and with it the power and cadence it has for the running activity. That includes sensors paired with the watch, such as a chest strap or power meter pedals. The app does not connect to these sensors itself.

- During a session the dashboard card `Sources` shows the value of the equipment and of the watch side by side for heart rate, power and cadence, as far as one of the two delivers it. Tap one to use it. The same choice is under `Settings > Data sources`, kept separately for each equipment type.
- The default is heart rate from the watch, power and cadence from the equipment.
- The chosen value is used for the display, the history and the values sent to the watch.
- If the chosen source delivers nothing for 5 seconds, the value stays empty; the heart rate of the watch stays for 15 seconds, because it changes slowly and the watch's messages can take seconds to arrive. The app does not switch to the other source by itself.
- Both values are stored with the session. For a session that contains both, the history shows the average of each and the difference.
- The charts of heart rate, power and cadence show both sources, on the dashboard and in the history: the chosen source in black (white on a dark screen), the other one in red and a little thinner, each named in the legend. Behind the average of the chosen source, that of the other one stands in brackets and in red, as in `avg 148 (150) spm`; a session lists these averages the same way under `All values`. In the history the chosen source is the one chosen now for that equipment type.
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

#### With a relay on a second phone

The same relay as an Android app, for a spare phone that stays near the treadmill: FTMS Relay (`app/relayApp`).

    treadmill --FTMS--> phone (bridge app) --foot pod--> second phone (FTMS Relay) --foot pod--> watch

1. Install FTMS Relay on the second phone (`ftms-relay-android-v1.1.0-debug.apk` from the release, or `relayApp-debug.apk` built from source), open it, tap `Start relay` and allow "Nearby devices". The second phone must not be one the watch is paired with.
2. Tap `Allow running in the background` if the app offers it. On phones that decide themselves which apps may launch (Honor, Huawei, Xiaomi), also allow the app to launch automatically in the system settings; otherwise the relay does not come back after a restart of the phone, and you have to open the app once.
3. Continue with steps 2 to 4 above; on the watch, pick the second phone as the foot pod.

The app shows whether it has found the bridge's phone, whether a watch receives the measurements and how many went through. It finds the bridge's sensor by the model that sensor reports, so other foot pods in range, such as a chest strap, are left alone. Its events are written to `files/logs/relay.log` in the app's data directory.

Tested with an Honor 70 (Android 14) and a computer that played the bridge and the watch: every measurement arrived unchanged, about 60 ms later. A Forerunner 970 found the phone as a foot pod and bonded with it when the sensor was added (Android may ask on the second phone to confirm the pairing). An Android phone is visible under a random Bluetooth address that is a new one each time the relay starts; the bonded watch found it again under a new address. A complete training through the second phone is still to be done.

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
- A session that the watch started is led by the watch: the app shows no `Pause` and `Stop` for it, neither in the app nor in its notification, and you pause and end it on the watch. If the app has not heard from the watch for 20 seconds, the buttons come back, so a session can still be ended when the watch is out of reach.
- For a session you started in the app, `Pause` and `Stop` in the app keep working. What you do there stays until the state of the activity on the watch changes again.
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
- Check `Settings > Logs` in the Android app.
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
  :app:androidApp:assembleDebug \
  :app:relayApp:testDebugUnitTest \
  :app:relayApp:lintDebug \
  :app:relayApp:assembleDebug
```

The debug APKs are written to:

```text
app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
app/relayApp/build/outputs/apk/debug/relayApp-debug.apk
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
app/relayApp/         Android relay app that passes the running sensor on from a second phone
app/shared/           Kotlin Multiplatform FTMS parser, smoothing, storage models
docs/                 Connect IQ Store submission docs and assets
tools/ftmsProbe/      Linux FTMS validation probe
```

Useful technical references:

- [Changelog](CHANGELOG.md)
