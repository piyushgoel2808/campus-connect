#!/usr/bin/env bash
# ==============================================================================
# GradBot Local AI Runner (BVICAM | Campus Connect)
# ==============================================================================
# Exposes local Ollama (gemma4:e4b + embeddinggemma-2:latest) and Qdrant to Render over a secure
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
    if command -v brew &>/dev/null; then
        echo "Installing via Homebrew: brew install cloudflared"
        brew install cloudflared
    else
        echo -e "${YELLOW}Homebrew not detected.${NC}"
        echo -e "💡 ${BOLD}Tip:${NC} You can run ${CYAN}./start-ai-runner-docker.sh${NC} which bundles cloudflared and Qdrant in Docker automatically!"
        echo "Or install cloudflared manually: https://github.com/cloudflare/cloudflared/releases"
        exit 1
    fi
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

if ! echo "$TAGS_JSON" | grep -q "embeddinggemma-2"; then
    echo -e "${YELLOW}⚠️  Model 'embeddinggemma-2' not found in Ollama. Pulling now...${NC}"
    ollama pull embeddinggemma-2
fi
echo -e "${GREEN}✓ Embedding model 'embeddinggemma-2' is available${NC}"

# Check local Qdrant Vector Database
QDRANT_PID=""
if ! curl -s http://localhost:6333/ &>/dev/null; then
    echo -e "${YELLOW}⚠️  Qdrant vector DB is not running on port 6333.${NC}"
    if [ ! -f "bin/qdrant" ]; then
        ARCH=$(uname -m)
        OS=$(uname -s | tr '[:upper:]' '[:lower:]')
        QDRANT_URL=""
        if [ "$OS" = "darwin" ] && [ "$ARCH" = "arm64" ]; then
            QDRANT_URL="https://github.com/qdrant/qdrant/releases/download/v1.20.0/qdrant-aarch64-apple-darwin.tar.gz"
        elif [ "$OS" = "darwin" ] && [ "$ARCH" = "x86_64" ]; then
            QDRANT_URL="https://github.com/qdrant/qdrant/releases/download/v1.20.0/qdrant-x86_64-apple-darwin.tar.gz"
        elif [ "$OS" = "linux" ] && [ "$ARCH" = "x86_64" ]; then
            QDRANT_URL="https://github.com/qdrant/qdrant/releases/download/v1.20.0/qdrant-x86_64-unknown-linux-gnu.tar.gz"
        elif [ "$OS" = "linux" ] && [ "$ARCH" = "aarch64" ]; then
            QDRANT_URL="https://github.com/qdrant/qdrant/releases/download/v1.20.0/qdrant-aarch64-unknown-linux-gnu.tar.gz"
        fi

        if [ -n "$QDRANT_URL" ]; then
            echo -e "${CYAN}Downloading standalone Qdrant binary for ${OS} ${ARCH}...${NC}"
            mkdir -p bin
            curl -sL "$QDRANT_URL" | tar -xz -C bin/
            chmod +x bin/qdrant
        else
            echo -e "${YELLOW}Could not determine prebuilt binary for ${OS} ${ARCH}.${NC}"
            echo -e "💡 ${BOLD}Tip:${NC} Run ${CYAN}./start-ai-runner-docker.sh${NC} to run Qdrant inside Docker!"
        fi
    fi
    if [ -f "bin/qdrant" ]; then
        echo "Starting Qdrant in the background..."
        ./bin/qdrant > /dev/null 2>&1 &
        QDRANT_PID=$!
        sleep 2
        if ! curl -s http://localhost:6333/ &>/dev/null; then
            echo -e "${YELLOW}⚠️  Could not connect to Qdrant at http://localhost:6333. Proceeding in standby...${NC}"
        else
            echo -e "${GREEN}✓ Qdrant Vector DB started on http://localhost:6333 (PID: $QDRANT_PID)${NC}"
        fi
    fi
else
    echo -e "${GREEN}✓ Qdrant Vector DB is running at http://localhost:6333${NC}"
fi

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

# Optional: Add your Render credentials for 1-click automatic environment variable sync & rebuild
# RENDER_API_KEY=rnd_...
# RENDER_SERVICE_ID=srv-...
# RENDER_APP_URL=https://campus-connect-live.onrender.com
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
    if [ -n "$QDRANT_PID" ]; then
        kill -9 "$QDRANT_PID" 2>/dev/null || true
    fi
    if [ -n "$TUNNEL_LOG" ] && [ -f "$TUNNEL_LOG" ]; then
        rm -f "$TUNNEL_LOG"
    fi
    echo -e "${GREEN}✓ Runner terminated cleanly.${NC}"
    exit "$exit_code"
}
trap cleanup EXIT SIGINT SIGTERM

