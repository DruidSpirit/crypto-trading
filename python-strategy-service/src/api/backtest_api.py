from fastapi import APIRouter, HTTPException, Request
from src.models.backtest_dto import BacktestRequestDTO, BacktestResponseDTO
from src.utils.backtest_engine import BacktestEngine
from src.utils.data_downloader import DataDownloader
from src.utils.multi_timeframe_data_manager import MultiTimeframeDataManager
from src.services.unified_data_service import unified_data_service
import logging
import json

logger = logging.getLogger(__name__)

router = APIRouter()

# Global instance
data_downloader = DataDownloader()


@router.post("/run", response_model=BacktestResponseDTO)
async def run_backtest(request: BacktestRequestDTO):
    """
    Run backtest

    Args:
        request: Backtest request parameters

    Returns:
        Backtest result
    """
    try:
        logger.info(f"Starting backtest: {request.strategy_name}, {request.symbol}, {request.start_date} to {request.end_date}")

        # Get historical data from database (not from CSV files)
        logger.info(f"Fetching data from database: symbol={request.symbol}, interval={request.timeframe}")
        data = unified_data_service.get_kline_data(
            symbol=request.symbol,
            interval=request.timeframe,
            start_date=request.start_date,
            end_date=request.end_date
        )
        logger.info(f"Data retrieval complete, data shape: {data.shape}, data empty: {data.empty}")

        if data.empty:
            logger.warning(f"No data found in database: {request.symbol} {request.timeframe}")
            return BacktestResponseDTO(
                success=False,
                message=f"No historical data found for {request.symbol} in the database. Please upload relevant data in data management first."
            )

        logger.info(f"Retrieved {len(data)} historical data records from database")

        # Create backtest engine
        engine = BacktestEngine(initial_balance=request.initial_balance)

        # Run backtest
        result = engine.run_backtest(
            symbol=request.symbol,
            data=data,
            strategy_name=request.strategy_name,
            strategy_params=request.strategy_params
        )

        logger.info(f"Backtest complete, total return: {result.total_return:.2f}")

        return BacktestResponseDTO(
            success=True,
            message="Backtest executed successfully",
            data=result
        )

    except Exception as e:
        logger.error(f"Backtest execution failed: {str(e)}", exc_info=True)
        return BacktestResponseDTO(
            success=False,
            message=f"Backtest execution failed: {str(e)}"
        )


@router.post("/run-enhanced", response_model=BacktestResponseDTO)
async def run_enhanced_backtest(request: Request):
    """
    Run enhanced backtest - uses minute-level data and multiple timeframes

    Args:
        request: Enhanced backtest request parameters

    Returns:
        Backtest result
    """
    try:
        data = await request.json()
        logger.info(f"Received enhanced backtest request: {json.dumps(data, indent=2)}")

        symbol = data.get('symbol', 'BTCUSDT')
        strategy_name = data.get('strategy_name', 'ElderSwingStrategy')
        days_back = data.get('days_back', 90)
        min_data_points = data.get('min_data_points', 2000)
        initial_balance = data.get('initial_balance', 10000.0)
        strategy_params = data.get('strategy_params', {})

        logger.info(f"Starting enhanced backtest: {strategy_name}, {symbol}, looking back {days_back} days")

        # Get minute-level data from database
        from datetime import datetime, timedelta
        end_date = datetime.now().strftime('%Y-%m-%d')
        start_date = (datetime.now() - timedelta(days=days_back)).strftime('%Y-%m-%d')

        minute_data = unified_data_service.get_kline_data(
            symbol=symbol,
            interval='1m',
            start_date=start_date,
            end_date=end_date
        )

        if minute_data.empty:
            return BacktestResponseDTO(
                success=False,
                message=f"No minute-level historical data found for {symbol} in the database. Please upload relevant data in data management first."
            )

        # Create enhanced backtest engine
        engine = BacktestEngine(initial_balance=initial_balance)

        # Run enhanced backtest (using database data)
        result = engine.run_backtest(
            symbol=symbol,
            data=minute_data,
            strategy_name=strategy_name,
            strategy_params=strategy_params
        )

        logger.info(f"Enhanced backtest complete, total return: {result.total_return:.2f}")

        return BacktestResponseDTO(
            success=True,
            message=f"Enhanced backtest executed successfully, using {len(minute_data)} minute-level data records from database",
            data=result
        )

    except Exception as e:
        logger.error(f"Enhanced backtest execution failed: {str(e)}", exc_info=True)
        return BacktestResponseDTO(
            success=False,
            message=f"Enhanced backtest execution failed: {str(e)}"
        )


