@echo off
echo =============================================
echo 验证Java项目修复是否正确
echo =============================================

echo 1. 检查关键类的语法...
echo.
echo 检查 KlineDataDTO.java:
findstr /n "BigDecimal\|openTime\|closePrice" src\main\java\druid\elf\tool\dto\KlineDataDTO.java

echo.
echo 检查 PythonStrategyAdapter.java:
findstr /n "executeStrategy\|setSymbol\|setStrategyName" src\main\java\druid\elf\tool\service\strategy\impl\PythonStrategyAdapter.java

echo.
echo 检查 RestTemplateConfig.java:
findstr /n "@Bean\|RestTemplate\|@Configuration" src\main\java\druid\elf\tool\config\RestTemplateConfig.java

echo.
echo 2. 检查导入语句...
echo.
echo PythonStrategyClientRestTemplate imports:
findstr /n "import.*RestTemplate\|import.*Service" src\main\java\druid\elf\tool\service\client\PythonStrategyClientRestTemplate.java

echo.
echo 3. 检查构造函数...
echo.
echo PythonStrategyClientRestTemplate constructor:
findstr /n "public.*PythonStrategyClientRestTemplate" src\main\java\druid\elf\tool\service\client\PythonStrategyClientRestTemplate.java

echo.
echo =============================================
echo 验证完成
echo =============================================
pause