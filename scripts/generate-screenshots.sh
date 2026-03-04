#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# generate-screenshots.sh — Capture real-app screenshots from emulators
#
# Generates 8 screenshots × 3 device profiles × 2 locales = 48 total.
# Uses instrumented tests with screencap for full-fidelity captures including
# Vico charts and dynamic BuildConfig version.
#
# Locale switching is done externally via `adb shell cmd locale set-app-locales`
# (API 33+) before each test run. The test detects the active locale at runtime.
#
# Device profiles:
#   phone  — Pixel 6       (1080×2400, 420dpi)
#   7inch  — Nexus 7 2013  (1200×1920, 323dpi)
#   10inch — Pixel C       (2560×1800, 308dpi)
#
# Usage:
#   ./scripts/generate-screenshots.sh                  # Full pipeline (create AVDs + run all)
#   ./scripts/generate-screenshots.sh --no-avd         # Skip AVD creation (use running emulator)
#   ./scripts/generate-screenshots.sh --phone-only     # Phone screenshots only
# =============================================================================

# Config
API=36
ABI="x86_64"
SYSTEM_IMAGE="google_apis_playstore"
SCREENSHOT_DIR="screenshots"
TEST_CLASS="com.accbot.dca.screenshots.ScreenshotCaptureTest"
LOCALES=("en" "cs")

export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$LOCALAPPDATA/Android/Sdk/platform-tools:$LOCALAPPDATA/Android/Sdk/emulator:$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin:$PATH"

# Parse arguments
SKIP_AVD=false
PHONE_ONLY=false
for arg in "$@"; do
    case "$arg" in
        --no-avd)      SKIP_AVD=true ;;
        --phone-only)  PHONE_ONLY=true ;;
    esac
done

# When multiple devices are connected, target the emulator automatically.
# ANDROID_SERIAL tells adb (and Gradle connectedAndroidTest) which device to use.
if [[ "$SKIP_AVD" == true ]] && [[ -z "${ANDROID_SERIAL:-}" ]]; then
    EMU_SERIAL=$(adb devices | awk '/^emulator-/ {print $1; exit}')
    if [[ -n "$EMU_SERIAL" ]]; then
        export ANDROID_SERIAL="$EMU_SERIAL"
        echo "==> Multiple devices detected; targeting $ANDROID_SERIAL"
    fi
fi

# Device definitions: avd_name|device_id|output_folder|lcd_density
if [[ "$PHONE_ONLY" == true ]]; then
    DEVICES=("screenshot_phone|pixel_6|phone|420")
else
    DEVICES=(
        "screenshot_phone|pixel_6|phone|420"
        "screenshot_7inch|Nexus 7 2013|7inch|323"
        "screenshot_10inch|pixel_c|10inch|308"
    )
fi

