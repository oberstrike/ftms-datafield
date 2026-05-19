param(
    [string]$Device = $(if ($env:DEVICE) { $env:DEVICE } else { "fr970" }),
    [string]$Variant = $(if ($env:VARIANT) { $env:VARIANT } else { "all" })
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

node tools\garmin-variants.mjs prepare

node tools\garmin-variants.mjs list | ForEach-Object {
    $parts = $_ -split "\|"
    $variantKey = $parts[0]
    $variantLabel = $parts[1]

    if ($Variant -ne "all" -and $Variant -ne $variantKey) {
        return
    }

    $jungleFile = Join-Path $ProjectDir "build\generated\garmin-variants\$variantKey\monkey.jungle"
    $outFile = Join-Path $OutDir "FtmsDataField-$variantKey-$Device.prg"

    Write-Host "Building $variantLabel ($variantKey) for $Device..."
    & monkeyc -f $jungleFile -d $Device -y $env:GARMIN_DEVELOPER_KEY -o $outFile -w
    Write-Host "Built: $outFile"
}

Write-Host "For sideloading, copy this .prg to GARMIN/APPS/ on the watch."
