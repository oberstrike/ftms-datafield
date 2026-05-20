# FTMS Datafield

[Deutsch](README.de.md)

FTMS Datafield bridges Bluetooth FTMS fitness equipment to Garmin Connect IQ data fields.

The project contains two parts:

- an Android bridge app that connects to FTMS equipment, stores sessions locally, and forwards live samples to a Garmin watch through the Garmin Connect IQ companion channel
- a Garmin Connect IQ data field source project that receives bridge samples and writes custom FIT developer fields

The bridge is designed as a read-only data bridge. It does not write to the FTMS Control Point `0x2AD9` and does not send vendor control commands to the treadmill.

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

## Supported FTMS Data

The parser supports these standard FTMS characteristics:

- Indoor Bike Data `0x2AD2`
- Treadmill Data `0x2ACD`
- Cross Trainer / Elliptical Data `0x2ACE`

The current Android bridge has been tested primarily with the Darwin TM70 Touch / `FS-BC11B7` treadmill.

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

- JDK 17
- Android SDK
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
docs/                 Design and validation notes
tools/ftmsProbe/      Linux FTMS validation probe
```

Useful technical references:

- [Android bridge design](docs/android-bridge-design.md)
- [FTMS validation guide](docs/ftms-validation.md)
- [FS-BC11B7 validation result](docs/ftms-validation-result.md)
- [Changelog](CHANGELOG.md)
