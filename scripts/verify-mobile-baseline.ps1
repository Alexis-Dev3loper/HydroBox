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

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js 22 is required to verify the embedded camera receiver."
}

& node --test (Join-Path $repoRoot "tests/js/camera-web-document.test.mjs")
if ($LASTEXITCODE -ne 0) {
    throw "Camera media boundary failed with exit code $LASTEXITCODE"
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

Write-Host "Mobile baseline: camera media boundary, unit tests, lint and debug assembly OK"
