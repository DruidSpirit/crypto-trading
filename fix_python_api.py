import sys
import os

# 添加Python项目路径到sys.path
python_project_path = r"C:\Users\druid\PycharmProjects\crypto-trading-strategy"
sys.path.insert(0, python_project_path)

try:
    print("Testing import backtest_api...")
    from src.api.backtest_api import router as backtest_router
    print("SUCCESS: backtest_api imported")
    
    print("Testing import strategy_api...")
    from src.api.strategy_api import router as strategy_router  
    print("SUCCESS: strategy_api imported")
    
    # 测试路由信息
    print("\nBacktest routes:")
    for route in backtest_router.routes:
        print(f"  {route.methods} {route.path}")
        
    print("\nStrategy routes:")
    for route in strategy_router.routes:
        print(f"  {route.methods} {route.path}")
        
    print("SUCCESS: All imports working")
        
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()