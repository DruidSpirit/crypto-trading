@echo off
title Crypto Trading - Python Strategy Service
echo ===========================================
echo  Crypto Trading - Python Strategy Service
echo ===========================================
echo.
where python >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python not found. Please install Python 3.9 or above.
    pause
    exit /b 1
)
cd /d "%~dp0python-strategy-service"
echo Installing dependencies...
pip install -r requirements.txt
echo.
echo Starting Python service on port 8001...
echo Close this window to stop.
echo.
set PORT=8001
python run.py
pause
