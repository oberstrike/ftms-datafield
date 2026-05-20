param(
    [string]$AppName = $(if ($env:APP_NAME) { $env:APP_NAME } else { "FtmsDataField" })
)

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $ProjectDir "build\outputs"
$OutFile = Join-Path $OutDir "$AppName.iq"

if (-not (Get-Command monkeyc -ErrorAction SilentlyContinue)) {
    throw "monkeyc not found. Add the active Garmin Connect IQ SDK bin directory to PATH."
}

if (-not $env:GARMIN_DEVELOPER_KEY) {
    throw "GARMIN_DEVELOPER_KEY is not set."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Set-Location $ProjectDir
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*.prg") -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*.prg.debug.xml") -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*-fit_contributions.json") -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $OutDir "FtmsDataField-*-*-settings.json") -ErrorAction SilentlyContinue

Write-Host "Exporting Connect IQ package..."
& monkeyc -f monkey.jungle -y $env:GARMIN_DEVELOPER_KEY -o $OutFile -e -w
Write-Host "Exported: $OutFile"
