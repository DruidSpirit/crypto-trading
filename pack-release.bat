@echo off
setlocal enabledelayedexpansion
echo ===========================================
echo  Crypto Trading - Release Packager
echo ===========================================
echo.

:: ---- Config ----
set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
set "RELEASE_NAME=crypto-trading-release"
set "RELEASE_DIR=%PROJECT_DIR%\%RELEASE_NAME%"
set "ZIP_NAME=%RELEASE_NAME%.zip"
set "JAR_NAME=crypto-trading.jar"
set "PYTHON_SRC=%PROJECT_DIR%\python-strategy-service"
set "PYTHON_DST=%RELEASE_DIR%\python-strategy-service"
set "TEMPLATE_DIR=%PROJECT_DIR%\release-template"

:: ---- Step 1: Build Java JAR ----
echo [1/4] Building Java JAR...
cd /d "%PROJECT_DIR%"
call mvnw.cmd clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)
echo      Done.

:: ---- Step 2: Prepare release directory ----
echo [2/4] Preparing release directory...
if exist "%RELEASE_DIR%" rd /s /q "%RELEASE_DIR%"
mkdir "%RELEASE_DIR%"

:: Copy JAR
copy /y "%PROJECT_DIR%\target\crypto-trading-0.0.1-SNAPSHOT.jar" "%RELEASE_DIR%\%JAR_NAME%" >nul

:: Copy Python service (exclude __pycache__, *.db, *.pyc, data/)
mkdir "%PYTHON_DST%"
xcopy "%PYTHON_SRC%\*.py" "%PYTHON_DST%\" /y /q >nul
xcopy "%PYTHON_SRC%\requirements.txt" "%PYTHON_DST%\" /y /q >nul
if exist "%PYTHON_SRC%\.env.example" copy /y "%PYTHON_SRC%\.env.example" "%PYTHON_DST%\" >nul

:: Copy src directory
mkdir "%PYTHON_DST%\src"
for /d /r "%PYTHON_SRC%\src" %%d in (*) do (
    set "rel=%%d"
    set "rel=!rel:%PYTHON_SRC%\src=!"
    mkdir "%PYTHON_DST%\src!rel!" 2>nul
)
for /r "%PYTHON_SRC%\src" %%f in (*.py) do (
    set "filepath=%%f"
    set "filepath=!filepath:%PYTHON_SRC%\src=%PYTHON_DST%\src!"
    xcopy "%%f" "!filepath!" /y /q >nul
)

:: Copy startup scripts
copy /y "%TEMPLATE_DIR%\start-java.bat" "%RELEASE_DIR%\" >nul
copy /y "%TEMPLATE_DIR%\start-java.sh" "%RELEASE_DIR%\" >nul
copy /y "%TEMPLATE_DIR%\start-python.bat" "%RELEASE_DIR%\" >nul
copy /y "%TEMPLATE_DIR%\start-python.sh" "%RELEASE_DIR%\" >nul
echo      Done.

:: ---- Step 3: Create README ----
echo [3/4] Creating README...
(
echo # Crypto Trading
echo.
echo ## Requirements
echo.
echo - **Java**: JRE 17 or above
echo - **Python**: 3.9 or above
echo.
echo ## Quick Start
echo.
echo ### Windows
echo.
echo 1. Double-click `start-java.bat` to start the Java backend
echo 2. Double-click `start-python.bat` to start the Python strategy service
echo.
echo ### Linux / macOS
echo.
echo ```
echo chmod +x start-java.sh start-python.sh
echo ./start-java.sh
echo ./start-python.sh
echo ```
echo.
echo ## Access
echo.
echo - Web UI: http://localhost:5567
echo - Python API: http://localhost:8001
echo.
echo ## Notes
echo.
echo - The Java backend must be started first, then the Python service.
echo - Each service runs in its own terminal window. Close the window to stop.
echo - The Python startup script will auto-install dependencies via `pip install`.
) > "%RELEASE_DIR%\README.txt"
echo      Done.

:: ---- Step 4: Create ZIP ----
echo [4/4] Creating ZIP archive...
cd /d "%PROJECT_DIR%"
if exist "%ZIP_NAME%" del /f "%ZIP_NAME%"
powershell -Command "Compress-Archive -Path '%RELEASE_DIR%\*' -DestinationPath '%PROJECT_DIR%\%ZIP_NAME%' -Force"
if %errorlevel% neq 0 (
    echo [ERROR] Failed to create ZIP!
    pause
    exit /b 1
)

:: Cleanup temp directory
rd /s /q "%RELEASE_DIR%"

echo.
echo ===========================================
echo  Release package created successfully!
echo  File: %PROJECT_DIR%\%ZIP_NAME%
echo ===========================================
pause
