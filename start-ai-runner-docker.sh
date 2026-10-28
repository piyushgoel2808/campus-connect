#!/usr/bin/env bash
# ==============================================================================
# GradBot Portable Docker AI Runner (BVICAM | Campus Connect)
# ==============================================================================
# Runs the full AI Edge Node (Ollama + Qdrant + Multiplexer Proxy + Cloudflare Tunnel)
# inside Docker containers on ANY laptop (macOS, Windows with WSL/Docker, Linux).
#
# Usage:
#   ./start-ai-runner-docker.sh
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# Colors for terminal output
BOLD="\033[1m"
GREEN="\033[0;32m"
BLUE="\033[0;34m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
CYAN="\033[0;36m"
NC="\033[0m"

echo -e "${BOLD}${BLUE}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║      🐳 GradBot Docker AI Runner (Portable Edge Node)          ║"
echo "║    Bridging Local AI (Ollama + Qdrant) to Render Cloud          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ------------------------------------------------------------------------------
# 1. PREREQUISITE CHECKS (Docker Engine & Compose)
# ------------------------------------------------------------------------------
echo -e "${CYAN}[1/5] Checking Docker environment...${NC}"

if ! command -v docker &>/dev/null; then
    echo -e "${RED}❌ Docker is not installed or not in PATH.${NC}"
    echo -e "   Please install Docker Desktop from: ${BOLD}https://www.docker.com/products/docker-desktop/${NC}"
    exit 1
fi

if ! docker info &>/dev/null; then
    echo -e "${RED}❌ Docker daemon is not running.${NC}"
    echo -e "   Please start Docker Desktop or your Docker service and retry."
    exit 1
fi

# Detect docker compose plugin or standalone docker-compose
if docker compose version &>/dev/null; then
    COMPOSE_CMD="docker compose"
elif command -v docker-compose &>/dev/null; then
    COMPOSE_CMD="docker-compose"
else
    echo -e "${RED}❌ docker compose is required but neither 'docker compose' nor 'docker-compose' was found.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker is running with ${COMPOSE_CMD}${NC}"

# ------------------------------------------------------------------------------
# 2. SHARED SECRET AUTHENTICATION SETUP
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[2/5] Initializing zero-trust security token...${NC}"
ENV_FILE=".env.ai-runner"
AUTH_HEADER="X-GradBot-Key"

if [ -f "$ENV_FILE" ]; then
    # Load existing secrets
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

if [ -z "$GRADBOT_SECRET_KEY" ]; then
    echo "Generating new 48-character cryptographically secure secret..."
    if command -v openssl &>/dev/null; then
        GRADBOT_SECRET_KEY=$(openssl rand -hex 24)
    elif command -v python3 &>/dev/null; then
        GRADBOT_SECRET_KEY=$(python3 -c "import secrets; print(secrets.token_hex(24))")
    else
        GRADBOT_SECRET_KEY=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 48 | head -n 1)
    fi

    cat <<EOF >> "$ENV_FILE"
# GradBot Local AI Runner Authentication Secret
# Keep this file private and never commit to version control!
GRADBOT_SECRET_KEY=${GRADBOT_SECRET_KEY}
AUTH_HEADER=${AUTH_HEADER}

# Optional: Add Render credentials for 100% automated environment variable sync
# RENDER_API_KEY=rnd_xxxxxxxxxxxx
# RENDER_SERVICE_ID=srv-xxxxxxxxxxxx
EOF
    chmod 600 "$ENV_FILE"
    echo -e "${GREEN}✓ Secret generated and saved to ${ENV_FILE}${NC}"
else
    echo -e "${GREEN}✓ Loaded existing secret from ${ENV_FILE}${NC}"
fi

export GRADBOT_SECRET_KEY
export AUTH_HEADER

# ------------------------------------------------------------------------------
# 3. LAUNCH DOCKER CONTAINERS
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[3/5] Starting containerized AI stack (Qdrant, Ollama, Proxy, Cloudflare)...${NC}"
$COMPOSE_CMD -f docker-compose.ai-runner.yml up -d --build

echo -e "${GREEN}✓ Containers deployed:${NC}"
echo -e "   • Qdrant Vector Engine   : container ${BOLD}campus-ai-qdrant${NC} (port 6333)"
echo -e "   • Ollama Inference Node  : container ${BOLD}campus-ai-ollama${NC} (port 11434)"
echo -e "   • Security Auth Proxy    : container ${BOLD}campus-ai-proxy${NC} (port 11435)"
echo -e "   • Cloudflare Edge Tunnel : container ${BOLD}campus-ai-tunnel${NC}"

# ------------------------------------------------------------------------------
# 4. MODEL VERIFICATION INSIDE OLLAMA CONTAINER
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[4/5] Verifying AI models in Ollama container...${NC}"

# Wait for Ollama to accept commands
echo "Waiting for Ollama service readiness..."
for i in {1..30}; do
    if docker exec campus-ai-ollama ollama list &>/dev/null; then
        break
    fi
    sleep 1
done

OLLAMA_MODELS=$(docker exec campus-ai-ollama ollama list || true)

# 1. Embedding Model
if ! echo "$OLLAMA_MODELS" | grep -q "embeddinggemma-2"; then
    echo -e "${YELLOW}⚠️  Embedding model 'embeddinggemma-2:latest' not found in container.${NC}"
    echo "Downloading embeddinggemma-2:latest (~768-dim vector embeddings)..."
    docker exec -it campus-ai-ollama ollama pull embeddinggemma-2:latest
fi
echo -e "${GREEN}✓ Embedding model 'embeddinggemma-2:latest' is ready${NC}"

