@echo off
echo ==============================================
echo 埃尔德策略集成测试
echo ==============================================

echo.
echo 1. 测试Python策略服务...
cd /d "C:\Users\druid\PycharmProjects\crypto-trading-strategy"
python -c "from src.strategies.strategy_factory import StrategyFactory; print('Available Python strategies:', StrategyFactory.get_available_strategies())"

echo.
echo 2. 检查Java策略适配器文件...
cd /d "C:\Users\druid\IdeaProjects\crypto-trading"
if exist "src\main\java\druid\elf\tool\service\strategy\impl\ElderSwingStrategyAdapter.java" (
    echo [OK] ElderSwingStrategyAdapter.java 存在
) else (
    echo [ERROR] ElderSwingStrategyAdapter.java 不存在
)

if exist "src\main\java\druid\elf\tool\service\strategy\impl\ElderIntradayStrategyAdapter.java" (
    echo [OK] ElderIntradayStrategyAdapter.java 存在
) else (
    echo [ERROR] ElderIntradayStrategyAdapter.java 不存在
)

echo.
echo 3. 检查旧策略是否已删除...
if not exist "src\main\java\druid\elf\tool\service\strategy\impl\SimpleMacdCrossStrategy.java" (
    echo [OK] SimpleMacdCrossStrategy.java 已删除
) else (
    echo [ERROR] SimpleMacdCrossStrategy.java 仍然存在
)

if not exist "src\main\java\druid\elf\tool\service\strategy\impl\PythonMacdCrossStrategy.java" (
    echo [OK] PythonMacdCrossStrategy.java 已删除
) else (
    echo [ERROR] PythonMacdCrossStrategy.java 仍然存在
)

if not exist "src\main\java\druid\elf\tool\service\strategy\impl\PythonStrategyAdapter.java" (
    echo [OK] PythonStrategyAdapter.java 已删除
) else (
    echo [ERROR] PythonStrategyAdapter.java 仍然存在
)

echo.
echo 4. 检查Python旧策略是否已删除...
cd /d "C:\Users\druid\PycharmProjects\crypto-trading-strategy"
if not exist "src\strategies\simple_macd_strategy.py" (
    echo [OK] simple_macd_strategy.py 已删除
) else (
    echo [ERROR] simple_macd_strategy.py 仍然存在
)

if not exist "src\strategies\macd_cross_strategy.py" (
    echo [OK] macd_cross_strategy.py 已删除
) else (
    echo [ERROR] macd_cross_strategy.py 仍然存在
)

echo.
echo ==============================================
echo 清理总结
echo ==============================================
echo 已删除策略：
echo   Java: SimpleMacdCrossStrategy, PythonMacdCrossStrategy, PythonStrategyAdapter
echo   Python: simple_macd_strategy, macd_cross_strategy
echo.
echo 新增策略：
echo   Java: ElderSwingStrategyAdapter, ElderIntradayStrategyAdapter
echo   Python: ElderSwingStrategy, ElderIntradayStrategy
echo ==============================================

pause