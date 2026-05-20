param(
    [string]$Device = $(if ($env:DEVICE) { $env:DEVICE } else { "fr970" })
)

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $ProjectDir "build\outputs"

if (-not (Get-Command monkeyc -ErrorAction SilentlyContinue)) {
    throw "monkeyc not found. Add the active Garmin Connect IQ SDK bin directory to PATH."
}

if (-not $env:GARMIN_DEVELOPER_KEY) {
    throw "GARMIN_DEVELOPER_KEY is not set. Example: `$env:GARMIN_DEVELOPER_KEY='C:\Users\YourName\garmin-dev\ciq.key'"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Set-Location $ProjectDir
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*.prg") -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*.prg.debug.xml") -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*-fit_contributions.json") -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*-settings.json") -ErrorAction SilentlyContinue

$outFile = Join-Path $OutDir "FtmsBridgeField-$Device.prg"

Write-Host "Building FTMS Bridge Field for $Device..."
& monkeyc -f monkey.jungle -d $Device -y $env:GARMIN_DEVELOPER_KEY -o $outFile -w
Write-Host "Built: $outFile"
Write-Host "For sideloading, copy this .prg to GARMIN/APPS/ on the watch."
