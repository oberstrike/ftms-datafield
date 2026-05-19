# FS-BC11B7 Validation Result

Date: 2026-05-18

## Result

Linux/BlueZ/Bleak can connect to `FS-BC11B7`, discover the full FTMS GATT table, and receive FTMS Treadmill Data notifications.

This confirms the treadmill behaves as a normal BLE FTMS peripheral for a generic Linux central. The remaining Garmin watch problem is therefore much more likely to be a Connect IQ BLE interoperability issue than a treadmill-side lack of FTMS.

## Evidence

Scan-only probe found:

```text
TARGET 1A:C0:EF:BA:83:9E name=FS-BC11B7
services=['0000fff0-0000-1000-8000-00805f9b34fb', '00001826-0000-1000-8000-00805f9b34fb']
manufacturer_data={'0x0422': '02 00 1a c0 ef 3a 00 00 00 2b'}
service_data={'00001826-0000-1000-8000-00805f9b34fb': '01 00 01'}
```

The `btmon` trace shows the primary advertisement still contains only flags, manufacturer data, and name:

```text
02 01 06 0d ff 22 04 02 00 1a c0 ef 3a 00 00 00 2b 0a 09 46 53 2d 42 43 31 31 42 37
```

The scan response contains both service UUIDs and FTMS service data:

```text
05 03 f0 ff 26 18 06 16 26 18 01 00 01
```

Decoded:

```text
FFF0 vendor service
1826 Fitness Machine service
1826 service data: 01 00 01
```

GATT discovery found:

```text
SERVICE 00001826-0000-1000-8000-00805f9b34fb [FTMS]
  CHAR 00002ad3-0000-1000-8000-00805f9b34fb props=['read', 'notify']
  CHAR 00002ada-0000-1000-8000-00805f9b34fb props=['notify']
  CHAR 00002ad9-0000-1000-8000-00805f9b34fb props=['write', 'indicate']
  CHAR 00002acc-0000-1000-8000-00805f9b34fb props=['read']
  CHAR 00002ad4-0000-1000-8000-00805f9b34fb props=['read']
  CHAR 00002acd-0000-1000-8000-00805f9b34fb props=['notify'] [TREADMILL DATA]
  CHAR 00002ad5-0000-1000-8000-00805f9b34fb props=['read']
SERVICE 0000fff0-0000-1000-8000-00805f9b34fb [VENDOR FFF0]
  CHAR 0000fff2-0000-1000-8000-00805f9b34fb props=['write-without-response']
  CHAR 0000fff1-0000-1000-8000-00805f9b34fb props=['notify']
```

A 20-second idle notification run received 40 packets from `0x2ACD`; sample:

```text
RX flags=0x0484 raw=84 04 00 00 00 00 00 00 00 00 00 00 00 00 speed=0.00km/h dist=0m elapsed=0s
```

The moving run received 90 packets over 45 seconds while the treadmill was running at 6.00 km/h and 3.0% incline; sample:

```text
RX flags=0x048c raw=8c 04 58 02 00 00 00 1e 00 00 00 00 00 00 00 00 32 00 speed=6.00km/h dist=0m incl=3.0% elapsed=50s
```

The moving packets prove the FTMS stream contains useful speed, incline, and elapsed-time values. The treadmill advertises the FTMS total-distance field, but it remained `0m` during the first moving capture.

A follow-up capture after the treadmill had run longer showed `dist=50m`, but the value stayed fixed at `50m` for the full 30-second sample window while speed stayed `6.00km/h` and elapsed time advanced from `238s` to `267s`; sample:

```text
RX flags=0x048c raw=8c 04 58 02 32 00 00 1e 00 00 00 03 00 00 00 00 ee 00 speed=6.00km/h dist=50m incl=3.0% elapsed=238s
```

So the Android bridge should treat FTMS distance as unreliable/stale and synthesize cumulative distance from speed/time whenever the reported FTMS distance does not advance monotonically.

A later 30-second report showed distance advancing, but only in coarse 10 m steps: `160m` at elapsed `473s`, `170m` at `477s`, `180m` at `483s`, `190m` at `489s`, `200m` at `495s`, and `210m` at `501s`. This confirms the bridge can use FTMS distance when it advances, but should smooth/fallback between coarse updates.

The trace shows only CCCD writes to enable/disable notifications for `0x2ACD`. It does not show writes to FTMS Control Point `0x2AD9` or vendor `FFF2`.

## Next Step

Proceed with the Android bridge prototype using the validated BLE path. The bridge should maintain its own cumulative distance from speed/time, reconcile to FTMS distance only when it advances plausibly, and use the smoothed cumulative distance for incline-based ascent.
