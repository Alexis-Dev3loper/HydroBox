[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$onWindows = [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
$wrapperName = if ($onWindows) { "gradlew.bat" } else { "gradlew" }
$wrapper = Join-Path $repoRoot $wrapperName

if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Gradle Wrapper is missing: $wrapper"
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "JDK 17 is required. Configure JAVA_HOME/PATH before running the Mobile baseline."
}

Push-Location $repoRoot
try {
    & $wrapper --no-daemon testDebugUnitTest lintDebug assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Mobile baseline failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host "Mobile baseline: unit tests, lint and debug assembly OK"