# 2. Chat Model
if ! echo "$OLLAMA_MODELS" | grep -q "gemma4:e4b"; then
    echo -e "${YELLOW}⚠️  Chat model 'gemma4:e4b' not found in container.${NC}"
    echo "Downloading gemma4:e4b (~6.6 GB)..."
    docker exec -it campus-ai-ollama ollama pull gemma4:e4b
fi
echo -e "${GREEN}✓ Chat model 'gemma4:e4b' is ready${NC}"

# ------------------------------------------------------------------------------
# 5. RETRIEVE PUBLIC CLOUDFLARE TUNNEL URL & TEST
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[5/5] Connecting Cloudflare Tunnel to Render...${NC}"
echo "Waiting for public HTTPS tunnel to assign hostname..."

TUNNEL_URL=""
for i in {1..30}; do
    TUNNEL_URL=$(docker logs campus-ai-tunnel 2>&1 | grep -o 'https://[a-zA-Z0-9-]*\.trycloudflare\.com' | tail -1 || true)
    if [ -n "$TUNNEL_URL" ]; then
        break
    fi
    sleep 1
done

if [ -z "$TUNNEL_URL" ]; then
    echo -e "${YELLOW}⚠️  Could not auto-detect tunnel URL within 30 seconds.${NC}"
    echo "Check tunnel logs manually: docker logs campus-ai-tunnel"
    TUNNEL_URL="https://<pending-check-docker-logs>.trycloudflare.com"
else
    echo -e "${GREEN}✓ Cloudflare Tunnel active: ${BOLD}${TUNNEL_URL}${NC}"
fi

# Optional automated Render API sync
RENDER_SYNCED=false
if [ -n "$RENDER_API_KEY" ] && [ -n "$RENDER_SERVICE_ID" ] && [ "$TUNNEL_URL" != "https://<pending-check-docker-logs>.trycloudflare.com" ]; then
    echo "Auto-syncing environment variables to Render via API..."
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
        "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/env-vars" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${RENDER_API_KEY}" \
        -d "[
            {\"key\": \"OLLAMA_BASE_URL\", \"value\": \"${TUNNEL_URL}\"},
            {\"key\": \"OLLAMA_AUTH_TOKEN\", \"value\": \"${GRADBOT_SECRET_KEY}\"},
            {\"key\": \"OLLAMA_AUTH_HEADER\", \"value\": \"${AUTH_HEADER}\"}
        ]")

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        echo -e "${GREEN}✓ Render environment variables updated successfully!${NC}"
        RENDER_SYNCED=true
    else
        echo -e "${YELLOW}⚠️  Render API sync returned HTTP ${HTTP_CODE}. Please verify manually.${NC}"
    fi
fi

# ------------------------------------------------------------------------------
# SUMMARY & INSTRUCTIONS
# ------------------------------------------------------------------------------
echo ""
echo -e "${BOLD}${GREEN}────────────────────────────────────────────────────────────────${NC}"
echo -e "${BOLD}${GREEN} 🎉 GRADBOT DOCKER AI RUNNER IS ONLINE!${NC}"
echo -e "${BOLD}${GREEN}────────────────────────────────────────────────────────────────${NC}"
echo -e "  ${BOLD}Public Tunnel URL :${NC} ${CYAN}${TUNNEL_URL}${NC}"
echo -e "  ${BOLD}Auth Header Key   :${NC} ${YELLOW}${AUTH_HEADER}${NC}"
echo -e "  ${BOLD}Auth Secret Token :${NC} ${YELLOW}${GRADBOT_SECRET_KEY}${NC}"
echo -e "  ${BOLD}Chat Model        :${NC} gemma4:e4b (Running in container campus-ai-ollama)"
echo -e "  ${BOLD}Embedding Model   :${NC} embeddinggemma-2:latest (768 dimensions)"
echo -e "  ${BOLD}Vector Database   :${NC} Qdrant (HNSW Vector Engine in campus-ai-qdrant)"
echo -e "  ${BOLD}Tunnel Multiplex  :${NC} Ollama (/api/*) & Qdrant (/qdrant/*, /collections/*)"
echo -e "${BOLD}${GREEN}────────────────────────────────────────────────────────────────${NC}"

if [ "$RENDER_SYNCED" = false ]; then
    echo -e "\n${BOLD}${YELLOW}👉 ACTION REQUIRED IN RENDER DASHBOARD:${NC}"
    echo "1. Go to: https://dashboard.render.com"
    echo "2. Open your Campus Connect Web Service -> 'Environment' tab"
    echo "3. Add or update these environment variables:"
    echo ""
    echo -e "   ${BOLD}OLLAMA_BASE_URL${NC}   = ${CYAN}${TUNNEL_URL}${NC}"
    echo -e "   ${BOLD}OLLAMA_AUTH_TOKEN${NC} = ${CYAN}${GRADBOT_SECRET_KEY}${NC}"
    echo -e "   ${BOLD}OLLAMA_AUTH_HEADER${NC}= ${CYAN}${AUTH_HEADER}${NC}"
    echo ""
    echo -e "   ${GREEN}(Note: Qdrant automatically multiplexes over ${TUNNEL_URL}/qdrant — zero extra variables needed!)${NC}"
    echo ""
    echo "4. Render will auto-deploy or restart and connect to this Docker runner!"
fi

echo -e "\n${BOLD}Helpful Docker Commands:${NC}"
echo -e "  • View live AI proxy logs : ${CYAN}docker logs -f campus-ai-proxy${NC}"
echo -e "  • View Cloudflare logs    : ${CYAN}docker logs -f campus-ai-tunnel${NC}"
echo -e "  • View Qdrant logs        : ${CYAN}docker logs -f campus-ai-qdrant${NC}"
echo -e "  • Stop all AI containers  : ${CYAN}$COMPOSE_CMD -f docker-compose.ai-runner.yml down${NC}"
echo ""
