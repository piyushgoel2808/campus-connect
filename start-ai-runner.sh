#!/usr/bin/env bash
# ==============================================================================
# GradBot Local AI Runner (BVICAM | Campus Connect)
# ==============================================================================
# Exposes local Ollama (gemma4:e4b + nomic-embed-text) to Render over a secure
# Cloudflare Tunnel with shared-secret header authentication (X-GradBot-Key).
#
# Usage:
#   ./start-ai-runner.sh
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
echo "║      🎓 GradBot AI Hybrid Runner (BVICAM | Campus Connect)     ║"
echo "║    Bridging Local Mac GPU (Gemma 4) to Render Production Cloud  ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ------------------------------------------------------------------------------
# 1. PREREQUISITE CHECKS
# ------------------------------------------------------------------------------
echo -e "${CYAN}[1/5] Checking local prerequisites...${NC}"

# Check Python 3
if ! command -v python3 &>/dev/null; then
    echo -e "${RED}❌ python3 is required but not installed.${NC}"
    exit 1
fi

# Check cloudflared
if ! command -v cloudflared &>/dev/null; then
    echo -e "${YELLOW}⚠️  cloudflared is not installed.${NC}"
    echo "Installing via Homebrew: brew install cloudflared"
    brew install cloudflared
fi

# Check local Ollama daemon
if ! curl -s http://localhost:11434/api/tags &>/dev/null; then
    echo -e "${YELLOW}⚠️  Ollama daemon is not running on port 11434.${NC}"
    echo "Starting Ollama in the background..."
    ollama serve &
    sleep 3
    if ! curl -s http://localhost:11434/api/tags &>/dev/null; then
        echo -e "${RED}❌ Could not connect to Ollama at http://localhost:11434. Please start Ollama and retry.${NC}"
        exit 1
    fi
fi
echo -e "${GREEN}✓ Ollama daemon is running at http://localhost:11434${NC}"

