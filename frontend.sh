#!/bin/bash

# DBaaS Platform - Frontend Start Script

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$PROJECT_DIR/frontend"

cleanup() {
    echo -e "\n${YELLOW}Đang dừng Frontend...${NC}"
    echo -e "${GREEN}Frontend đã dừng.${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

# Install dependencies if needed
if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    echo -e "${YELLOW}Installing frontend dependencies...${NC}"
    cd "$FRONTEND_DIR"
    npm install
fi

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    DBaaS Platform - Frontend Server     ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}🚀 Khởi động Frontend (Next.js)...${NC}"
echo -e "${GREEN}   URL: http://localhost:3000${NC}"
echo -e "${YELLOW}   Nhấn Ctrl+C để dừng${NC}"
echo ""

cd "$FRONTEND_DIR"
npm run dev
