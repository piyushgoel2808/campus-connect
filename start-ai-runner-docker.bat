@echo off
REM ==============================================================================
REM GradBot Portable Docker AI Runner for Windows (BVICAM | Campus Connect)
REM ==============================================================================

echo ================================================================
echo   GradBot Docker AI Runner (Windows Edge Node)
echo   Bridging Local AI (Ollama + Qdrant) to Render Cloud
echo ================================================================

REM Check Docker
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker daemon is not running!
    echo Please start Docker Desktop for Windows and try again.
    pause
    exit /b 1
)

REM Setup Secret
set ENV_FILE=.env.ai-runner
set AUTH_HEADER=X-GradBot-Key

if exist "%ENV_FILE%" (
    for /f "usebackq tokens=1,2 delims==" %%G in ("%ENV_FILE%") do (
        if "%%G"=="GRADBOT_SECRET_KEY" set GRADBOT_SECRET_KEY=%%H
    )
)

if "%GRADBOT_SECRET_KEY%"=="" (
    powershell -Command "[guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')" > temp_key.txt
    set /p GRADBOT_SECRET_KEY=<temp_key.txt
    del temp_key.txt
    echo GRADBOT_SECRET_KEY=%GRADBOT_SECRET_KEY%>> "%ENV_FILE%"
    echo AUTH_HEADER=%AUTH_HEADER%>> "%ENV_FILE%"
)

echo [1/3] Starting AI Docker Containers...
docker compose -f docker-compose.ai-runner.yml up -d --build

echo [2/3] Checking Ollama Models...
docker exec campus-ai-ollama ollama pull embeddinggemma-2:latest
docker exec campus-ai-ollama ollama pull gemma4:e4b

echo [3/3] Getting Cloudflare Tunnel URL...
timeout /t 5 /nobreak >nul
docker logs campus-ai-tunnel 2>&1 | findstr "trycloudflare.com"

echo.
echo ================================================================
echo   GRADBOT DOCKER AI RUNNER IS RUNNING!
echo   Secret Key : %GRADBOT_SECRET_KEY%
echo   Auth Header: %AUTH_HEADER%
echo ================================================================
echo To stop: docker compose -f docker-compose.ai-runner.yml down
pause
