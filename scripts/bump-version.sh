#!/bin/bash
#
# AccBot Version Bump Script
# Usage: ./scripts/bump-version.sh [major|minor|patch]
#
# Examples:
#   ./scripts/bump-version.sh patch   # 1.0.0 -> 1.0.1
#   ./scripts/bump-version.sh minor   # 1.0.1 -> 1.1.0
#   ./scripts/bump-version.sh major   # 1.1.0 -> 2.0.0
#

set -e

TYPE=${1:-patch}
PROPS="accbot-android/gradle.properties"

# Check if gradle.properties exists
if [ ! -f "$PROPS" ]; then
    echo "Error: $PROPS not found"
    echo "Run this script from the AccBot root directory"
    exit 1
fi

# Read current version
MAJOR=$(grep "^VERSION_MAJOR=" "$PROPS" | cut -d= -f2)
MINOR=$(grep "^VERSION_MINOR=" "$PROPS" | cut -d= -f2)
PATCH=$(grep "^VERSION_PATCH=" "$PROPS" | cut -d= -f2)

OLD_VERSION="$MAJOR.$MINOR.$PATCH"

# Calculate new version
case $TYPE in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
    *)
        echo "Usage: $0 [major|minor|patch]"
        echo "  major - Breaking changes (1.0.0 -> 2.0.0)"
        echo "  minor - New features (1.0.0 -> 1.1.0)"
        echo "  patch - Bug fixes (1.0.0 -> 1.0.1)"
        exit 1
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"

# Update gradle.properties
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/^VERSION_MAJOR=.*/VERSION_MAJOR=$MAJOR/" "$PROPS"
    sed -i '' "s/^VERSION_MINOR=.*/VERSION_MINOR=$MINOR/" "$PROPS"
    sed -i '' "s/^VERSION_PATCH=.*/VERSION_PATCH=$PATCH/" "$PROPS"
else
    # Linux/Windows (Git Bash)
    sed -i "s/^VERSION_MAJOR=.*/VERSION_MAJOR=$MAJOR/" "$PROPS"
    sed -i "s/^VERSION_MINOR=.*/VERSION_MINOR=$MINOR/" "$PROPS"
    sed -i "s/^VERSION_PATCH=.*/VERSION_PATCH=$PATCH/" "$PROPS"
fi

echo "Version bumped: $OLD_VERSION -> $NEW_VERSION"

# ── Changelog scaffolding ───────────────────────────────────────────────────

CHANGELOG="changelog.json"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$CHANGELOG" ]; then
    # Reuse ensure_jq from generate-changelog.sh
    source "$SCRIPT_DIR/generate-changelog.sh" --source-only 2>/dev/null || true

    # Inline jq availability check (in case sourcing didn't work)
    if ! command -v jq &>/dev/null; then
        # Try common locations
        if [ -x "$HOME/.local/bin/jq" ] || [ -x "$HOME/.local/bin/jq.exe" ]; then
            export PATH="$HOME/.local/bin:$PATH"
        fi
    fi

    if command -v jq &>/dev/null; then
        NEW_VERSION_CODE=$((MAJOR * 10000 + MINOR * 1000 + PATCH * 100))

        # Check if this version already exists
        existing=$(jq --argjson vc "$NEW_VERSION_CODE" '[.[] | select(.versionCode == $vc)] | length' "$CHANGELOG")

        if [ "$existing" -eq 0 ]; then
            # Prepend new template entry with all locales
            jq --argjson vc "$NEW_VERSION_CODE" \
               --arg ver "$NEW_VERSION" \
               '[{versionCode: $vc, version: $ver, title: {en: "TODO: Release title", cs: "TODO: Název releasu"}, features: {en: ["TODO: Add features"], cs: ["TODO: Přidat funkce"]}}] + .' \
               "$CHANGELOG" > "$CHANGELOG.tmp" && mv "$CHANGELOG.tmp" "$CHANGELOG"

            echo ""
            echo "Added changelog template for v$NEW_VERSION (versionCode $NEW_VERSION_CODE)"
        fi

        # Regenerate ChangelogData.kt
        "$SCRIPT_DIR/generate-changelog.sh"
    else
        echo ""
        echo "Warning: jq not found — skipping changelog scaffolding"
        echo "Run: ./scripts/generate-changelog.sh (it will auto-install jq)"
    fi
fi

echo ""
echo "Next steps:"
echo "  1. Edit changelog.json with real release notes for v$NEW_VERSION"
echo "  2. Run: ./scripts/release.sh [--push]"
echo "     (regenerates Kotlin, commits, tags v$NEW_VERSION, optionally pushes)"
echo ""
echo "This will trigger the GitHub Actions workflow to build and release."
