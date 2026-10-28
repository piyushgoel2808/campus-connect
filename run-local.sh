#!/usr/bin/env bash
# ==============================================================================
# Campus Connect (GradLink) - Local Development Launcher
# ==============================================================================
# Runs the full Spring Boot application locally with:
#   1. Zero external database requirement (persistent embedded H2 in MySQL mode)
#   2. Local Mac Ollama Gemma 4 & nomic-embed-text
#   3. Web UI at http://localhost:8080
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

BOLD="\033[1m"
GREEN="\033[0;32m"
BLUE="\033[0;34m"
YELLOW="\033[1;33m"
CYAN="\033[0;36m"
NC="\033[0m"

echo -e "${BOLD}${BLUE}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║      🎓 Campus Connect (GradLink) - Local Dev Server           ║"
echo "║    Spring Boot + Embedded Database + Local Gemma 4 AI          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# 1. Set JDK 17
if [ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
fi

# 2. Check Ollama daemon
if curl -s http://localhost:11434/api/tags &>/dev/null; then
    echo -e "${GREEN}✓ Local Ollama daemon detected at http://localhost:11434 (Gemma 4 active)${NC}"
else
    echo -e "${YELLOW}⚠️  Ollama daemon not detected. Starting in background...${NC}"
    ollama serve &
    sleep 2
fi

# 3. Check Qdrant Vector DB
if curl -s http://localhost:6333/ &>/dev/null; then
    echo -e "${GREEN}✓ Local Qdrant Vector DB detected at http://localhost:6333${NC}"
elif [ -f "./bin/qdrant" ]; then
    echo -e "${CYAN}Starting local Qdrant Vector DB in background...${NC}"
    ./bin/qdrant > /dev/null 2>&1 &
    sleep 2
fi

echo -e "\n${CYAN}Starting Spring Boot on http://localhost:8080 (Profile: local)...${NC}"
echo -e "${BOLD}Pre-seeded Accounts:${NC}"
echo -e "  • Admin   : ${YELLOW}admin@bvicam.in${NC}  / Password: ${YELLOW}admin123${NC}"
echo -e "  • Alumni  : ${YELLOW}alumni@bvicam.in${NC} / Password: ${YELLOW}1234${NC}"
echo -e "  • Student : ${YELLOW}student@bvicam.in${NC} / Password: ${YELLOW}1234${NC}\n"

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
