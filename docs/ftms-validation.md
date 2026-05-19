# FTMS Validation Pass

This validation checks whether the Darwin TM70 Touch / `FS-BC11B7` behaves like a normal BLE FTMS peripheral for Linux and phones. It does not deploy to the watch and does not write treadmill control or vendor commands.

## Local Setup

BlueZ and the probe venv are expected on this machine:

```bash
sudo apt-get install -y bluez python3-venv
python3 -m venv .venv-ftms-probe
. .venv-ftms-probe/bin/activate
python -m pip install -r tools/ftmsProbe/requirements-ftms-probe.txt
```

Confirm the adapter:

```bash
systemctl is-active bluetooth
bluetoothctl show
```

## Capture With BlueZ

Terminal 1:

```bash
mkdir -p artifacts/captures
sudo btmon -w artifacts/captures/fs-bc11b7-$(date +%Y%m%d-%H%M%S).btsnoop
```

Terminal 2:

```bash
. .venv-ftms-probe/bin/activate
python tools/ftmsProbe/ftms_probe.py \
  --target-prefix FS-BC11B \
  --notify-seconds 45 \
  --jsonl artifacts/captures/fs-bc11b7-samples.jsonl
```

Run it twice:

1. treadmill idle, with Zwift/Kinomap/FitShow/nRF disconnected
2. belt moving, again with other apps disconnected

The probe scans without a service UUID filter, connects to the selected device, prints the discovered GATT table, and subscribes only to FTMS Treadmill Data `0x2ACD`.

## Phone Capture

Use nRF Connect on the phone:

1. Scan for `FS-BC11B7`.
2. Capture advertisement details, service UUIDs, manufacturer data, and scan response if shown.
3. Connect without writing control values.
4. Save screenshots or export logs for:
   - FTMS service `0x1826`
   - Treadmill Data `0x2ACD`
   - Fitness Machine Control Point `0x2AD9`
   - vendor service `FFF0`, `FFF1`, and `FFF2`
5. Repeat idle and belt-moving states.

## Interpretation

- Linux connects and receives `0x2ACD` notifications: proceed with the Android bridge prototype.
- Phone works but Linux fails: investigate treadmill state, single-central behavior, or adapter/BlueZ compatibility, but still favor Android bridge.
- Neither Linux nor phone receives FTMS notifications without writes: pause bridge implementation and reassess whether vendor protocol or a firmware setting is required.
