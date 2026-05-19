param(
    [string]$Device = $(if ($env:DEVICE) { $env:DEVICE } else { "fr970" }),
    [string]$Variant = $(if ($env:VARIANT) { $env:VARIANT } else { "ascent" })
)

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $PSScriptRoot
$PrgFile = Join-Path $ProjectDir "build\outputs\FtmsDataField-$Variant-$Device.prg"

& "$PSScriptRoot\build.ps1" -Device $Device -Variant $Variant

if (-not (Get-Command connectiq -ErrorAction SilentlyContinue)) {
    throw "connectiq not found. Add the active Garmin Connect IQ SDK bin directory to PATH."
}

if (-not (Get-Command monkeydo -ErrorAction SilentlyContinue)) {
    throw "monkeydo not found. Add the active Garmin Connect IQ SDK bin directory to PATH."
}

Start-Process connectiq
Start-Sleep -Seconds 2

Write-Host "Running $PrgFile on simulator device $Device..."
& monkeydo $PrgFile $Device