# Safely find a usable PROXY_PORT without terminating unrelated services
while lsof -Pi :$PROXY_PORT -sTCP:LISTEN -t &>/dev/null; do
    EXISTING_PID=$(lsof -Pi :$PROXY_PORT -sTCP:LISTEN -t | head -n 1)
    if ps -p "$EXISTING_PID" -o command= 2>/dev/null | grep -q "ai_proxy.py"; then
        echo -e "${YELLOW}⚠️  Existing GradBot proxy found on port $PROXY_PORT (PID: $EXISTING_PID). Restarting it...${NC}"
        kill -9 "$EXISTING_PID" 2>/dev/null || true
        sleep 1
        break
    else
        echo -e "${YELLOW}⚠️  Port $PROXY_PORT is in use by PID $EXISTING_PID ($(ps -p "$EXISTING_PID" -o comm= 2>/dev/null || echo 'external service')).${NC}"
        PROXY_PORT=$((PROXY_PORT + 1))
        echo -e "${CYAN}→ Auto-selecting next available port: $PROXY_PORT${NC}"
    fi
done

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
# 5. END-TO-END VERIFICATION, INSTANT HOT-RELOAD & RENDER AUTO-REBUILD
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}[5/5] Running end-to-end tunnel verification & Render synchronization...${NC}"

HTTP_CODE="000"
for attempt in {1..10}; do
    HTTP_CODE=$(curl -s -m 8 -H "$AUTH_HEADER: $GRADBOT_SECRET_KEY" -o /dev/null -w "%{http_code}" "$TUNNEL_URL/api/tags" || true)
    if [ "$HTTP_CODE" = "200" ]; then
        break
    fi
    sleep 2
done

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ End-to-end verification successful! (HTTP 200 OK from tunnel)${NC}"
else
    echo -e "${YELLOW}⚠️  Tunnel verification returned HTTP $HTTP_CODE. Proceeding with sync...${NC}"
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

# Step 5A: Instant zero-downtime hot-reload so the active Render server connects in <1s
HOT_RELOADED=false
if [ -n "$RENDER_APP_URL" ]; then
    echo -e "\n${CYAN}⚡ Syncing live Render endpoint via zero-downtime hot-reload (${RENDER_APP_URL})...${NC}"
    HOT_RESP_FILE=$(mktemp 2>/dev/null || echo "/tmp/gradbot_hot_reload_$$.json")
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

# Step 5B: Persist Render env vars & automatically trigger Render rebuild (poll until live if hot-reload didn't succeed)
RENDER_SYNCED=false
DEPLOY_ID=""
if [ -n "$RENDER_API_KEY" ] && [ -n "$RENDER_SERVICE_ID" ]; then
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
        echo -e "${GREEN}✓ Render env vars (OLLAMA_BASE_URL, OLLAMA_AUTH_TOKEN, OLLAMA_AUTH_HEADER) persisted!${NC}"
        RENDER_SYNCED=true

        DEPLOY_RESP_FILE=$(mktemp 2>/dev/null || echo "/tmp/gradbot_deploy_$$.json")
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
                        echo -e "${GREEN}✓ End-to-end Render <-> Mac AI link verified (HTTP 200)!${NC}"
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
        echo -e "${YELLOW}⚠️  Render env var update returned status ($URL_STATUS, $TOKEN_STATUS, $HEADER_STATUS). Check your RENDER_API_KEY and RENDER_SERVICE_ID.${NC}"
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
echo -e "  ${BOLD}Embedding Model   :${NC} embeddinggemma-2:latest (Running on local Mac)"
echo -e "  ${BOLD}Vector Database   :${NC} Qdrant (HNSW Vector Index on Port 6333)"
echo -e "  ${BOLD}Tunnel Multiplex  :${NC} Ollama (/api/*) & Qdrant (/qdrant/*, /collections/*)"
echo -e "${BOLD}${GREEN}────────────────────────────────────────────────────────────────${NC}"

if [ "$RENDER_SYNCED" = false ] && [ "$HOT_RELOADED" = false ]; then
    echo -e "${BOLD}👉 To connect your Render deployment:${NC}"
    echo -e "   1. Go to your Render Dashboard -> Your Service -> ${BOLD}Environment${NC}"
    echo -e "   2. Update or add these two environment variables:"
    echo ""
    echo -e "      ${BOLD}OLLAMA_BASE_URL${NC}   = ${CYAN}${TUNNEL_URL}${NC}"
    echo -e "      ${BOLD}OLLAMA_AUTH_TOKEN${NC} = ${YELLOW}${GRADBOT_SECRET_KEY}${NC}"
    echo ""
    echo -e "   (Optionally set RENDER_API_KEY and RENDER_SERVICE_ID in .env.ai-runner"
    echo -e "    so the script persists & rebuilds Render automatically on future launches!)"
elif [ "$RENDER_SYNCED" = true ]; then
    echo -e "  ${GREEN}✓ Render env vars persisted & automatic rebuild triggered${DEPLOY_ID:+ (${DEPLOY_ID})}.${NC}"
    if [ "$HOT_RELOADED" = true ]; then
        echo -e "  ${GREEN}✓ Live GradBot is ALREADY CONNECTED now via Instant Hot-Reload (<1s).${NC}"
    fi
else
    echo -e "  ${GREEN}✓ Live GradBot connected via Instant Hot-Reload (set RENDER_API_KEY to also persist env vars).${NC}"
fi

echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo -e "Press ${BOLD}[Ctrl + C]${NC} to stop the runner and close the tunnel.\n"

# Keep the script running and tail proxy/tunnel logs
wait $TUNNEL_PID
