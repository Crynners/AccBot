<#
.SYNOPSIS
    Generate Google Play screenshots for all device profiles and locales.

.DESCRIPTION
    Captures 8 screenshots x 3 device profiles x N locales using instrumented tests.
    Each emulator is booted with swiftshader (software GPU) and cold boot to ensure
    screencap works correctly. Locales are switched externally via adb.

    Device profiles:
      phone  - Pixel 6       (1080x2400, 420dpi)  portrait
      7inch  - Nexus 7 2013  (1200x1920, 323dpi)  portrait
      10inch - Pixel C       (2560x1800, 308dpi)  landscape

    Locales are auto-detected from res/values-* directories (+ "en" as default).

.PARAMETER DeviceFilter
    Run only specific device(s). Comma-separated: phone,7inch,10inch. Default: all.

.PARAMETER LocaleFilter
    Run only specific locale(s). Comma-separated: en,cs. Default: all detected.

.PARAMETER SkipBuild
    Skip Gradle build (use already-installed APKs). Useful for re-running after a failure.

.EXAMPLE
    .\scripts\generate-screenshots.ps1
    .\scripts\generate-screenshots.ps1 -DeviceFilter phone
    .\scripts\generate-screenshots.ps1 -DeviceFilter 7inch,10inch -LocaleFilter cs
    .\scripts\generate-screenshots.ps1 -SkipBuild
#>

param(
    [string]$DeviceFilter = "",
    [string]$LocaleFilter = "",
    [switch]$SkipBuild
)

# Note: using "Continue" (default) because native commands (adb, gradle) write
# informational messages to stderr which would cause terminating errors with "Stop".
# We check $LASTEXITCODE explicitly for actual failures instead.
$ErrorActionPreference = "Continue"

# --- Paths -----------------------------------------------------------------------
$ProjectRoot   = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$AndroidDir    = Join-Path $ProjectRoot "accbot-android"
$ScreenshotDir = Join-Path $ProjectRoot "screenshots"
$ResDir        = Join-Path $AndroidDir "app\src\main\res"

$SdkRoot  = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$Adb      = Join-Path $SdkRoot "platform-tools\adb.exe"
$Emulator = Join-Path $SdkRoot "emulator\emulator.exe"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH      = "$env:JAVA_HOME\bin;$($env:PATH)"

$TestClass = "com.accbot.dca.screenshots.ScreenshotCaptureTest"
$AppPackage = "com.accbot.dca"
$Port = 5556

# --- Device definitions -------------------------------------------------------
$AllDevices = @(
    @{ Name = "phone";  Avd = "screenshot_phone";  Folder = "phone"  }
    @{ Name = "7inch";  Avd = "screenshot_7inch";  Folder = "7inch"  }
    @{ Name = "10inch"; Avd = "screenshot_10inch"; Folder = "10inch" }
)

# --- Auto-detect locales ------------------------------------------------------
function Get-Locales {
    $locales = @("en")  # default locale (no suffix directory)
    Get-ChildItem -Path $ResDir -Directory -Filter "values-*" | ForEach-Object {
        $lang = $_.Name -replace "^values-", ""
        # Only include simple language codes (skip region variants like values-en-rUS)
        if ($lang -match "^[a-z]{2}$") {
            $locales += $lang
        }
    }
    return $locales | Sort-Object -Unique
}

