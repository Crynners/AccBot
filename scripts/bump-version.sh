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
echo ""
echo "Next steps:"
echo "  1. Review changes: git diff"
echo "  2. Commit: git commit -am \"Bump version to $NEW_VERSION\""
echo "  3. Tag: git tag v$NEW_VERSION"
echo "  4. Push: git push && git push --tags"
echo ""
echo "This will trigger the GitHub Actions workflow to build and release."
