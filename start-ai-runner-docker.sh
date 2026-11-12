#!/usr/bin/env bash
# ==============================================================================
# GradBot Portable Docker AI Runner (BVICAM | Campus Connect)
# ==============================================================================
# Runs the AI Edge Node (Ollama + Qdrant + Multiplexer Proxy + Cloudflare Tunnel)
# via Docker on ANY laptop (macOS, Windows with WSL/Docker, Linux).
# Automatically uses host GPU Ollama if already running (e.g., Apple Metal MLX),
# or spins up the full containerized Ollama service on a fresh machine.
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

# Helper to find first available TCP port starting from a preferred port
find_free_port() {
    local start_port=$1
    local port=$start_port
    while :; do
        if command -v lsof &>/dev/null; then
            if ! lsof -iTCP:"$port" -sTCP:LISTEN &>/dev/null; then
                echo "$port"
                return
            fi
        elif command -v python3 &>/dev/null; then
            if python3 -c "import socket,sys; s=socket.socket(); r=s.connect_ex(('127.0.0.1',$port)); s.close(); sys.exit(0 if r!=0 else 1)" 2>/dev/null; then
                echo "$port"
                return
            fi
        else
            echo "$port"
            return
        fi
        port=$((port + 1))
    done
}

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

    (
        umask 077
        if [ -s "$ENV_FILE" ]; then
            printf "\n" >> "$ENV_FILE"
        fi
        cat <<EOF >> "$ENV_FILE"
# GradBot Local AI Runner Authentication Secret
# Keep this file private and never commit to version control!
GRADBOT_SECRET_KEY=${GRADBOT_SECRET_KEY}
AUTH_HEADER=${AUTH_HEADER}

# Optional: Add Render credentials for 100% automated environment variable sync & rebuild
# RENDER_API_KEY=rnd_xxxxxxxxxxxx
# RENDER_SERVICE_ID=srv-xxxxxxxxxxxx
# RENDER_APP_URL=https://campus-connect-live.onrender.com
EOF
    )
    chmod 600 "$ENV_FILE"
    echo -e "${GREEN}✓ Secret generated and saved to ${ENV_FILE}${NC}"
else
    echo -e "${GREEN}✓ Loaded existing secret from ${ENV_FILE}${NC}"
fi

export GRADBOT_SECRET_KEY
export AUTH_HEADER

# ------------------------------------------------------------------------------
# 3. LAUNCH DOCKER CONTAINERS (SMART HOST OLLAMA & PORT DETECTION)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[3/5] Detecting runtime topology & launching Docker containers...${NC}"

# Avoid Qdrant port conflict if another project holds port 6333 on host
if [ -z "$AI_QDRANT_PORT" ]; then
    # Check if existing campus-ai-qdrant container already owns its bound port
    EXISTING_QDRANT_PORT=$(docker port campus-ai-qdrant 6333/tcp 2>/dev/null | head -1 | awk -F: '{print $NF}' || true)
    if [ -n "$EXISTING_QDRANT_PORT" ]; then
        AI_QDRANT_PORT="$EXISTING_QDRANT_PORT"
    else
        AI_QDRANT_PORT=$(find_free_port 6333)
    fi
fi
export AI_QDRANT_PORT

if [ -z "$AI_PROXY_PORT" ]; then
    EXISTING_PROXY_PORT=$(docker port campus-ai-proxy 11435/tcp 2>/dev/null | head -1 | awk -F: '{print $NF}' || true)
    if [ -n "$EXISTING_PROXY_PORT" ]; then
        AI_PROXY_PORT="$EXISTING_PROXY_PORT"
    else
        AI_PROXY_PORT=$(find_free_port 11435)
    fi
fi
export AI_PROXY_PORT

USE_HOST_OLLAMA=false
if [ "${FORCE_DOCKER_OLLAMA:-false}" != "true" ] && curl -s http://127.0.0.1:11434/api/tags &>/dev/null; then
    # Check if port 11434 belongs to host daemon (not an existing campus-ai-ollama container)
    CONTAINER_OLLAMA_RUNNING=$(docker ps --filter "name=^campus-ai-ollama$" --format "{{.Names}}" 2>/dev/null || true)
    if [ -z "$CONTAINER_OLLAMA_RUNNING" ]; then
        USE_HOST_OLLAMA=true
    fi
