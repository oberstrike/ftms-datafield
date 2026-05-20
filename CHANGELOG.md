# Changelog

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
