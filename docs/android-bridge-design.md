# Android FTMS Bridge Design

## Goal

Build an Android companion app that reads FTMS treadmill data from the Darwin TM70 Touch / `FS-BC11B7` and forwards clean samples to the Garmin Connect IQ data field.

The app is a bridge, not a treadmill controller. It must not write to FTMS Control Point `0x2AD9` or vendor characteristic `FFF2`.

## User Experience

The app opens directly to a bridge dashboard:

```text
FTMS Bridge

Treadmill       FS-BC11B7    Connected
Garmin Watch    FR970        Connected

Speed           6.00 km/h
Incline         3.0 %
Distance        0.21 km
Ascent          6.3 m
Elapsed         08:22

[ Start Bridge / Stop Bridge ]
```

Expected user flow:

1. Add the Connect IQ data field to the treadmill activity on the watch.
2. Start the treadmill activity on the watch.
3. Start the treadmill belt.
4. Open the Android bridge app.
5. Tap `Start Bridge`.
6. App scans for `FS-BC11B*`, connects, subscribes to FTMS Treadmill Data `0x2ACD`, and starts forwarding samples to the watch.
7. Watch data field changes from BLE scan/TRYFS status to phone-bridge data and records FIT developer fields.

The first prototype can use automatic device selection:

- treadmill: strongest device with name prefix `FS-BC11B`
- Garmin: first connected Garmin device with the data field app installed

Manual selection can be added after the bridge proves the end-to-end path.

## Workspace Architecture

Use Nx as the monorepo task runner and Gradle/Kotlin Multiplatform as the mobile build system.

Nx should orchestrate the repo; it should not replace Gradle for Kotlin builds or the Garmin Connect IQ compiler for Monkey C builds.

Target layout:

```text
package.json
nx.json
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml

app/
  androidApp/        Android app, Compose UI, Android BLE, Garmin Connect IQ Android SDK
  garminDataField/   Connect IQ data field, Monkey C sources and build scripts
  shared/            Kotlin Multiplatform parser, smoothing, metrics, bridge message model

tools/
  ftmsProbe/         Linux validation probe

artifacts/
  captures/          Local BLE captures, ignored except placeholder
  logs/              Local watch logs, ignored except placeholder
```

Migration should be staged:

1. Add Nx to the existing repo and register current Connect IQ scripts as Nx targets.
2. Add a Gradle/KMP workspace with `app/shared` and Android app shell.
3. Keep the Connect IQ data field under `app/garminDataField` behind a stable Nx target.

Nx projects and targets:

- `garminDataField`
  - `build`: runs `app/garminDataField/scripts/build.sh`
  - `export`: runs `app/garminDataField/scripts/export-iq.sh`
  - `deploy`: optional explicit target, never part of default/CI pipelines
- `shared`
  - Gradle-inferred `build`, `test`, and KMP test tasks
- `androidApp`
  - `build`: runs `:app:androidApp:assembleDebug`
  - `install`: runs `:app:androidApp:installDebug`
  - `test`: runs `:app:androidApp:testDebugUnitTest`
- `ftmsProbe`
  - `scan`: runs `tools/ftmsProbe/ftms_probe.py --no-connect`
  - `report`: runs `tools/ftmsProbe/ftms_probe.py --notify-seconds 30`

Nx configuration:

- Use explicit `project.json` targets so project names stay stable: `androidApp`, `shared`, `garminDataField`, and `ftmsProbe`.
- Keep Gradle as the source of truth for Kotlin and Android compilation.
- Cache build/test/report tasks that produce deterministic outputs.
- Do not cache deploy/sideload/watch-connected tasks.

## Kotlin Multiplatform Boundaries

Use KMP for business logic that can be unit-tested without Android or Garmin dependencies:

- FTMS treadmill packet parser
- sample data model
- distance smoothing and reconciliation
- ascent calculation from distance delta and incline
- phone-to-watch message encoding model

Keep platform code outside KMP:

- Android BLE scan/connect/GATT notification code stays in `app/androidApp`.
- Garmin Connect IQ Android SDK code stays in `app/androidApp`.
- Monkey C watch receiver stays in the Connect IQ app; it cannot consume Kotlin directly.

KMP module shape:

```text
app/shared/
  build.gradle.kts
  src/commonMain/kotlin/
    FtmsTreadmillParser.kt
    TreadmillSample.kt
    TreadmillSampleSmoother.kt
    BridgeMessage.kt
  src/commonTest/kotlin/
    FtmsTreadmillParserTest.kt
    TreadmillSampleSmootherTest.kt
```

Targets for prototype:

- `androidTarget()` for the Android bridge app.
- `jvm()` for fast parser/smoother tests on the developer machine and CI.

iOS can be added later, but it is not part of the first prototype.

## Android Architecture

Use one Android app under `app/androidApp`.

Core modules:

- `TreadmillBleClient`
  - scans without requiring advertised service UUIDs
  - matches name prefix `FS-BC11B`
  - connects with Android BLE GATT
  - discovers services
  - subscribes only to FTMS Treadmill Data `0x2ACD`
  - never writes `0x2AD9`, `FFF2`, or vendor commands
- `FtmsTreadmillParser`
  - lives in `app/shared`
  - parses the same fields as the Monkey C `FtmsParser.parseTreadmill`
  - extracts speed, distance, incline, positive elevation, heart rate, power, elapsed time, and raw flags
