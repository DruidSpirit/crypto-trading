import sys
import os

# 添加Python项目路径到sys.path
python_project_path = r"C:\Users\druid\PycharmProjects\crypto-trading-strategy"
sys.path.insert(0, python_project_path)
os.chdir(python_project_path)

from fastapi import FastAPI
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
    print("Starting Python Strategy Service with all APIs...")
    print("Available endpoints:")
    print("  Strategy API: /api/strategy/*")
    print("  Backtest API: /api/backtest/*")
    print("  Docs: http://localhost:8000/docs")
    
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8001,
        reload=False
    )