#!/bin/bash

# DBaaS Platform - Start Script
# Chạy cả backend và frontend đồng thời

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend"
FRONTEND_DIR="$PROJECT_DIR/frontend"

# Log file locations
BACKEND_LOG="$PROJECT_DIR/logs/backend.log"
FRONTEND_LOG="$PROJECT_DIR/logs/frontend.log"

# Create logs directory
mkdir -p "$PROJECT_DIR/logs"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Đang dừng các services...${NC}"
    if [ ! -z "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null || true
    fi
    if [ ! -z "$FRONTEND_PID" ]; then
        kill $FRONTEND_PID 2>/dev/null || true
    fi
    echo -e "${GREEN}Đã dừng tất cả services.${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

# Check dependencies
check_dependencies() {
    echo -e "${BLUE}Kiểm tra dependencies...${NC}"
    
    if ! command -v java &> /dev/null; then
        echo -e "${RED}❌ Java chưa được cài đặt${NC}"
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven chưa được cài đặt${NC}"
        exit 1
    fi
    
    if ! command -v node &> /dev/null; then
        echo -e "${RED}❌ Node.js chưa được cài đặt${NC}"
        exit 1
    fi
    
    if ! command -v npm &> /dev/null; then
        echo -e "${RED}❌ npm chưa được cài đặt${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Tất cả dependencies đã sẵn sàng${NC}"
}

# Install frontend dependencies if needed
install_frontend_deps() {
    if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
        echo -e "${YELLOW}Installing frontend dependencies...${NC}"
        cd "$FRONTEND_DIR"
        npm install
    fi
}

# Start backend
start_backend() {
    echo -e "${BLUE}🚀 Khởi động Backend (Spring Boot)...${NC}"
    cd "$BACKEND_DIR"
    mvn spring-boot:run > "$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
    echo -e "${GREEN}   Backend đang chạy (PID: $BACKEND_PID)${NC}"
    echo -e "${GREEN}   Log: $BACKEND_LOG${NC}"
    echo -e "${GREEN}   URL: http://localhost:8080${NC}"
}

# Start frontend
start_frontend() {
    echo -e "${BLUE}🚀 Khởi động Frontend (Next.js)...${NC}"
    cd "$FRONTEND_DIR"
    npm run dev > "$FRONTEND_LOG" 2>&1 &
    FRONTEND_PID=$!
    echo -e "${GREEN}   Frontend đang chạy (PID: $FRONTEND_PID)${NC}"
    echo -e "${GREEN}   Log: $FRONTEND_LOG${NC}"
    echo -e "${GREEN}   URL: http://localhost:3000${NC}"
}

# Main
main() {
    echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║       DBaaS Platform - Start Script     ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
    echo ""
    
    check_dependencies
    install_frontend_deps
    
    echo ""
    start_backend
    echo ""
    start_frontend
    
    echo ""
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo -e "${GREEN}✅ Tất cả services đã khởi động!${NC}"
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo ""
    echo -e "${YELLOW}Backend:  http://localhost:8080${NC}"
    echo -e "${YELLOW}Frontend: http://localhost:3000${NC}"
    echo ""
    echo -e "${YELLOW}Nhấn Ctrl+C để dừng tất cả services${NC}"
    echo ""
    
    # Wait for both processes
    wait
}

main "$@"