@router.post("/download-minute-data")
async def download_minute_data(request: Request):
    """
    Download minute-level historical data - large data volume

    Args:
        request: Request containing symbol, days_back

    Returns:
        Download result
    """
    try:
        data = await request.json()
        logger.info(f"Received minute-level data download request: {json.dumps(data, indent=2)}")

        symbol = data.get('symbol', 'BTCUSDT')
        days_back = data.get('days_back', 30)
        force_download = data.get('force_download', False)

        # Create data manager
        data_manager = MultiTimeframeDataManager()

        # Download minute-level data
        minute_data = data_manager.download_minute_data(
            symbol=symbol,
            days_back=days_back,
            force_download=force_download
        )

        if minute_data.empty:
            return {
                "success": False,
                "message": f"Unable to fetch minute-level data for {symbol}"
            }

        # Statistics
        stats = {
            "symbol": symbol,
            "days_back": days_back,
            "total_records": len(minute_data),
            "start_date": minute_data.index[0].strftime('%Y-%m-%d %H:%M'),
            "end_date": minute_data.index[-1].strftime('%Y-%m-%d %H:%M'),
            "price_range": {
                "min": float(minute_data['low'].min()),
                "max": float(minute_data['high'].max()),
                "latest": float(minute_data['close'].iloc[-1])
            }
        }

        logger.info(f"Minute-level data download complete: {stats['total_records']} records")

        return {
            "success": True,
            "message": f"Successfully downloaded {stats['total_records']} minute-level data records",
            "data": stats
        }

    except Exception as e:
        logger.error(f"Minute-level data download failed: {str(e)}", exc_info=True)
        return {
            "success": False,
            "message": f"Minute-level data download failed: {str(e)}"
        }


@router.post("/download-data")
async def download_data(request: Request):
    """
    Download historical data

    Args:
        request: Request containing symbol, start_date, end_date, timeframe

    Returns:
        Download result
    """
    try:
        data = await request.json()
        logger.info(f"Received data download request: {json.dumps(data, indent=2)}")

        symbol = data.get('symbol', 'BTCUSDT')
        start_date = data.get('start_date')
        end_date = data.get('end_date')
        timeframe = data.get('timeframe', '1h')

        # Download data
        df = data_downloader.download_binance_data(
            symbol=symbol,
            interval=timeframe,
            start_date=start_date,
            end_date=end_date
        )

        if df.empty:
            return {
                "success": False,
                "message": f"Unable to download data for {symbol}"
            }

        # Save locally
        filepath = data_downloader.save_data(df, symbol, timeframe)

        return {
            "success": True,
            "message": f"Successfully downloaded and saved {symbol} data",
            "data": {
                "symbol": symbol,
                "timeframe": timeframe,
                "records_count": len(df),
                "start_date": df.index[0].strftime('%Y-%m-%d %H:%M:%S'),
                "end_date": df.index[-1].strftime('%Y-%m-%d %H:%M:%S'),
                "filepath": filepath
            }
        }

    except Exception as e:
        logger.error(f"Data download failed: {str(e)}", exc_info=True)
        return {
            "success": False,
            "message": f"Data download failed: {str(e)}"
        }


