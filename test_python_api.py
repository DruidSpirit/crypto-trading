import sys
import os

# 添加Python项目路径到sys.path
python_project_path = r"C:\Users\druid\PycharmProjects\crypto-trading-strategy"
sys.path.insert(0, python_project_path)

try:
    # 导入测试
    print("测试导入 backtest_api...")
    from src.api.backtest_api import router as backtest_router
    print("✅ backtest_api 导入成功")
    
    print("测试导入 strategy_api...")
    from src.api.strategy_api import router as strategy_router  
    print("✅ strategy_api 导入成功")
    
    # 测试路由信息
    print("\n回测路由信息:")
    for route in backtest_router.routes:
        print(f"  {route.methods} {route.path}")
        
    print("\n策略路由信息:")
    for route in strategy_router.routes:
        print(f"  {route.methods} {route.path}")
        
    # 创建修复的main.py
    fixed_main_content = '''from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from src.api.strategy_api import router as strategy_router
from src.api.backtest_api import router as backtest_router
import logging
import uvicorn

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

logger = logging.getLogger(__name__)

# 创建FastAPI应用
app = FastAPI(
    title="加密货币交易策略服务",
    description="提供加密货币交易策略执行的REST API服务",
    version="1.0.0"
)

# 添加CORS中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(strategy_router, prefix="/api/strategy", tags=["策略"])
app.include_router(backtest_router, prefix="/api/backtest", tags=["回测"])

@app.get("/")
async def root():
    return {
        "message": "加密货币交易策略服务",
        "version": "1.0.0",
        "status": "running"
    }

@app.get("/health")
async def health_check():
    return {"status": "healthy", "message": "服务运行正常"}

if __name__ == "__main__":
    logger.info("启动策略服务...")
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=False
    )
'''
    
    with open(os.path.join(python_project_path, "main_fixed.py"), "w", encoding="utf-8") as f:
        f.write(fixed_main_content)
    
    print("✅ 创建修复的 main_fixed.py 成功")
    
except Exception as e:
    print(f"❌ 错误: {e}")
    import traceback
    traceback.print_exc()