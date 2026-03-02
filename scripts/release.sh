#!/bin/bash
#
# AccBot Release Script
# Creates a version commit and tag from gradle.properties (single source of truth).
#
# Usage: ./scripts/release.sh [--push]
#
#   --push    Also push commit and tag to remote (triggers CI release)
#
# This script ensures the git tag always matches the app version.
# Run this AFTER editing changelog.json with real release notes.
#

set -e

PROPS="accbot-android/gradle.properties"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -f "$PROPS" ]; then
    echo "Error: $PROPS not found"
    echo "Run this script from the AccBot root directory"
    exit 1
fi

# ── Read version from gradle.properties (single source of truth) ────────────

MAJOR=$(grep "^VERSION_MAJOR=" "$PROPS" | cut -d= -f2)
MINOR=$(grep "^VERSION_MINOR=" "$PROPS" | cut -d= -f2)
PATCH=$(grep "^VERSION_PATCH=" "$PROPS" | cut -d= -f2)
VERSION="$MAJOR.$MINOR.$PATCH"
TAG="v$VERSION"

echo "Version from gradle.properties: $VERSION"
echo "Tag: $TAG"
echo ""

# ── Validate changelog ──────────────────────────────────────────────────────

CHANGELOG="changelog.json"
if [ -f "$CHANGELOG" ]; then
    if ! command -v jq &>/dev/null; then
        if [ -x "$HOME/.local/bin/jq" ] || [ -x "$HOME/.local/bin/jq.exe" ]; then
            export PATH="$HOME/.local/bin:$PATH"
        fi
    fi

    if command -v jq &>/dev/null; then
        has_todo=$(jq -r --arg ver "$VERSION" '.[] | select(.version == $ver) | (.title | to_entries[].value) + " " + ([.features | to_entries[].value[]] | join(" "))' "$CHANGELOG" | grep -c "TODO" || true)
        if [ "$has_todo" -gt 0 ]; then
            echo "Warning: changelog.json for v$VERSION still contains TODO placeholders!"
            echo "Edit changelog.json first, then run: ./scripts/generate-changelog.sh"
            echo ""
            read -p "Continue anyway? [y/N] " -n 1 -r
            echo ""
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                exit 1
            fi
        fi
    fi
fi

# ── Regenerate ChangelogData.kt ─────────────────────────────────────────────

echo "Regenerating ChangelogData.kt..."
"$SCRIPT_DIR/generate-changelog.sh"
echo ""

# ── Check for existing tag ──────────────────────────────────────────────────

if git rev-parse "$TAG" &>/dev/null; then
    echo "Error: Tag $TAG already exists!"
    echo "If you need to re-release, delete the old tag first:"
    echo "  git tag -d $TAG && git push origin :refs/tags/$TAG"
    exit 1
fi

# ── Check working tree ──────────────────────────────────────────────────────

if [ -n "$(git status --porcelain)" ]; then
    echo "Uncommitted changes detected. Staging and committing..."
    echo ""
    git add -A
    git status --short
    echo ""
    read -p "Commit these changes as 'Release v$VERSION'? [Y/n] " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Nn]$ ]]; then
        echo "Aborted. Commit manually, then re-run this script."
        exit 1
    fi
    git commit -m "Release v$VERSION"
else
    echo "Working tree clean."
fi

# ── Create tag ──────────────────────────────────────────────────────────────

git tag "$TAG"
echo ""
echo "Created tag: $TAG"

# ── Push if requested ───────────────────────────────────────────────────────

if [ "$1" = "--push" ]; then
    echo "Pushing to remote..."
    git push
    git push --tags
    echo ""
    echo "Done! CI will now build and create the GitHub Release."
else
    echo ""
    echo "Tag created locally. To trigger the release:"
    echo "  git push && git push --tags"
fi
