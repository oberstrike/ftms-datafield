# Changelog

## Unreleased

Android bridge:

- Fills FTMS notifications that carry only part of a record (the "More Data" flag, alternating field sets) with the last values, no older than 3 seconds, so fields no longer blank between notifications.
- Reads treadmill pace fields in both widths found in the field: 2 bytes as in the current Bluetooth specification and 1 byte as sent by many treadmills. The width is chosen from the packet length.
- Handles all Bluetooth callbacks on the main thread and ignores callbacks from a connection that was already replaced, which removes a class of races during reconnects.
- Keeps the connection when a single FTMS characteristic refuses notifications, as long as one data characteristic works.
- Reports a failed scan separately from a failed connection, stops scanning after 30 seconds, and reports "Bluetooth off" during connect instead of waiting silently.
- Stops forwarding a sample to the watch once it is older than 5 seconds, so the watch no longer shows a frozen value as live.
- Tells the watch about pause and resume and repeats the pause signal every 2 seconds.
- Adds an optional auto start (Settings > Bridge behavior, off by default): while the app is open, it connects to the selected FTMS equipment as soon as that is switched on and starts the bridge when the equipment moves. After a session it waits until the equipment has stopped before it starts again.
- Adds an equipment type (Devices tab): treadmill, indoor cycling or cross trainer, preselected from the equipment's advertising data where it names one. The dashboard keeps its own live values and charts per type, and each session is stored with its type. The history shows the type, leaves out values that do not apply, and can be filtered by type. Existing sessions get their type from their samples when the database is updated.
- Adds a target power for indoor bikes: a dashboard card with plus and minus and presets, set through the FTMS Control Point. Presets and step size are configured under Settings > Indoor cycling. This is the first time the app writes to equipment, and only for the type indoor cycling; treadmills and cross trainers are still only read.
- Makes the target power of an indoor bike exact: tap the target to type it as a number, and step it by 1 W as well as by the step size from the settings. The target is sent exact to the watt even to a bike that reports coarser steps; the app keeps to those steps only once the bike refused a target between them. The card shows the measured power next to the target.
- Holds the target power of an indoor bike through a session: the app sends its target again after a reconnect and when a paused session is resumed. A new session starts without a target and sends nothing until one is set; the target last set in the app on that bike is filled in when one is typed. A target set elsewhere, on the bike itself or by a watch that controls it, is taken over when the bike announces it; the app then only shows it and sends nothing on its own until a target is set in the app again. "No target" in the input dialog stops the app from setting one.
- Redraws the charts of the dashboard and the history: a value axis on round numbers, the time below, the latest value with average and maximum above, and a readout of the value under a finger. The power chart of an indoor bike shows the target power as a stepped line. Running averages in the dashboard are now computed over the whole session even when the chart shows only its last minutes.
- Shows on the dashboard only what belongs to the equipment: the resistance is no longer offered for treadmills, the cadence is called step rate (spm) on a treadmill and cadence (rpm) otherwise, and during a session tiles, charts and source rows for values that neither the equipment nor the watch delivers are left out.
- Rearranges the dashboard: start, pause and stop are in a bar fixed above the navigation, with the state of the bridge and the session time, and a session opens on the charts, each with its latest value, followed by the target power of an indoor bike, the live values, the sources and the status. Without a session the dashboard shows the status and the last session. The switch between values and charts is gone.
- Removes the distance chart from the dashboard and the history; the distance stays a live value and a session total.
- Keeps the heart rate received from the watch for 15 seconds instead of 5 before it counts as missing, so it no longer blanks when the watch's messages arrive late. Power and cadence from the watch still count for 5 seconds.
- Applies the treadmill power estimate to treadmills only.
- Takes power and cadence from the watch as well as heart rate, so sensors paired with the watch (a chest strap, power meter pedals) can be used. For heart rate, power and cadence you choose per equipment type whether the equipment or the watch is used: on the new dashboard card `Sources`, which shows both live values side by side, or under `Settings > Data sources`. The chosen value is used for the display, the history and the values sent to the watch. If the chosen source delivers nothing for 5 seconds, the value stays empty; the app does not switch to the other source by itself. The heart rate source setting under `Settings > Bridge behavior` moved to the new page and keeps its value.
- Stores what the equipment and the watch each reported with every sample. The history shows the average of both, their difference, and a chart with both lines for sessions that contain both.
- Adds a running sensor for the watch (Settings > Bridge behavior, off by default): while the bridge runs with a treadmill, the phone is visible as a Bluetooth foot pod and sends the treadmill's speed, total distance and step rate once a second. A watch paired with it can take pace and distance for its own activity from the treadmill; a Connect IQ data field cannot replace them. A watch keeps one connection to the phone, so by itself the sensor excludes the data field: it connects only while the phone connection of the watch is off, and in that mode the bridge starts without reaching the data field. With the relay below, both work together. Needs the Android permission `BLUETOOTH_ADVERTISE`, which belongs to the same "Nearby devices" group as the existing ones. Tested with a Forerunner 970, which took over speed and step rate from it.
- Reads the total distance of a treadmill's Running Speed and Cadence service in tenths of a metre, as the Bluetooth definition says, instead of metres. This only matters for treadmills that report their distance through that service alone.
- Follows the activity on the watch (Settings > Bridge behavior, on by default): the session starts when the activity starts, pauses when the activity is stopped, and ends when the activity is saved or discarded. Needs the data field from this version and the app open on the phone when the activity starts.
- Adds waiting for the watch in the background (Settings > Bridge behavior, off by default): once the app was opened, it stays in its foreground service with a notification, so a session starts with the activity on the watch without opening the app again. The notification ends the waiting; after a session the app waits for the next activity.
- Tells the watch from the start of a session, and while the equipment is silent, that the bridge runs without data (`ftms_wait`, every 2 seconds). A data field that had connected to the equipment itself then lets go of it; before, the phone could not get the equipment's only connection.
- Sends to each installed variant of the data field only once the previous message to it was answered, and leaves a variant alone for 10 seconds after a failure. Garmin Connect reports all variants as installed while one runs; samples sent every second to all of them piled up in Garmin Connect by the thousand until no message got through in either direction.
- Works out a step rate for treadmills with the FitShow protocol from the step count in their vendor packets, since their FTMS data carries none. It appears as the equipment's step rate in the app and goes to the watch through the running sensor, which otherwise reports a step rate of zero that the watch takes over.
- Adds an auto stop for treadmills (Settings > Bridge behavior, on by default): a session ends by itself once the belt has stood still for 30 seconds or the treadmill has been out of reach for 90 seconds, as of the moment the belt stopped. While the watch reports an activity of its own, the watch decides instead. Sessions on an indoor bike or cross trainer do not end by themselves.
- Counts the distance and ascent of a session from its own start. A session that began on equipment already in use used to take over the equipment's whole distance, which also made the average speed wrong. A reset of the equipment during a session no longer loses what was counted.
- Keeps the diagnostic log of a session open when it starts within 2.5 seconds after the previous one was stopped; the delayed closing of the old log used to close the new one.
- Starts the foreground service with the `connectedDevice` type and stops cleanly when Android refuses to start it.
- Shows a hint with an install or update button when Garmin Connect is missing or too old, and tries again when the app returns to the foreground or "Refresh" is tapped. The Garmin SDK dialog it replaces left the app unresponsive after "Cancel".
- Formats numbers in the language chosen in the app rather than the system language.
- Follows the system light or dark theme, uses dynamic colors on Android 12 and later, and draws edge to edge.
- Adds an adaptive launcher icon with a monochrome variant.
- Excludes the app data from cloud backup, and the debug logs from device transfer.
- Requests the notification permission together with the Bluetooth permissions on Android 13 and later, without requiring it to start the bridge.