fi

if [ "$USE_HOST_OLLAMA" = true ]; then
    echo -e "${GREEN}✓ Native host Ollama detected on port 11434 (preserving GPU acceleration & cached models)${NC}"
    export AI_OLLAMA_TARGET="http://host.docker.internal:11434"
    $COMPOSE_CMD -f docker-compose.ai-runner.yml up -d --build qdrant
    $COMPOSE_CMD -f docker-compose.ai-runner.yml up -d --build --force-recreate ai-proxy cloudflared
    OLLAMA_MODE_LABEL="Host Native GPU (http://host.docker.internal:11434)"
else
    export AI_OLLAMA_TARGET="http://ollama:11434"
    $COMPOSE_CMD -f docker-compose.ai-runner.yml up -d --build qdrant ollama
    $COMPOSE_CMD -f docker-compose.ai-runner.yml up -d --build --force-recreate ai-proxy cloudflared
    OLLAMA_MODE_LABEL="Docker Container (campus-ai-ollama)"
fi

echo -e "${GREEN}✓ Containers deployed:${NC}"
echo -e "   • Qdrant Vector Engine   : container ${BOLD}campus-ai-qdrant${NC} (host port ${AI_QDRANT_PORT} -> 6333)"
echo -e "   • Ollama Inference Node  : ${BOLD}${OLLAMA_MODE_LABEL}${NC}"
echo -e "   • Security Auth Proxy    : container ${BOLD}campus-ai-proxy${NC} (host port ${AI_PROXY_PORT} -> 11435)"
echo -e "   • Cloudflare Edge Tunnel : container ${BOLD}campus-ai-tunnel${NC}"

# ------------------------------------------------------------------------------
# 4. MODEL VERIFICATION (HOST OR CONTAINER)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[4/5] Verifying AI models...${NC}"

