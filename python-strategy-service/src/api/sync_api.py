from fastapi import APIRouter, HTTPException, BackgroundTasks
from pydantic import BaseModel
from typing import Dict, List, Optional
from src.services.strategy_sync_service import StrategySyncService
from src.database.connection import test_connection, create_tables
import logging

logger = logging.getLogger(__name__)

router = APIRouter()

# Strategy sync service instance
sync_service = StrategySyncService()


class SyncResponse(BaseModel):
    success: bool
    message: str
    strategies_found: Optional[int] = None
    strategies_synced: Optional[int] = None
    created_count: Optional[int] = None
    updated_count: Optional[int] = None
    orphaned_count: Optional[int] = None
    current_strategies: Optional[List[str]] = None
    error: Optional[str] = None


class StrategyInfo(BaseModel):
    id: int
    filename: str
    displayName: str
    description: Optional[str]
    status: str
    uploadTime: Optional[str]
    lastUpdateTime: Optional[str]


@router.post("/sync", response_model=SyncResponse)
async def sync_strategies():
    """Sync Python strategies to MySQL database"""
    try:
        logger.info("Received strategy sync request")

        # Test database connection
        if not test_connection():
            raise HTTPException(status_code=500, detail="Database connection failed")

        # Ensure tables exist
        create_tables()

        # Execute sync
        result = sync_service.sync_strategies_to_database()

        if result.get("success"):
            return SyncResponse(**result)
        else:
            raise HTTPException(status_code=500, detail=result.get("message", "Sync failed"))

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Strategy sync API failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")


@router.get("/strategies", response_model=List[StrategyInfo])
async def get_database_strategies():
    """Get strategy list from database"""
    try:
        strategies = sync_service.get_database_strategies()
        return [StrategyInfo(**strategy) for strategy in strategies]
    except Exception as e:
        logger.error(f"Failed to get database strategy list: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get strategy list: {str(e)}")


@router.delete("/strategies/{strategy_id}")
async def delete_strategy(strategy_id: int):
    """Delete strategy from database"""
    try:
        success = sync_service.delete_strategy_from_database(strategy_id)
        if success:
            return {"success": True, "message": "Strategy deleted successfully"}
        else:
            raise HTTPException(status_code=404, detail="Strategy not found")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to delete strategy: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to delete strategy: {str(e)}")


@router.post("/sync-on-startup")
async def sync_on_startup(background_tasks: BackgroundTasks):
    """Auto-sync strategies on startup (background task)"""
    try:
        logger.info("Startup strategy sync request")

        def sync_task():
            try:
                if test_connection():
                    create_tables()
                    result = sync_service.sync_strategies_to_database()
                    logger.info(f"Startup strategy sync complete: {result}")
                else:
                    logger.error("Startup strategy sync failed: Database connection failed")
            except Exception as e:
                logger.error(f"Startup strategy sync failed: {e}", exc_info=True)

        background_tasks.add_task(sync_task)

        return {
            "success": True,
            "message": "Strategy sync task added to background queue"
        }

    except Exception as e:
        logger.error(f"Startup strategy sync API failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Startup sync failed: {str(e)}")


@router.get("/health")
async def health_check():
    """Health check - check database connection"""
    try:
        db_ok = test_connection()
        return {
            "status": "healthy" if db_ok else "unhealthy",
            "database": "connected" if db_ok else "disconnected"
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "unhealthy",
            "database": "error",
            "error": str(e)
        }
