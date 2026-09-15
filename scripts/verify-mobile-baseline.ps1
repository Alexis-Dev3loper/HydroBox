[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repoRoot "gradlew.bat"

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
