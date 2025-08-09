@echo off
echo ===========================================
echo Quick Start Script for Java Application
echo ===========================================

echo 1. 检查并设置JAVA_HOME...
set JAVA_HOME=C:\program1\java\jdk-21.0.8
echo JAVA_HOME set to: %JAVA_HOME%

echo.
echo 2. 检查Maven Wrapper...
if exist mvnw.cmd (
    echo Maven Wrapper found, attempting to run...
    
    echo 尝试清理和编译项目...
    call mvnw.cmd clean compile -DskipTests -X
    
    if %errorlevel% == 0 (
        echo 编译成功！
        echo.
        echo 3. 启动Spring Boot应用...
        call mvnw.cmd spring-boot:run -DskipTests
    ) else (
        echo 编译失败，尝试直接运行（如果之前编译过）...
        call mvnw.cmd spring-boot:run -DskipTests
    )
) else (
    echo Maven Wrapper not found!
    echo Please ensure Maven is properly installed.
)

pause