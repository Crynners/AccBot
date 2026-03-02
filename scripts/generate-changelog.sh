#!/bin/bash
#
# Generate ChangelogData.kt from changelog.json
# Usage: ./scripts/generate-changelog.sh
#
# Requires jq — auto-installs if not found.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JSON_FILE="$ROOT_DIR/changelog.json"
OUTPUT_FILE="$ROOT_DIR/accbot-android/app/src/main/java/com/accbot/dca/presentation/changelog/ChangelogData.kt"

# ── Auto-install jq if missing ──────────────────────────────────────────────

ensure_jq() {
    if command -v jq &>/dev/null; then
        return
    fi

    echo "jq not found — installing..."

    case "$OSTYPE" in
        msys*|mingw*|cygwin*)
            # Windows / Git Bash
            mkdir -p "$HOME/.local/bin"
            curl -sL -o "$HOME/.local/bin/jq.exe" \
                "https://github.com/jqlang/jq/releases/latest/download/jq-windows-amd64.exe"
            chmod +x "$HOME/.local/bin/jq.exe"
            export PATH="$HOME/.local/bin:$PATH"
            ;;
        darwin*)
            brew install jq
            ;;
        *)
            sudo apt-get update -qq && sudo apt-get install -y -qq jq
            ;;
    esac

    if ! command -v jq &>/dev/null; then
        echo "Error: Failed to install jq"
        exit 1
    fi
    echo "jq installed successfully."
}

ensure_jq

# ── Validate input ──────────────────────────────────────────────────────────

if [ ! -f "$JSON_FILE" ]; then
    echo "Error: $JSON_FILE not found"
    echo "Run this script from the AccBot root directory"
    exit 1
fi

# ── Helper: escape string for Kotlin ────────────────────────────────────────

escape_kotlin() {
    echo "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

# ── Generate Kotlin ─────────────────────────────────────────────────────────

echo "Generating ChangelogData.kt from changelog.json..."

{
    cat <<'HEADER'
package com.accbot.dca.presentation.changelog

// Generated from changelog.json by scripts/generate-changelog.sh
// Do not edit manually — run the generator script after updating changelog.json

object ChangelogData {
    val entries: List<ChangelogEntry> = listOf(
HEADER

    entry_count=$(jq 'length' "$JSON_FILE")
    for (( i=0; i<entry_count; i++ )); do
        versionCode=$(jq -r ".[$i].versionCode" "$JSON_FILE")
        version=$(jq -r ".[$i].version" "$JSON_FILE")

        echo "        ChangelogEntry("
        echo "            versionCode = $versionCode,"
        echo "            version = \"$version\","

        # ── titles map ──
        echo "            titles = mapOf("
        title_keys=$(jq -r ".[$i].title | keys[]" "$JSON_FILE" | tr -d '\r')
        for lang in $title_keys; do
            title=$(jq -r --argjson idx "$i" --arg l "$lang" '.[$idx].title[$l]' "$JSON_FILE")
            escaped_title=$(escape_kotlin "$title")
            echo "                \"$lang\" to \"$escaped_title\","
        done
        echo "            ),"

        # ── features map ──
        echo "            features = mapOf("
        feature_keys=$(jq -r ".[$i].features | keys[]" "$JSON_FILE" | tr -d '\r')
        for lang in $feature_keys; do
            echo "                \"$lang\" to listOf("
            feature_count=$(jq -r --argjson idx "$i" --arg l "$lang" '.[$idx].features[$l] | length' "$JSON_FILE")
            for (( j=0; j<feature_count; j++ )); do
                feature=$(jq -r --argjson idx "$i" --arg l "$lang" --argjson fi "$j" '.[$idx].features[$l][$fi]' "$JSON_FILE")
                escaped_feature=$(escape_kotlin "$feature")
                echo "                    \"$escaped_feature\","
            done
            echo "                ),"
        done
        echo "            )"

        echo "        ),"
    done

    cat <<'FOOTER'
    )

    fun getNewEntries(lastSeenVersionCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode > lastSeenVersionCode }
}
FOOTER

} > "$OUTPUT_FILE"

echo "Generated: $OUTPUT_FILE"
echo "Entries: $entry_count"
