# IntelliJ Setup for the FTMS Garmin Data Field

This project can be edited in IntelliJ IDEA. Garmin's official tooling is still centered around the Connect IQ SDK, `monkeyc`, `monkeydo`, `connectiq`, and the official VS Code extension. In IntelliJ, use the IDE as the editor and run the Garmin SDK commands through the terminal or shell run configurations.

## 1. Install Garmin Connect IQ SDK

1. Download and install the Garmin Connect IQ SDK Manager.
2. Use the SDK Manager to install the latest SDK and the device files for your watch, for example `fr970`.
3. Ensure these commands work in a new terminal:

```bash
monkeyc --version
connectiq
monkeydo
```

If the commands are not found, add the active Connect IQ SDK `bin` directory to your `PATH`.

## 2. Developer key

The compiler needs a Connect IQ developer key. Set it as an environment variable before building:

macOS/Linux:

```bash
export GARMIN_DEVELOPER_KEY="$HOME/garmin-dev/ciq.key"
```

Windows PowerShell:

```powershell
$env:GARMIN_DEVELOPER_KEY="C:\Users\YourName\garmin-dev\ciq.key"
```

Keep this key safe. If you publish an app, you need the same key for future updates.

## 3. IntelliJ plugin

Optional but useful: install the JetBrains Marketplace plugin `Monkey C (Garmin Connect IQ)`. It gives syntax highlighting and basic Monkey C support. Treat it as unofficial; use the scripts below as the source of truth for builds.

## 4. Open the project

1. IntelliJ IDEA -> Open -> select this folder.
2. Open the terminal in IntelliJ.
3. Build for Forerunner 970:

macOS/Linux:

```bash
DEVICE=fr970 ./app/garminDataField/scripts/build.sh
```

Windows PowerShell:

```powershell
$env:DEVICE="fr970"
.\app\garminDataField\scripts\build.ps1
```

Output:

```text
app/garminDataField/build/outputs/FtmsBridgeField-fr970.prg
```

Copy the `.prg` file to the watch:

```text
GARMIN/APPS/
```

## 5. Run in simulator

macOS/Linux:

```bash
DEVICE=fr970 ./app/garminDataField/scripts/run-sim.sh
```

Windows PowerShell:

```powershell
$env:DEVICE="fr970"
.\app\garminDataField\scripts\run-sim.ps1
```

BLE behavior with real FTMS devices must still be tested on the real watch. The simulator is useful for compile/UI checks, but Bluetooth testing is often more reliable on-device.

## 6. Export store package

macOS/Linux:

```bash
./app/garminDataField/scripts/export-iq.sh
```

Windows PowerShell:

```powershell
.\app\garminDataField\scripts\export-iq.ps1
```

Output:

```text
app/garminDataField/build/outputs/FtmsDataField.iq
```
