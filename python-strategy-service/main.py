from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from src.api.strategy_api import router as strategy_router
from src.api.backtest_api import router as backtest_router
from src.api.data_api import router as data_router
from src.api.sync_api import router as sync_router
import logging
import uvicorn
from contextlib import asynccontextmanager

# Configure logging
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifecycle management"""
    logger.info("Strategy service starting...")

    # Auto-sync strategies on startup - call sync service directly instead of sending HTTP request
    try:
        from src.services.strategy_sync_service import StrategySyncService
        from src.database.connection import test_connection, create_tables

        logger.info("Starting startup strategy sync...")

        # Test database connection
        if test_connection():
            logger.info("Database connection successful")
            # Ensure tables exist
            create_tables()

            # Execute sync
            sync_service = StrategySyncService()
            result = sync_service.sync_strategies_to_database()

            if result.get("success"):
                logger.info(f"Startup strategy sync successful: {result}")
            else:
                logger.warning(f"Startup strategy sync failed: {result}")
        else:
            logger.warning("Database connection failed, skipping startup strategy sync")

    except Exception as e:
        logger.error(f"Startup sync failed: {e}", exc_info=True)

    yield
    logger.info("Strategy service shutting down...")


# Create FastAPI application
app = FastAPI(
    title="Crypto Trading Strategy Service",
    description="REST API service for crypto trading strategy execution",
    version="1.0.0",
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Should restrict to specific domains in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routes
logger.info("Registering strategy routes...")
app.include_router(strategy_router, prefix="/api/strategy", tags=["Strategy"])
logger.info("Registering backtest routes...")
app.include_router(backtest_router, prefix="/api/backtest", tags=["Backtest"])
logger.info("Registering data management routes...")
logger.info(f"Data router type: {type(data_router)}")
logger.info(f"Data router routes: {len(data_router.routes)}")
for route in data_router.routes:
    if hasattr(route, 'path'):
        logger.info(f"  Data router route: {route.path}")

try:
    app.include_router(data_router, prefix="/api", tags=["Data Management"])
    logger.info("Data router registered successfully!")
except Exception as e:
    logger.error(f"Failed to register data router: {e}")

logger.info(f"App total routes after data router: {len(app.routes)}")
data_routes_found = []
for route in app.routes:
    if hasattr(route, 'path') and ('data' in route.path or 'upload' in route.path):
        data_routes_found.append(route.path)
        logger.info(f"  Data route found: {route.path}")

logger.info(f"Total data routes found: {len(data_routes_found)}")
logger.info("Registering sync routes...")
app.include_router(sync_router, prefix="/api/sync", tags=["Sync"])
logger.info("All routes registered")


@app.get("/")
async def root():
    """Root path"""
    return {
        "message": "Crypto Trading Strategy Service",
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/health")
async def health_check():
    """Global health check"""
    return {"status": "healthy", "message": "Service is running normally"}


if __name__ == "__main__":
    logger.info("Starting strategy service...")
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8001,
        reload=False,  # Disable auto-reload to ensure routes are registered correctly
        log_config=None  # Use custom logging configuration
    )
