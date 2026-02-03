#!/bin/bash

# DBaaS Platform - Backend Start Script
# Nhấn 'r' để restart, 'q' để thoát

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend"
BACKEND_PID=""

cleanup() {
    echo -e "\n${YELLOW}Đang dừng Backend...${NC}"
    if [ ! -z "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null || true
        wait $BACKEND_PID 2>/dev/null || true
    fi
    echo -e "${GREEN}Backend đã dừng.${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

start_backend() {
    echo -e "${BLUE}🚀 Khởi động Backend (Spring Boot)...${NC}"
    cd "$BACKEND_DIR"
    
    # Load environment variables from .env file
    if [ -f ".env" ]; then
        echo -e "${GREEN}   Loading .env file...${NC}"
        export $(grep -v '^#' .env | xargs)
    else
        echo -e "${RED}   ⚠️ .env file not found! Copy .env.example to .env${NC}"
    fi
    
    mvn spring-boot:run &
    BACKEND_PID=$!
    echo -e "${GREEN}   Backend đang chạy (PID: $BACKEND_PID)${NC}"
    echo -e "${GREEN}   Logs đang được hiển thị trực tiếp trên terminal${NC}"
    echo -e "${GREEN}   URL: http://localhost:8080${NC}"
}

stop_backend() {
    if [ ! -z "$BACKEND_PID" ]; then
        echo -e "${YELLOW}Đang dừng Backend...${NC}"
        kill $BACKEND_PID 2>/dev/null || true
        wait $BACKEND_PID 2>/dev/null || true
        BACKEND_PID=""
    fi
}

restart_backend() {
    echo -e "\n${YELLOW}🔄 Đang restart Backend...${NC}"
    stop_backend
    sleep 1
    start_backend
}

main() {
    echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     DBaaS Platform - Backend Server     ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
    echo ""
    
    start_backend
    
    echo ""
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo -e "${YELLOW}  Phím tắt:${NC}"
    echo -e "${YELLOW}    r - Restart backend${NC}"
    echo -e "${YELLOW}    q - Thoát${NC}"
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo ""
    
    while true; do
        read -rsn1 key
        case "$key" in
            r|R)
                restart_backend
                ;;
            q|Q)
                cleanup
                ;;
        esac
    done
}

main "$@"