Tools:

- Adds a relay for the running sensor (`tools/footPodRelay/foot_pod_relay.py`, Linux with BlueZ): a computer takes the phone's sensor and offers it to the watch under its own Bluetooth address, so the watch can use the sensor while it stays connected to the phone through Garmin Connect and the data field keeps working. Tested with a Forerunner 970 and BlueZ 5.82; on the first attempt the watch's firmware crashed once, for a reason not yet known.

Garmin data field:

- Shows `PAUSE` while the phone bridge is paused.
- Ignores samples that arrive out of order.
- Sends the watch heart rate to the phone only while the bridge is active.
- Sends the power and cadence the watch has for the activity to the phone together with the heart rate. An older Android app ignores the new values; an older data field keeps sending heart rate only.
- Reports the state of the activity timer (recording, stopped, auto pause, ended) to the phone, so the phone's session can follow the activity. Without a running phone bridge it does so when the state changes and every 5 seconds while the activity records; a watch connected to the equipment itself repeats it every 30 seconds after the first minute.
- Shows `PWAIT` while the phone bridge runs without data from the equipment, and leaves the equipment's connection to the phone during that time instead of connecting to it itself.
- Reads treadmill pace fields in both widths, matching the Android parser.

Build:

- Updates Gradle to 9.9.2 (with a checksum for the wrapper download), the Android Gradle plugin to 9.5.2, Kotlin to 2.4.21, Compose to 1.13.0, Material 3 to 1.5.0, Decompose to 3.6.2, SQLDelight to 2.4.1 and Vico to 3.3.1.
- Compiles against Android SDK 37.1. The target SDK stays at 36.
- Updates Nx to 24 and the FTMS probe to bleak 3.1.0.
- Removes the GitHub Actions release workflow. Releases are built locally.

## v1.0.1

- Persists running workout sessions while they are active so interrupted sessions can be recovered with samples and stats.
- Avoids stopping foreground-service protection for a newly started bridge while a previous session is still finishing.
- Adds nested settings pages for bridge behavior, dashboard metrics, timing, language, debug logging, remembered devices, and power estimation.
- Adds configurable dashboard metrics, live chart selections, and session history reporting.
- Adds a Settings Open Source view with direct library versions, licenses, a Garmin SDK notice, and the Android app version.

## v1.0.0

Initial public Android bridge release.

- Provides a debug-signed Android APK for sideloading.
- Connects to FTMS treadmill equipment from the phone and forwards samples to the Garmin Connect IQ data field.
- Stores workout sessions locally with history charts and diagnostic logs.
- Includes English and German Android UI strings.
- Adds Connect IQ Store package metadata and listing copy for the Garmin data field.
- Adds a configurable primary watch metric for the single public Connect IQ data field.
- Removes legacy metric-specific Garmin variant builds from the public workflow.
- Garmin watch artifacts are prepared for Connect IQ Store submission; until review is complete, the Connect IQ data field remains source-build/sideload only.