@router.post("/batch-download")
async def batch_download_data(request: Request):
    """
    Batch download historical data for multiple trading pairs

    Args:
        request: Request containing symbols list and other parameters

    Returns:
        Batch download result
    """
    try:
        data = await request.json()
        logger.info(f"Received batch data download request: {json.dumps(data, indent=2)}")

        symbols = data.get('symbols', ['BTCUSDT', 'ETHUSDT'])
        start_date = data.get('start_date')
        end_date = data.get('end_date')
        timeframe = data.get('timeframe', '1h')

        results = []

        for symbol in symbols:
            try:
                logger.info(f"Starting download for {symbol} data")

                # Download data
                df = data_downloader.download_binance_data(
                    symbol=symbol,
                    interval=timeframe,
                    start_date=start_date,
                    end_date=end_date
                )

                if not df.empty:
                    # Save locally
                    filepath = data_downloader.save_data(df, symbol, timeframe)

                    results.append({
                        "symbol": symbol,
                        "success": True,
                        "records_count": len(df),
                        "start_date": df.index[0].strftime('%Y-%m-%d %H:%M:%S'),
                        "end_date": df.index[-1].strftime('%Y-%m-%d %H:%M:%S'),
                        "filepath": filepath
                    })
                    logger.info(f"{symbol} data download complete, {len(df)} records total")
                else:
                    results.append({
                        "symbol": symbol,
                        "success": False,
                        "error": f"Unable to download data for {symbol}"
                    })

            except Exception as e:
                logger.error(f"Download {symbol} failed: {str(e)}")
                results.append({
                    "symbol": symbol,
                    "success": False,
                    "error": str(e)
                })

        success_count = sum(1 for r in results if r.get('success', False))

        return {
            "success": True,
            "message": f"Batch download complete, succeeded: {success_count}/{len(symbols)}",
            "data": {
                "total_symbols": len(symbols),
                "success_count": success_count,
                "results": results
            }
        }

    except Exception as e:
        logger.error(f"Batch data download failed: {str(e)}", exc_info=True)
        return {
            "success": False,
            "message": f"Batch data download failed: {str(e)}"
        }



@router.get("/progress/{task_id}")
async def get_backtest_progress(task_id: str):
    """
    Get backtest progress
    """
    return {
        "success": True,
        "status": True,
        "statusCode": 2004,
        "message": "Backtest completed",
        "data": {
            "task_id": task_id,
            "id": task_id,
            "task_name": "Backtest task",
            "progress_pct": 100.0,
            "status": "completed",
            "message": "Backtest complete",
            "current_operation": "Backtest complete",
            "start_time": "2024-01-01T00:00:00",
            "end_time": "2024-01-31T23:59:59",
            "error_message": None,
            "has_results": True,
            "backtest_results": {
                "total_return": 0.0,
                "total_return_pct": 0.0,
                "max_drawdown": 0.0,
                "sharpe_ratio": 0.0,
                "total_trades": 0,
                "win_rate": 0.0
            },
            "symbol": "BTCUSDT",
            "endDate": "2024-01-31",
            "startDate": "2024-01-01",
            "initialBalance": 10000.0,
            "strategyName": "ElderSwingStrategy"
        }
    }


@router.get("/local-data-info")
async def get_data_info():
    """
    Get local data information

    Returns:
        Local data file information
    """
    try:
        import os
        data_files = []

        if os.path.exists(data_downloader.data_dir):
            for filename in os.listdir(data_downloader.data_dir):
                if filename.endswith('.csv'):
                    filepath = os.path.join(data_downloader.data_dir, filename)
                    stat = os.stat(filepath)

                    # Try to read file for more information
                    try:
                        df = data_downloader.load_data(filename.replace('.csv', '').replace('_1h', ''), '1h')
                        if df is not None:
                            data_files.append({
                                "filename": filename,
                                "size": stat.st_size,
                                "modified": stat.st_mtime,
                                "records_count": len(df),
                                "start_date": df.index[0].strftime('%Y-%m-%d %H:%M:%S'),
                                "end_date": df.index[-1].strftime('%Y-%m-%d %H:%M:%S')
                            })
                    except Exception as e:
                        data_files.append({
                            "filename": filename,
                            "size": stat.st_size,
                            "modified": stat.st_mtime,
                            "error": str(e)
                        })

        return {
            "success": True,
            "message": "Data information retrieved successfully",
            "data": {
                "data_dir": data_downloader.data_dir,
                "files_count": len(data_files),
                "files": data_files
            }
        }

    except Exception as e:
        logger.error(f"Failed to get data information: {str(e)}", exc_info=True)
        return {
            "success": False,
            "message": f"Failed to get data information: {str(e)}"
        }
