#!/usr/bin/env bash
# AccBot iOS deploy script
# Downloads latest .ipa from CI and installs it on iPad via Sideloader CLI
#
# Usage:
#   ./scripts/deploy-ios.sh           # download latest + install
#   ./scripts/deploy-ios.sh --local   # install existing .ipa without downloading

set -euo pipefail

SIDELOADER="$LOCALAPPDATA/Sideloader/sideloader-cli-x86_64-windows-msvc.exe"
IPA_DIR="$(cd "$(dirname "$0")/.." && pwd)/ipa-download"
IPA_FILE="$IPA_DIR/AccBot-unsigned.ipa"
ANISETTE_URL="http://localhost:6969"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check prerequisites
check_prereqs() {
    if [ ! -f "$SIDELOADER" ]; then
        echo -e "${RED}Sideloader not found at $SIDELOADER${NC}"
        exit 1
    fi

    if ! curl -s "$ANISETTE_URL" > /dev/null 2>&1; then
        echo -e "${YELLOW}Starting Anisette server...${NC}"
        docker start anisette-v3 2>/dev/null || \
            docker run -d --restart always --name anisette-v3 \
                -p 6969:6969 \
                --volume anisette-v3_data:/home/Alcoholic/.config/anisette-v3/lib/ \
                dadoum/anisette-v3-server
        sleep 2
    fi
    echo -e "${GREEN}Anisette server OK${NC}"
}

# Download latest .ipa from CI
download_ipa() {
    echo -e "${YELLOW}Downloading latest .ipa from CI...${NC}"
    mkdir -p "$IPA_DIR"
    rm -f "$IPA_DIR"/*.ipa

    # Get latest successful run
    local run_id
    run_id=$(gh run list --workflow ios-build.yml --status success --limit 1 --json databaseId -q '.[0].databaseId')
    if [ -z "$run_id" ]; then
        echo -e "${RED}No successful CI runs found${NC}"
        exit 1
    fi

    echo "Run ID: $run_id"
    gh run download "$run_id" --name AccBot-Device --dir "$IPA_DIR"
    echo -e "${GREEN}Downloaded: $(ls "$IPA_DIR"/*.ipa)${NC}"
}

# Install via Sideloader
install_ipa() {
    if [ ! -f "$IPA_FILE" ]; then
        echo -e "${RED}.ipa not found at $IPA_FILE${NC}"
        exit 1
    fi

    echo -e "${YELLOW}Installing $(basename "$IPA_FILE") on iPad...${NC}"
    echo -e "${YELLOW}You will be prompted for Apple ID, password, and 2FA code.${NC}"
    echo ""

    export SIDELOADER_ANISETTE_URL="$ANISETTE_URL"
    "$SIDELOADER" install -i "$IPA_FILE"

    echo ""
    echo -e "${GREEN}Done! AccBot installed on iPad.${NC}"
}

# Main
check_prereqs

if [ "${1:-}" != "--local" ]; then
    download_ipa
fi

install_ipa