EMU_PID=""
cleanup() {
    # Reset per-app locale on exit
    adb shell cmd locale set-app-locales com.accbot.dca --locales "" 2>/dev/null || true
    if [[ -n "$EMU_PID" ]]; then
        echo "Shutting down emulator (PID $EMU_PID)..."
        kill "$EMU_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Prepare output directory
rm -rf "$SCREENSHOT_DIR"
mkdir -p "$SCREENSHOT_DIR"

# ---------------------------------------------------------------------------
# Helper: run tests for all locales on the currently connected emulator
# ---------------------------------------------------------------------------
run_screenshots() {
    local output_folder="$1"

    # Build and install APKs once (Gradle reinstalls wipe per-app locale settings)
    echo ""
    echo "==> Building and installing APKs..."
    cd accbot-android
    ./gradlew installDebug installDebugAndroidTest
    cd ..

    for lang in "${LOCALES[@]}"; do
        echo ""
        echo "========================================"
        echo "  Capturing $lang screenshots → $lang/$output_folder/"
        echo "========================================"

        # Set per-app locale AFTER install (API 33+)
        if [[ "$lang" == "en" ]]; then
            # Clear per-app locale → system default (EN on our AVDs)
            adb shell cmd locale set-app-locales com.accbot.dca --locales ""
        else
            adb shell cmd locale set-app-locales com.accbot.dca --locales "$lang"
        fi

        # Clean any leftover screenshots on device
        adb shell "rm -rf /sdcard/Pictures/accbot-screenshots" 2>/dev/null || true

        # Run instrumented tests directly via am instrument (APKs already installed)
        adb shell am instrument -w \
            -e class "$TEST_CLASS" \
            com.accbot.dca.test/androidx.test.runner.AndroidJUnitRunner

        # Pull screenshots and flatten the nested accbot-screenshots/ dir
        local dest="$SCREENSHOT_DIR/$lang/$output_folder"
        mkdir -p "$dest"
        adb pull //sdcard/Pictures/accbot-screenshots/ "$dest/"
        if [[ -d "$dest/accbot-screenshots" ]]; then
            mv "$dest/accbot-screenshots/"* "$dest/" 2>/dev/null || true
            rmdir "$dest/accbot-screenshots" 2>/dev/null || true
        fi
        adb shell "rm -rf /sdcard/Pictures/accbot-screenshots"
    done

    # Reset to no per-app locale
    adb shell cmd locale set-app-locales com.accbot.dca --locales ""
}

# ---------------------------------------------------------------------------
# Main loop: for each device profile, boot emulator and capture screenshots
# ---------------------------------------------------------------------------
for device_config in "${DEVICES[@]}"; do
    IFS='|' read -r avd_name device_id folder lcd_density <<< "$device_config"

    if [[ "$SKIP_AVD" == true ]]; then
        echo ""
        echo "============================================================"
        echo "  Using running emulator for: $folder"
        echo "============================================================"
        adb wait-for-device
    else
        # Check if AVD exists (prefer avdmanager, fall back to emulator -list-avds)
        avd_exists=false
        if avdmanager list avd -c 2>/dev/null | grep -q "^${avd_name}$"; then
            avd_exists=true
        elif emulator -list-avds 2>/dev/null | grep -q "^${avd_name}$"; then
            avd_exists=true
        fi

        if [[ "$avd_exists" == false ]]; then
            if command -v avdmanager &>/dev/null; then
                echo ""
                echo "==> Creating AVD: $avd_name ($device_id, API $API)..."
                echo "no" | avdmanager create avd \
                    -n "$avd_name" \
                    -k "system-images;android-$API;$SYSTEM_IMAGE;$ABI" \
                    -d "$device_id"
            else
                echo ""
                echo "WARNING: AVD '$avd_name' does not exist and avdmanager is not available."
                echo "         Install cmdline-tools or create the AVD manually in Android Studio."
                echo "         Skipping $folder..."
                continue
            fi
        else
            echo "==> AVD '$avd_name' already exists."
        fi

        # Kill any running emulator before starting a new one
        if [[ -n "$EMU_PID" ]]; then
            kill "$EMU_PID" 2>/dev/null || true
            wait "$EMU_PID" 2>/dev/null || true
            EMU_PID=""
            sleep 3
        fi

        echo ""
        echo "============================================================"
        echo "  Booting emulator: $avd_name ($folder)"
        echo "============================================================"
        emulator -avd "$avd_name" -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot-load -gpu swiftshader_indirect &
        EMU_PID=$!
        export ANDROID_SERIAL="emulator-5556"

        echo "==> Waiting for device ($ANDROID_SERIAL)..."
        adb -s "$ANDROID_SERIAL" wait-for-device
        adb shell "while [[ -z \$(getprop sys.boot_completed) ]]; do sleep 1; done"
        echo "==> Emulator booted."
    fi

    run_screenshots "$folder"

    # If using --no-avd, only run once (same emulator for all)
    if [[ "$SKIP_AVD" == true ]]; then
        break
    fi
done

# --- Summary ---
echo ""
echo "========================================"
echo "  Screenshots captured successfully!"
echo "========================================"
echo ""
for lang in "${LOCALES[@]}"; do
    echo "  $lang/:"
    for device_config in "${DEVICES[@]}"; do
        IFS='|' read -r _ _ folder _ <<< "$device_config"
        echo "    $folder/:"
        ls "$SCREENSHOT_DIR/$lang/$folder/" 2>/dev/null | sed 's/^/      /' || echo "      (none found)"
        if [[ "$SKIP_AVD" == true ]]; then break; fi
    done
done
echo ""
echo "Output directory: $SCREENSHOT_DIR/"
