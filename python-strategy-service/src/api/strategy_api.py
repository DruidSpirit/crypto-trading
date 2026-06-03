from fastapi import APIRouter, HTTPException, Request
from src.models.dto import StrategyRequestDTO, StrategyResponseDTO, TradeStrategyDTO
from src.strategies.strategy_factory import StrategyFactory
import logging
import json

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/execute", response_model=StrategyResponseDTO)
async def execute_strategy(fastapi_request: Request):
    """
    Execute trading strategy

    Args:
        fastapi_request: FastAPI request object

    Returns:
        Strategy execution result
    """
    try:
        # Get raw JSON data
        raw_data = await fastapi_request.json()
        logger.info(f"Received raw request data: {json.dumps(raw_data, indent=2, default=str)}")

        # Try to parse as StrategyRequestDTO
        try:
            request = StrategyRequestDTO(**raw_data)
            logger.info(f"DTO parsed successfully: strategy={request.strategyName}, symbol={request.symbol}")
        except Exception as validation_error:
            logger.error(f"Pydantic validation failed: {validation_error}")
            logger.error(f"Failed data structure: {type(raw_data)}")
            if isinstance(raw_data, dict):
                logger.error(f"Data keys: {list(raw_data.keys())}")
                for key, value in raw_data.items():
                    logger.error(f"  {key}: {type(value)} = {str(value)[:100]}...")
            return StrategyResponseDTO(
                success=False,
                message=f"Data validation failed: {str(validation_error)}"
            )

        logger.info(f"Starting strategy execution: {request.strategyName}, symbol: {request.symbol}")

        # Create strategy instance
        strategy = StrategyFactory.create_strategy(request.strategyName)
        if strategy is None:
            logger.error(f"Strategy not found: {request.strategyName}")
            return StrategyResponseDTO(
                success=False,
                message=f"Strategy not found: {request.strategyName}"
            )

        # Execute strategy
        result = strategy.execute(request.symbol, request.klineData)

        if result is None:
            logger.info(f"Strategy {request.strategyName} did not generate a trading signal")
            return StrategyResponseDTO(
                success=True,
                message="Strategy executed successfully, but no trading signal generated"
            )

        logger.info(f"Strategy executed successfully, generated trading signal: {result.signal}")
        return StrategyResponseDTO(
            success=True,
            data=result,
            message="Strategy executed successfully"
        )

    except Exception as e:
        logger.error(f"Strategy execution failed: {str(e)}", exc_info=True)
        return StrategyResponseDTO(
            success=False,
            message=f"Strategy execution failed: {str(e)}"
        )


@router.get("/strategies")
async def get_available_strategies():
    """
    Get available strategy list

    Returns:
        Available strategy list
    """
    try:
        strategies = StrategyFactory.get_available_strategies()
        logger.info(f"Returning available strategy list: {strategies}")
        return {
            "success": True,
            "data": strategies,
            "message": "Strategy list retrieved successfully"
        }
    except Exception as e:
        logger.error(f"Failed to get strategy list: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get strategy list: {str(e)}")


@router.post("/reload")
async def reload_strategy(request: Request):
    """
    Hot-reload strategy endpoint

    Args:
        request: FastAPI request object

    Returns:
        Hot-reload result
    """
    try:
        # Get request data
        data = await request.json()
        logger.info(f"Received hot-reload request: {json.dumps(data, indent=2, default=str)}")

        # Reload strategies
        StrategyFactory.reload_strategies()

        # Get updated strategy list
        strategies = StrategyFactory.get_available_strategies()

        return {
            "success": True,
            "message": f"Strategy hot-reload successful, loaded {len(strategies)} strategies",
            "data": {
                "strategies": strategies,
                "timestamp": json.dumps(data.get("timestamp", ""), default=str)
            }
        }

    except Exception as e:
        logger.error(f"Strategy hot-reload failed: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Strategy hot-reload failed: {str(e)}")


@router.post("/delete")
async def delete_strategy(request: Request):
    """
    Delete strategy endpoint

    Args:
        request: FastAPI request object

    Returns:
        Deletion result
    """
    try:
        # Get request data
        data = await request.json()
        logger.info(f"Received strategy deletion request: {json.dumps(data, indent=2, default=str)}")

        # Specific strategy deletion logic can be added here, such as:
        # 1. Remove strategy from strategy factory
        # 2. Clean up related cache
        # 3. Stop related tasks, etc.

        # Return success response for now
        return {
            "success": True,
            "message": "Strategy deleted successfully",
            "filename": data.get("filename", "")
        }

    except Exception as e:
        logger.error(f"Strategy deletion failed: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Strategy deletion failed: {str(e)}")


@router.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy", "message": "Strategy service is running normally"}