if [ "$USE_HOST_OLLAMA" = true ]; then
    TAGS_JSON=$(curl -s http://127.0.0.1:11434/api/tags || true)
    if ! echo "$TAGS_JSON" | grep -q "embeddinggemma-2"; then
        echo -e "${YELLOW}⚠️  Pulling 'embeddinggemma-2:latest' on host Ollama...${NC}"
        ollama pull embeddinggemma-2:latest
    fi
    echo -e "${GREEN}✓ Embedding model 'embeddinggemma-2:latest' is ready on host${NC}"

    if ! echo "$TAGS_JSON" | grep -q "gemma4:e4b"; then
        echo -e "${YELLOW}⚠️  Pulling 'gemma4:e4b' on host Ollama...${NC}"
        ollama pull gemma4:e4b
    fi
    echo -e "${GREEN}✓ Chat model 'gemma4:e4b' is ready on host${NC}"
else
    echo "Waiting for Ollama container readiness..."
    for i in {1..30}; do
        if docker exec campus-ai-ollama ollama list &>/dev/null; then
            break
        fi
        sleep 1
    done

    OLLAMA_MODELS=$(docker exec campus-ai-ollama ollama list || true)

    if ! echo "$OLLAMA_MODELS" | grep -q "embeddinggemma-2"; then
        echo -e "${YELLOW}⚠️  Embedding model 'embeddinggemma-2:latest' not found in container.${NC}"
        echo "Downloading embeddinggemma-2:latest (~768-dim vector embeddings)..."
        docker exec campus-ai-ollama ollama pull embeddinggemma-2:latest
    fi
    echo -e "${GREEN}✓ Embedding model 'embeddinggemma-2:latest' is ready${NC}"

    if ! echo "$OLLAMA_MODELS" | grep -q "gemma4:e4b"; then
        echo -e "${YELLOW}⚠️  Chat model 'gemma4:e4b' not found in container.${NC}"
        echo "Downloading gemma4:e4b..."
        docker exec campus-ai-ollama ollama pull gemma4:e4b
    fi
    echo -e "${GREEN}✓ Chat model 'gemma4:e4b' is ready${NC}"
fi

# ------------------------------------------------------------------------------
# 5. RETRIEVE PUBLIC CLOUDFLARE TUNNEL URL, VERIFY & SYNC RENDER
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[5/5] Verifying Docker proxy, Cloudflare Tunnel & syncing Render...${NC}"

# Ensure local ai-proxy container responds on host port
for i in {1..15}; do
    if curl -s "http://127.0.0.1:${AI_PROXY_PORT}/health" | grep -q "ONLINE"; then
        break
    fi
    sleep 1
done

echo "Waiting for fresh public HTTPS tunnel hostname..."
TUNNEL_URL=""
for i in {1..30}; do
    TUNNEL_URL=$(docker logs campus-ai-tunnel 2>&1 | grep -o 'https://[-a-zA-Z0-9.]*\.trycloudflare\.com' | tail -n 1 || true)
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
    HTTP_CODE="000"
    for attempt in {1..10}; do
        HTTP_CODE=$(curl -s -m 8 -H "${AUTH_HEADER}: ${GRADBOT_SECRET_KEY}" -o /dev/null -w "%{http_code}" "${TUNNEL_URL}/api/tags" || true)
        if [ "$HTTP_CODE" = "200" ]; then
            break
        fi
        sleep 2
    done
    if [ "$HTTP_CODE" = "200" ]; then
        echo -e "${GREEN}✓ End-to-end tunnel verification successful! (HTTP 200 OK from tunnel)${NC}"
    else
        echo -e "${YELLOW}⚠️  Tunnel verification returned HTTP ${HTTP_CODE}. Proceeding with sync...${NC}"
    fi
fi

RENDER_APP_URL="${RENDER_APP_URL:-https://campus-connect-live.onrender.com}"
RENDER_APP_URL="${RENDER_APP_URL%/}"

extract_json_field() {
    local json="$1"
    local key="$2"
    if command -v python3 &>/dev/null; then
        JSON_INPUT="$json" JSON_KEY="$key" python3 -c "import json,os; d=json.loads(os.environ['JSON_INPUT']); v=d.get(os.environ['JSON_KEY'],''); print(str(v).lower() if isinstance(v,bool) else (v if isinstance(v,(str,int,float)) else ''))" 2>/dev/null || true
    else
        echo "$json" | tr -d '\n\r' | sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
    fi
}

# Step 5A: Instant zero-downtime hot-reload so active Render container switches in <1s
HOT_RELOADED=false
if [ -n "$RENDER_APP_URL" ] && [ "$TUNNEL_URL" != "https://<pending-check-docker-logs>.trycloudflare.com" ]; then
    echo -e "\n${CYAN}⚡ Syncing live Render endpoint via zero-downtime hot-reload (${RENDER_APP_URL})...${NC}"
    HOT_RESP_FILE=$(mktemp 2>/dev/null || echo "/tmp/gradbot_docker_hot_reload_$$.json")
    HOT_CODE=$(curl -s -o "$HOT_RESP_FILE" -w "%{http_code}" --connect-timeout 15 --max-time 75 \
        -X POST "${RENDER_APP_URL}/api/bot/runner-endpoint" \
        -H "Content-Type: application/json" \
        -H "${AUTH_HEADER}: ${GRADBOT_SECRET_KEY}" \
        -d "{\"baseUrl\": \"${TUNNEL_URL}\"}" || true)
    HOT_CODE=${HOT_CODE:-000}
    HOT_BODY=$(cat "$HOT_RESP_FILE" 2>/dev/null || true)
    rm -f "$HOT_RESP_FILE"

    if [ "$HOT_CODE" = "200" ] && echo "$HOT_BODY" | grep -q '"ollamaOnline"[[:space:]]*:[[:space:]]*true'; then
        HOT_RELOADED=true
        echo -e "${GREEN}✓ Instant Hot-Reload applied! Live GradBot on Render is ONLINE now (<1s).${NC}"
    else
        echo -e "${YELLOW}ℹ️  Instant hot-reload returned HTTP ${HOT_CODE}; Render rebuild will apply the updated URL.${NC}"
    fi
fi

# Step 5B: Persist Render env vars & automatically trigger Render rebuild (poll until live if needed)
RENDER_SYNCED=false
DEPLOY_ID=""
if [ -n "$RENDER_API_KEY" ] && [ -n "$RENDER_SERVICE_ID" ] && [ "$TUNNEL_URL" != "https://<pending-check-docker-logs>.trycloudflare.com" ]; then
    echo -e "\n${CYAN}🔄 Persisting Render environment variables & queuing automatic rebuild...${NC}"
    sync_render_var() {
        local key="$1"
        local val="$2"
        curl -s -o /dev/null -w "%{http_code}" -X PUT \
            "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/env-vars/${key}" \
            -H "Accept: application/json" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${RENDER_API_KEY}" \
            -d "{\"value\": \"${val}\"}" || true
    }

    URL_STATUS=$(sync_render_var "OLLAMA_BASE_URL" "${TUNNEL_URL}")
    TOKEN_STATUS=$(sync_render_var "OLLAMA_AUTH_TOKEN" "${GRADBOT_SECRET_KEY}")
    HEADER_STATUS=$(sync_render_var "OLLAMA_AUTH_HEADER" "${AUTH_HEADER}")

    if { [ "$URL_STATUS" = "200" ] || [ "$URL_STATUS" = "201" ]; } && \
       { [ "$TOKEN_STATUS" = "200" ] || [ "$TOKEN_STATUS" = "201" ]; } && \
       { [ "$HEADER_STATUS" = "200" ] || [ "$HEADER_STATUS" = "201" ]; }; then
        echo -e "${GREEN}✓ Render environment variables persisted successfully!${NC}"
        RENDER_SYNCED=true

        DEPLOY_RESP_FILE=$(mktemp 2>/dev/null || echo "/tmp/gradbot_docker_deploy_$$.json")
        DEPLOY_HTTP=$(curl -s -o "$DEPLOY_RESP_FILE" -w "%{http_code}" -X POST \
            "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/deploys" \
            -H "Authorization: Bearer ${RENDER_API_KEY}" \
            -H "Content-Type: application/json" \
            -d '{"clearCache": "do_not_clear"}' || true)
        DEPLOY_BODY=$(cat "$DEPLOY_RESP_FILE" 2>/dev/null || true)
        rm -f "$DEPLOY_RESP_FILE"

        if [ "$DEPLOY_HTTP" = "201" ] || [ "$DEPLOY_HTTP" = "200" ]; then
            DEPLOY_ID=$(extract_json_field "$DEPLOY_BODY" "id")
            echo -e "${GREEN}✓ Triggered automatic Render rolling rebuild${DEPLOY_ID:+ (Deploy: ${DEPLOY_ID})}!${NC}"

            if [ "$HOT_RELOADED" = false ] && [ -n "$DEPLOY_ID" ]; then
                echo -e "${CYAN}⏳ Waiting for Render deploy to go LIVE (so GradBot never opens in standby mode)...${NC}"
                POLL_START=$(date +%s)
                LAST_STATUS=""
                FINAL_STATUS=""
                for attempt in $(seq 1 45); do
                    STATUS_JSON=$(curl -s -m 10 \
                        -H "Authorization: Bearer ${RENDER_API_KEY}" \
                        "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/deploys/${DEPLOY_ID}" || true)
                    CURR_STATUS=$(extract_json_field "$STATUS_JSON" "status")
                    ELAPSED=$(( $(date +%s) - POLL_START ))
                    if [ "$CURR_STATUS" != "$LAST_STATUS" ] || [ $((attempt % 3)) -eq 0 ]; then
                        echo -e "   [${ELAPSED}s] Render deploy status: ${BOLD}${CURR_STATUS:-checking}${NC}"
                        LAST_STATUS="$CURR_STATUS"
                    fi
                    if [ "$CURR_STATUS" = "live" ]; then
                        FINAL_STATUS="live"
                        break
                    elif [ "$CURR_STATUS" = "build_failed" ] || [ "$CURR_STATUS" = "update_failed" ] || [ "$CURR_STATUS" = "canceled" ] || [ "$CURR_STATUS" = "deactivated" ]; then
                        FINAL_STATUS="$CURR_STATUS"
                        break
                    fi
                    sleep 8
                done

                if [ "$FINAL_STATUS" = "live" ]; then
                    echo -e "${GREEN}✓ Render deploy ${DEPLOY_ID} is LIVE! Verifying GradBot tunnel link...${NC}"
                    POST_LIVE_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 15 --max-time 30 \
                        -X POST "${RENDER_APP_URL}/api/bot/runner-endpoint" \
                        -H "Content-Type: application/json" \
                        -H "${AUTH_HEADER}: ${GRADBOT_SECRET_KEY}" \
                        -d "{\"baseUrl\": \"${TUNNEL_URL}\"}" || true)
                    if [ "$POST_LIVE_CODE" = "200" ]; then
                        HOT_RELOADED=true
                        echo -e "${GREEN}✓ End-to-end Render <-> Docker AI link verified (HTTP 200)!${NC}"
                    fi
                elif [ -n "$FINAL_STATUS" ]; then
                    echo -e "${RED}⚠️  Render deploy ended with status '${FINAL_STATUS}'. Check Render dashboard logs.${NC}"
                else
                    echo -e "${YELLOW}⚠️  Render deploy is still in progress after 6 minutes. Check Render dashboard.${NC}"
                fi
            elif [ "$HOT_RELOADED" = true ]; then
                echo -e "${GREEN}✓ Background Render rebuild queued — GradBot is already LIVE right now via hot-reload!${NC}"
            fi
        else
            echo -e "${YELLOW}⚠️  Render deploy trigger returned HTTP ${DEPLOY_HTTP}. Check Render dashboard or API key.${NC}"
        fi
    else
        echo -e "${YELLOW}⚠️  Render API sync returned statuses (${URL_STATUS}, ${TOKEN_STATUS}, ${HEADER_STATUS}). Please verify manually.${NC}"
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
echo -e "  ${BOLD}Chat Model        :${NC} gemma4:e4b (${OLLAMA_MODE_LABEL})"
echo -e "  ${BOLD}Embedding Model   :${NC} embeddinggemma-2:latest (768 dimensions)"
echo -e "  ${BOLD}Vector Database   :${NC} Qdrant (HNSW Vector Engine in campus-ai-qdrant)"
echo -e "  ${BOLD}Tunnel Multiplex  :${NC} Ollama (/api/*) & Qdrant (/qdrant/*, /collections/*)"
echo -e "${BOLD}${GREEN}────────────────────────────────────────────────────────────────${NC}"

if [ "$RENDER_SYNCED" = false ] && [ "$HOT_RELOADED" = false ]; then
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
    echo "4. Trigger 'Manual Deploy' (or configure RENDER_API_KEY + RENDER_SERVICE_ID in .env.ai-runner for 100% automation)!"
elif [ "$RENDER_SYNCED" = true ]; then
    echo -e "\n  ${GREEN}✓ Render env vars persisted & automatic rebuild triggered${DEPLOY_ID:+ (${DEPLOY_ID})}.${NC}"
    if [ "$HOT_RELOADED" = true ]; then
        echo -e "  ${GREEN}✓ Live GradBot is ALREADY CONNECTED now via Instant Hot-Reload (<1s).${NC}"
    fi
else
    echo -e "\n  ${GREEN}✓ Live GradBot connected via Instant Hot-Reload (set RENDER_API_KEY to also persist env vars).${NC}"
fi

echo -e "\n${BOLD}Helpful Docker Commands:${NC}"
echo -e "  • View live AI proxy logs : ${CYAN}docker logs -f campus-ai-proxy${NC}"
echo -e "  • View Cloudflare logs    : ${CYAN}docker logs -f campus-ai-tunnel${NC}"
echo -e "  • View Qdrant logs        : ${CYAN}docker logs -f campus-ai-qdrant${NC}"
echo -e "  • Stop all AI containers  : ${CYAN}$COMPOSE_CMD -f docker-compose.ai-runner.yml down${NC}"
echo ""
