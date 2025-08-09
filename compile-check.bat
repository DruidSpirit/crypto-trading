@echo off
echo ========================================
echo Java项目编译检查
echo ========================================

echo 1. 检查Java版本...
java -version

echo.
echo 2. 检查Maven Wrapper...
if exist "mvnw.cmd" (
    echo Maven Wrapper exists
    echo 尝试获取Maven版本...
    call mvnw.cmd --version
) else (
    echo Maven Wrapper not found
)

echo.
echo 3. 尝试编译项目...
if exist "mvnw.cmd" (
    echo 开始编译...
    call mvnw.cmd clean compile -DskipTests -q
    if %errorlevel% == 0 (
        echo 编译成功!
    ) else (
        echo 编译失败，错误码: %errorlevel%
    )
) else (
    echo 无法编译，Maven Wrapper缺失
)

echo.
echo 4. 检查target目录...
if exist "target" (
    echo Target目录存在
    dir target /b
) else (
    echo Target目录不存在
)

echo.
echo 编译检查完成
pause