# --- Helpers ------------------------------------------------------------------
function Write-Step($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Write-Header($msg) {
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Yellow
    Write-Host "  $msg" -ForegroundColor Yellow
    Write-Host ("=" * 60) -ForegroundColor Yellow
}

function Wait-ForBoot {
    Write-Step "Waiting for emulator-$Port to boot..."
    & $Adb -s "emulator-$Port" wait-for-device
    # Wait for boot_completed property
    do {
        Start-Sleep -Seconds 2
        $bootCompleted = & $Adb -s "emulator-$Port" shell "getprop sys.boot_completed" 2>$null
    } while ($bootCompleted.Trim() -ne "1")
    Write-Host "  Emulator booted." -ForegroundColor Green
}

function Start-EmulatorForDevice($avdName) {
    Write-Step "Booting emulator: $avdName (swiftshader, cold boot)..."
    $emulatorProcess = Start-Process -FilePath $Emulator -ArgumentList @(
        "-avd", $avdName,
        "-port", $Port,
        "-no-window",
        "-no-audio",
        "-no-boot-anim",
        "-no-snapshot-load",
        "-gpu", "swiftshader_indirect"
    ) -PassThru -WindowStyle Hidden
    $script:EmulatorPid = $emulatorProcess.Id
    Wait-ForBoot
}

function Stop-Emulator {
    if ($script:EmulatorPid) {
        Write-Step "Shutting down emulator (PID $($script:EmulatorPid))..."
        & $Adb -s "emulator-$Port" emu kill 2>$null
        Start-Sleep -Seconds 3
        $script:EmulatorPid = $null
    }
}

function Install-Apks {
    Write-Step "Building and installing APKs..."
    Push-Location $AndroidDir
    try {
        & .\gradlew.bat installDebug installDebugAndroidTest 2>&1 | ForEach-Object {
            if ($_ -match "BUILD|FAIL|Installed") { Write-Host "  $_" }
        }
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
    } finally {
        Pop-Location
    }
}

function Run-TestsForLocale($lang, $folder) {
    # Set per-app locale (API 33+)
    if ($lang -eq "en") {
        & $Adb -s "emulator-$Port" shell cmd locale set-app-locales $AppPackage --locales '""' 2>$null
    } else {
        & $Adb -s "emulator-$Port" shell cmd locale set-app-locales $AppPackage --locales $lang 2>$null
    }

    # Clean device screenshots
    & $Adb -s "emulator-$Port" shell "rm -rf /sdcard/Pictures/accbot-screenshots" 2>$null

    # Run instrumented tests
    Write-Step "Running tests: $lang / $folder"
    $output = & $Adb -s "emulator-$Port" shell am instrument -w `
        -e class $TestClass `
        "$AppPackage.test/androidx.test.runner.AndroidJUnitRunner" 2>&1
    $resultLine = ($output | Select-String "OK|FAIL" | Select-Object -Last 1)
    Write-Host "  $resultLine"

    if ($output -match "FAILURES") {
        Write-Host "  TEST FAILED -- see output above" -ForegroundColor Red
        $output | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        return $false
    }

    # Pull screenshots
    $dest = Join-Path $ScreenshotDir "$lang\$folder"
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    & $Adb -s "emulator-$Port" pull "/sdcard/Pictures/accbot-screenshots/" "$dest\" 2>$null

    # Flatten nested directory (adb pull creates accbot-screenshots/ subfolder)
    $nested = Join-Path $dest "accbot-screenshots"
    if (Test-Path $nested) {
        Get-ChildItem $nested -File | Move-Item -Destination $dest -Force
        Remove-Item $nested -Recurse -Force
    }

    # Clean device
    & $Adb -s "emulator-$Port" shell "rm -rf /sdcard/Pictures/accbot-screenshots" 2>$null

    $count = (Get-ChildItem $dest -Filter "*.png").Count
    Write-Host "  Captured $count screenshots -> $lang/$folder/" -ForegroundColor Green
    return $true
}

# --- Main ---------------------------------------------------------------------

# Resolve devices
if ($DeviceFilter) {
    $filterNames = $DeviceFilter -split ","
    $Devices = @($AllDevices | Where-Object { $filterNames -contains $_.Name })
} else {
    $Devices = $AllDevices
}

# Resolve locales
if ($LocaleFilter) {
    $Locales = @(($LocaleFilter -split ",") | ForEach-Object { $_.Trim() })
} else {
    $Locales = @(Get-Locales)
}

$totalScreenshots = 8 * $Devices.Count * $Locales.Count
Write-Header "Screenshot Generation"
Write-Host "  Devices: $($Devices.Name -join ', ')"
Write-Host "  Locales: $($Locales -join ', ')"
$devCount = $Devices.Count
$locCount = $Locales.Count
Write-Host "  Expected: $totalScreenshots screenshots (8 x $devCount x $locCount)"

# Clean output
if (Test-Path $ScreenshotDir) { Remove-Item $ScreenshotDir -Recurse -Force }
New-Item -ItemType Directory -Path $ScreenshotDir -Force | Out-Null

$script:EmulatorPid = $null
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

try {
    foreach ($device in $Devices) {
        Write-Header "$($device.Name) ($($device.Avd))"

        # Boot emulator
        Start-EmulatorForDevice $device.Avd

        # Install APKs (once per device)
        if (-not $SkipBuild) {
            Install-Apks
        }

        # Run for each locale
        foreach ($lang in $Locales) {
            Write-Header "  $lang / $($device.Folder)"
            $success = Run-TestsForLocale $lang $device.Folder
            if (-not $success) {
                Write-Host "  Skipping remaining locales for $($device.Name)" -ForegroundColor Red
                break
            }
        }

        # Reset locale
        & $Adb -s "emulator-$Port" shell cmd locale set-app-locales $AppPackage --locales '""' 2>$null

        # Kill emulator
        Stop-Emulator
    }
} finally {
    # Cleanup: always try to kill emulator
    Stop-Emulator
}

$stopwatch.Stop()

# --- Summary ------------------------------------------------------------------
Write-Host ""
Write-Header "Screenshots Complete!"
$actual = 0
foreach ($lang in $Locales) {
    Write-Host "  ${lang}/" -ForegroundColor Cyan
    foreach ($device in $Devices) {
        $dir = Join-Path $ScreenshotDir "$lang\$($device.Folder)"
        if (Test-Path $dir) {
            $files = Get-ChildItem $dir -Filter "*.png"
            $actual += $files.Count
            Write-Host "    $($device.Folder)/ ($($files.Count) files)" -ForegroundColor White
            $files | ForEach-Object { Write-Host "      $($_.Name)" -ForegroundColor DarkGray }
        } else {
            Write-Host "    $($device.Folder)/ (missing)" -ForegroundColor Red
        }
    }
}
Write-Host ""
Write-Host "  Total: $actual / $totalScreenshots screenshots" -ForegroundColor $(if ($actual -eq $totalScreenshots) { "Green" } else { "Yellow" })
Write-Host "  Time:  $([math]::Round($stopwatch.Elapsed.TotalMinutes, 1)) minutes"
Write-Host "  Output: $ScreenshotDir\"
Write-Host ""