- `TreadmillSampleSmoother`
  - lives in `app/shared`
  - maintains smooth cumulative distance from speed/time
  - uses FTMS distance only when it advances plausibly
  - treats duplicate/coarse distance values as stale
  - computes ascent from positive incline and smoothed distance delta
- `GarminBridgeClient`
  - initializes Garmin Connect IQ Android SDK
  - finds connected Garmin devices
  - verifies app id `8eb0b6152ef04aa7a1687c67ce46bfdf`
  - sends one normalized sample per second
- `BridgeRuntime`
  - owns current connection states and latest metrics
  - exposes a simple Compose UI state
- `navigation/*Component`
  - follows the Decompose-style component boundary used by the app shell
  - keeps screen views focused on rendering and user events

## Phone-to-Watch Message

Send a compact dictionary to the Connect IQ data field. The Kotlin representation lives in `app/shared`; the Monkey C receiver mirrors this contract manually.

Required keys:

```text
v       protocol version, int, always 1
type    "ftms"
kind    2 for treadmill
seq     monotonically increasing int
flags   raw FTMS flags
```

Optional metric keys:

```text
speed100    speed km/h * 100, int
dist10      smoothed distance meters * 10, int
ftmsDist    raw FTMS distance meters, int
incl10      incline percent * 10, int
ascent10    bridge-computed ascent meters * 10, int
power       watts, int
hr          bpm, int
elapsed     treadmill elapsed seconds, int
```

Do not send null values. Omit missing metrics.

Distance policy:

- `dist10` is the value the watch should use for ascent and FIT distance developer field.
- `ftmsDist` is diagnostic/raw.
- If FTMS distance moves in coarse 10 m jumps, the bridge keeps `dist10` smooth between jumps.
- If FTMS distance goes backward or freezes, keep integrating from speed/time.

## Watch Data Field Changes

Add a phone bridge receiver to the existing Connect IQ data field:

- add `Communications` permission
- register `Communications.registerForPhoneAppMessages`
- parse bridge dictionary into `FtmsSample`
- call `FtmsState.update(sample)`
- keep existing `FtmsFitWriter` path unchanged

Source priority:

1. Phone bridge sample if received within the last 5 seconds.
2. Direct CIQ BLE for other machines if no fresh bridge sample exists.

For this treadmill, bridge samples should stop the watch from repeatedly trying the failing `FS-BC11B` direct BLE fallback.

Watch status text:

- `PHONE` when bridge samples are fresh
- `PSTALE` when bridge was active but no sample has arrived for more than 5 seconds
- existing `SCAN`, `TRYFS`, `PAIR`, `STALE` statuses remain for direct BLE mode

## Safety Rules

The bridge app must be read-only toward the treadmill:

- allowed: scan
- allowed: connect
- allowed: GATT service discovery
- allowed: subscribe/unsubscribe to `0x2ACD`
- optional later: read FTMS Feature `0x2ACC`, Supported Speed Range `0x2AD4`, Supported Inclination Range `0x2AD5`
- forbidden: write FTMS Control Point `0x2AD9`
- forbidden: write vendor `FFF2`
- forbidden: send speed/incline/control commands

The Android app should disconnect on `Stop Bridge`, app background timeout, or BLE error.

## Testing

Nx task expectations:

```bash
npx nx test shared
npx nx build androidApp
npx nx build garminDataField
npx nx run ftmsProbe:report
```

Parser tests:

- idle packet:
  - `84 04 00 00 00 00 00 00 00 00 00 00 00 00`
  - expected speed `0.00`, distance `0`, elapsed `0`
- moving packet:
  - `8c 04 58 02 a0 00 00 1e 00 00 00 0b 00 00 00 00 d9 01`
  - expected speed `6.00`, distance `160`, incline `3.0`, elapsed `473`
- coarse distance packet sequence:
  - verify bridge distance remains smooth between `160`, `170`, `180`, ... raw updates
- malformed/truncated packet:
  - parser returns partial data without crashing

Manual end-to-end test:

1. Build and sideload updated Connect IQ data field.
2. Install Android debug app.
3. Start treadmill activity on FR970.
4. Start treadmill belt at 6 km/h, 3% incline.
5. Start Android bridge.
6. Confirm Android app shows live FTMS data.
7. Confirm watch status changes to `PHONE`.
8. Confirm watch ascent increments smoothly.
9. Stop Android bridge and confirm watch changes to `PSTALE` after timeout.

## Prototype Acceptance Criteria

The first prototype is successful when:

- Android connects to `FS-BC11B7` without any treadmill writes.
- Android displays live speed and incline from FTMS.
- Android sends one bridge message per second to the watch.
- Watch data field displays bridge-fed ascent/status.
- FIT developer fields are written from bridge samples during a recorded activity.
- Disconnect/reconnect can recover without restarting the treadmill.

## References

- Nx Gradle plugin: https://nx.dev/docs/technologies/java/gradle/introduction
- Nx task runner: https://nx.dev/docs/features/run-tasks
- Android Kotlin Multiplatform overview: https://developer.android.com/kotlin/multiplatform
- Android KMP setup: https://developer.android.com/kotlin/multiplatform/setup
