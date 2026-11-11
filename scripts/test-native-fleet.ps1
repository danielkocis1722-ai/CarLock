param(
    [Parameter(Mandatory=$true)][string]$JavaPath,
    [Parameter(Mandatory=$true)][string]$KotlinHome,
    [Parameter(Mandatory=$true)][string]$JsonJar
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$sources = Join-Path $workspace 'modules/carlock-bluetooth/android/src/main/java/expo/modules/carlockbluetooth'
$testOutput = Join-Path ([System.IO.Path]::GetTempPath()) ('locksora-fleet-' + [guid]::NewGuid() + '.jar')
& $JavaPath -cp (Join-Path $KotlinHome 'lib/*') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler `
    -kotlin-home $KotlinHome -classpath $JsonJar -include-runtime -d $testOutput `
    (Join-Path $sources 'VehicleFleet.kt') (Join-Path $sources 'FleetCodec.kt') `
    (Join-Path $workspace 'tests/native/FleetTests.kt')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $JavaPath -cp ($testOutput + [System.IO.Path]::PathSeparator + $JsonJar) expo.modules.carlockbluetooth.FleetTestsKt
exit $LASTEXITCODE
