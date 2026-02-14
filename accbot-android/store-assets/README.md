# AccBot Store Assets

Play Store graphics for AccBot DCA.

## Files

| File | Purpose | Dimensions |
|---|---|---|
| `icon-512.svg` | Hi-res app icon | 512 x 512 |
| `feature-graphic-1024x500.svg` | Feature graphic banner | 1024 x 500 |

## Convert SVG to PNG

### Option 1: Browser
1. Open the SVG file in Chrome/Edge
2. Right-click → Inspect → Console
3. Take a screenshot at the correct resolution

### Option 2: Inkscape (CLI)
```bash
inkscape icon-512.svg --export-type=png --export-width=512 --export-height=512 -o icon-512.png
inkscape feature-graphic-1024x500.svg --export-type=png --export-width=1024 --export-height=500 -o feature-graphic-1024x500.png
```

### Option 3: Online
Upload SVGs to [svgtopng.com](https://svgtopng.com) or [cloudconvert.com](https://cloudconvert.com).

## Screenshots

Screenshots must be captured from the running app. Recommended set (minimum 2, ideal 8):

1. Dashboard with active plan and portfolio summary
2. Portfolio performance chart with ROI
3. Strategy selection (Classic, ATH, Fear & Greed)
4. Transaction history with filters
5. Exchange setup with QR scanning
6. Security onboarding screen
7. Sandbox mode demonstration
8. Settings with exchange management

**Dimensions:** 1080 x 1920 (portrait) or 1920 x 1080 (landscape)

### Capture via ADB
```bash
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./screenshots/
```