# Check required models
TAGS_JSON=$(curl -s http://localhost:11434/api/tags)
if ! echo "$TAGS_JSON" | grep -q "gemma4:e4b"; then
    echo -e "${YELLOW}⚠️  Model 'gemma4:e4b' not found in Ollama. Pulling now...${NC}"
    ollama pull gemma4:e4b
fi
echo -e "${GREEN}✓ Chat model 'gemma4:e4b' is available${NC}"

if ! echo "$TAGS_JSON" | grep -q "nomic-embed-text"; then
    echo -e "${YELLOW}⚠️  Model 'nomic-embed-text' not found in Ollama. Pulling now...${NC}"
    ollama pull nomic-embed-text
fi
echo -e "${GREEN}✓ Embedding model 'nomic-embed-text' is available${NC}"

# ------------------------------------------------------------------------------
# 2. SHARED SECRET GENERATION & PERSISTENCE
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[2/5] Initializing security credentials...${NC}"

ENV_FILE=".env.ai-runner"
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

if [ -z "$GRADBOT_SECRET_KEY" ]; then
    GRADBOT_SECRET_KEY=$(openssl rand -hex 24)
    (
        umask 077
        cat <<EOF > "$ENV_FILE"
# GradBot Local AI Runner Configuration
# DO NOT COMMIT THIS FILE TO VERSION CONTROL
GRADBOT_SECRET_KEY=$GRADBOT_SECRET_KEY
PROXY_PORT=11435

# Optional: Add your Render credentials for 1-click automatic environment variable sync
# RENDER_API_KEY=rnd_...
# RENDER_SERVICE_ID=srv-...
EOF
    )
    chmod 600 "$ENV_FILE"
    echo -e "${GREEN}✓ Generated fresh shared secret in $ENV_FILE (mode 0600)${NC}"
else
    chmod 600 "$ENV_FILE" 2>/dev/null || true
    echo -e "${GREEN}✓ Loaded existing shared secret from $ENV_FILE${NC}"
fi

PROXY_PORT=${PROXY_PORT:-11435}
AUTH_HEADER="X-GradBot-Key"

# ------------------------------------------------------------------------------
# 3. START AUTHENTICATION PROXY
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[3/5] Starting authenticated reverse proxy on port $PROXY_PORT...${NC}"

PROXY_PID=""
TUNNEL_PID=""
TUNNEL_LOG=""

cleanup() {
    local exit_code=$?
    trap - EXIT SIGINT SIGTERM
    echo -e "\n${YELLOW}🛑 Shutting down GradBot AI Runner...${NC}"
    if [ -n "$PROXY_PID" ]; then
        kill -9 "$PROXY_PID" 2>/dev/null || true
    fi
    if [ -n "$TUNNEL_PID" ]; then
        kill -9 "$TUNNEL_PID" 2>/dev/null || true
    fi
    if [ -n "$TUNNEL_LOG" ] && [ -f "$TUNNEL_LOG" ]; then
        rm -f "$TUNNEL_LOG"
    fi
    echo -e "${GREEN}✓ Runner terminated cleanly.${NC}"
    exit "$exit_code"
}
trap cleanup EXIT SIGINT SIGTERM

# Safely check PROXY_PORT without terminating unrelated services
if lsof -Pi :$PROXY_PORT -sTCP:LISTEN -t &>/dev/null; then
    EXISTING_PID=$(lsof -Pi :$PROXY_PORT -sTCP:LISTEN -t | head -n 1)
    if ps -p "$EXISTING_PID" -o command= | grep -q "ai_proxy.py"; then
        echo -e "${YELLOW}⚠️  Existing GradBot proxy found on port $PROXY_PORT (PID: $EXISTING_PID). Restarting it...${NC}"
        kill -9 "$EXISTING_PID" 2>/dev/null || true
        sleep 1
    else
        echo -e "${RED}❌ Port $PROXY_PORT is already in use by PID $EXISTING_PID ($(ps -p "$EXISTING_PID" -o comm= 2>/dev/null || echo 'unknown')).${NC}"
        echo -e "Please configure a different PROXY_PORT in $ENV_FILE or stop the conflicting service."
        exit 1
    fi
fi

python3 scripts/ai_proxy.py --port "$PROXY_PORT" --secret "$GRADBOT_SECRET_KEY" --header "$AUTH_HEADER" &
PROXY_PID=$!
sleep 1

# Verify proxy health
if ! curl -s "http://127.0.0.1:$PROXY_PORT/health" | grep -q "ONLINE"; then
    echo -e "${RED}❌ Failed to start authentication proxy on port $PROXY_PORT.${NC}"
    kill -9 "$PROXY_PID" 2>/dev/null || true
    exit 1
fi
echo -e "${GREEN}✓ Secure proxy online at http://127.0.0.1:$PROXY_PORT${NC}"

# ------------------------------------------------------------------------------
# 4. START CLOUDFLARE TUNNEL
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[4/5] Establishing Cloudflare HTTPS tunnel...${NC}"

TUNNEL_LOG="/tmp/gradbot_tunnel_$$.log"
rm -f "$TUNNEL_LOG"

cloudflared tunnel --url "http://localhost:$PROXY_PORT" > "$TUNNEL_LOG" 2>&1 &
TUNNEL_PID=$!

echo -n "Waiting for public tunnel URL"
TUNNEL_URL=""
for i in {1..30}; do
    if [ -f "$TUNNEL_LOG" ]; then
        FOUND_URL=$(grep -o 'https://[-a-zA-Z0-9.]*\.trycloudflare\.com' "$TUNNEL_LOG" | head -n 1 || true)
        if [ -n "$FOUND_URL" ]; then
            TUNNEL_URL="$FOUND_URL"
            break
        fi
    fi
    echo -n "."
    sleep 1
done
echo ""

if [ -z "$TUNNEL_URL" ]; then
    echo -e "${RED}❌ Could not obtain Cloudflare tunnel URL within 30 seconds.${NC}"
    cat "$TUNNEL_LOG"
    cleanup
fi

echo -e "${GREEN}✓ Public HTTPS Tunnel: ${BOLD}${TUNNEL_URL}${NC}"

# ------------------------------------------------------------------------------
# 5. END-TO-END VERIFICATION
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[5/5] Running end-to-end verification ping...${NC}"

HTTP_CODE="000"
for attempt in {1..5}; do
    HTTP_CODE=$(curl -s -m 5 -H "$AUTH_HEADER: $GRADBOT_SECRET_KEY" -o /dev/null -w "%{http_code}" "$TUNNEL_URL/api/tags" || true)
    if [ "$HTTP_CODE" = "200" ]; then
        break
    fi
    sleep 1
done

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ End-to-end verification successful! (HTTP 200 OK from tunnel)${NC}"
else
    echo -e "${YELLOW}⚠️  Tunnel verification returned HTTP $HTTP_CODE. Still proceeding...${NC}"
fi

# Optional Render auto-sync if API key is provided
RENDER_SYNCED=false
if [ -n "$RENDER_API_KEY" ] && [ -n "$RENDER_SERVICE_ID" ]; then
    echo -e "\n${CYAN}🔄 Auto-updating Render environment variables (per-key to protect existing config)...${NC}"
    URL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars/OLLAMA_BASE_URL" \
        -H "Authorization: Bearer $RENDER_API_KEY" \
        -H "Content-Type: application/json" \
        -d "{\"value\": \"$TUNNEL_URL\"}" || true)
    TOKEN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars/OLLAMA_AUTH_TOKEN" \
        -H "Authorization: Bearer $RENDER_API_KEY" \
        -H "Content-Type: application/json" \
        -d "{\"value\": \"$GRADBOT_SECRET_KEY\"}" || true)

    if [ "$URL_STATUS" = "200" ] && [ "$TOKEN_STATUS" = "200" ]; then
        echo -e "${GREEN}✓ Render environment variables (OLLAMA_BASE_URL, OLLAMA_AUTH_TOKEN) updated automatically!${NC}"
        RENDER_SYNCED=true
    else
        echo -e "${YELLOW}⚠️  Render env var update returned status ($URL_STATUS, $TOKEN_STATUS). Check your RENDER_API_KEY and RENDER_SERVICE_ID.${NC}"
    fi
fi

# ------------------------------------------------------------------------------
# SUMMARY DISPLAY
# ------------------------------------------------------------------------------
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  🚀 GradBot Local AI Runner is ONLINE & READY!                 ${NC}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo -e "  ${BOLD}Public Tunnel URL :${NC} ${CYAN}${TUNNEL_URL}${NC}"
echo -e "  ${BOLD}Auth Header Key   :${NC} ${YELLOW}${AUTH_HEADER}${NC}"
echo -e "  ${BOLD}Auth Secret Token :${NC} ${YELLOW}${GRADBOT_SECRET_KEY}${NC}"
echo -e "  ${BOLD}Chat Model        :${NC} gemma4:e4b (Running on local Mac GPU)"
echo -e "  ${BOLD}Embedding Model   :${NC} nomic-embed-text (Running on local Mac)"
echo -e "${BOLD}${GREEN}────────────────────────────────────────────────────────────────${NC}"

if [ "$RENDER_SYNCED" = false ]; then
    echo -e "${BOLD}👉 To connect your Render deployment:${NC}"
    echo -e "   1. Go to your Render Dashboard -> Your Service -> ${BOLD}Environment${NC}"
    echo -e "   2. Update or add these two environment variables:"
    echo ""
    echo -e "      ${BOLD}OLLAMA_BASE_URL${NC}   = ${CYAN}${TUNNEL_URL}${NC}"
    echo -e "      ${BOLD}OLLAMA_AUTH_TOKEN${NC} = ${YELLOW}${GRADBOT_SECRET_KEY}${NC}"
    echo ""
    echo -e "   (Optionally set RENDER_API_KEY and RENDER_SERVICE_ID in .env.ai-runner"
    echo -e "    so the script syncs Render automatically on future launches!)"
else
    echo -e "  ${GREEN}✓ Render environment synchronized automatically.${NC}"
fi

echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo -e "Press ${BOLD}[Ctrl + C]${NC} to stop the runner and close the tunnel.\n"

# Keep the script running and tail proxy/tunnel logs
wait $TUNNEL_PID
