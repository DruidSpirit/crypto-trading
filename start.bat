@echo off
REM 设置 JRE 路径
set JRE_PATH=.\zulu17.56.15-ca-jre17.0.14-win_x64\bin\javaw.exe

REM 设置 JAR 包路径
set JAR_PATH=.\crypto-trading-0.0.1-SNAPSHOT.jar

REM 设置日志文件（可选）
set LOG_FILE=.\app.log

REM 启动 JAR 包并在后台运行
%JRE_PATH% -jar %JAR_PATH% > %LOG_FILE% 2>&1

REM 获取后台进程ID
for /f "tokens=2 delims=," %%a in ('wmic process where "name='javaw.exe' and commandline like '%%tool-0.0.1-SNAPSHOT.jar%%'" get ProcessId /format:csv') do (
    set PID=%%a
)

REM 输出后台进程ID
echo JAR process started in the background with PID: %PID%
pause