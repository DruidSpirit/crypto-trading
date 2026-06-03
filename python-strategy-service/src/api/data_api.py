from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
import pandas as pd
import logging
import io
from datetime import datetime
import sqlite3
from typing import Optional
import os
from src.services.unified_data_service import unified_data_service

logger = logging.getLogger(__name__)
router = APIRouter()

# Database path
DATABASE_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "crypto_trading.db")


def get_db_connection():
    """Get database connection"""
    return sqlite3.connect(DATABASE_PATH)


def validate_csv_data(df: pd.DataFrame) -> dict:
    """Validate CSV data format"""
    required_columns = ['timestamp', 'open', 'high', 'low', 'close', 'volume']

    # Check for required columns
    missing_columns = []
    for col in required_columns:
        if col not in df.columns:
            # Try common column name variants
            alternatives = {
                'timestamp': ['time', 'datetime', 'date'],
                'open': ['open_price', 'o'],
                'high': ['high_price', 'h'],
                'low': ['low_price', 'l'],
                'close': ['close_price', 'c'],
                'volume': ['vol', 'v']
            }

            found = False
            if col in alternatives:
                for alt in alternatives[col]:
                    if alt in df.columns:
                        df.rename(columns={alt: col}, inplace=True)
                        found = True
                        break

            if not found:
                missing_columns.append(col)

    if missing_columns:
        return {
            "valid": False,
            "error": f"Missing required columns: {missing_columns}",
            "columns": list(df.columns)
        }

    # Validate data types
    try:
        # Convert timestamps
        if df['timestamp'].dtype == 'object':
            # Try to parse time strings
            df['timestamp'] = pd.to_datetime(df['timestamp']).astype(int) // 10**6  # Convert to milliseconds
        elif df['timestamp'].dtype in ['int64', 'float64']:
            # Already numeric, check if unit conversion needed
            if df['timestamp'].iloc[0] < 10**10:  # If seconds-level timestamp, convert to milliseconds
                df['timestamp'] = df['timestamp'] * 1000

        # Convert price and volume to numeric
        for col in ['open', 'high', 'low', 'close', 'volume']:
            df[col] = pd.to_numeric(df[col], errors='coerce')

        # Check for invalid data
        if df.isnull().any().any():
            invalid_rows = df.isnull().any(axis=1).sum()
            logger.warning(f"Found {invalid_rows} invalid data rows, will be filtered")
            df.dropna(inplace=True)

        if len(df) == 0:
            return {
                "valid": False,
                "error": "No valid data rows"
            }

        return {
            "valid": True,
            "data": df,
            "rows": len(df),
            "start_time": int(df['timestamp'].min()),
            "end_time": int(df['timestamp'].max())
        }

    except Exception as e:
        return {
            "valid": False,
            "error": f"Data format error: {str(e)}"
        }


def save_kline_data_to_db(df: pd.DataFrame, symbol: str, exchange: str, time_interval: str):
    """Save K-line data to database"""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        # Check if table exists, create if not
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS kline_data (
                id TEXT PRIMARY KEY,
                symbol TEXT NOT NULL,
                exchange TEXT NOT NULL,
                time_interval TEXT NOT NULL,
                open_time INTEGER NOT NULL,
                close_time INTEGER NOT NULL,
                open_price DECIMAL(20,8) NOT NULL,
                high_price DECIMAL(20,8) NOT NULL,
                low_price DECIMAL(20,8) NOT NULL,
                close_price DECIMAL(20,8) NOT NULL,
                volume DECIMAL(20,8) NOT NULL,
                quote_asset_volume DECIMAL(20,8),
                number_of_trades INTEGER,
                taker_buy_base_asset_volume DECIMAL(20,8),
                taker_buy_quote_asset_volume DECIMAL(20,8),
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
        """)

        # Create index
        cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_symbol_interval_time
            ON kline_data(symbol, time_interval, open_time)
        """)

        cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_symbol_time
            ON kline_data(symbol, open_time)
        """)

        # Prepare data for insertion
        current_time = datetime.now().isoformat()
        inserted_count = 0
        skipped_count = 0

        for _, row in df.iterrows():
            # Generate unique ID
            record_id = f"{symbol}_{exchange}_{time_interval}_{int(row['timestamp'])}"

            # Check if record already exists
            cursor.execute("""
                SELECT id FROM kline_data
                WHERE symbol = ? AND exchange = ? AND open_time = ?
            """, (symbol, exchange, int(row['timestamp'])))

            if cursor.fetchone():
                skipped_count += 1
                continue

            # Calculate close_time (assume K-line duration based on time_interval)
            interval_ms = {
                '1m': 60 * 1000,
                '5m': 5 * 60 * 1000,
                '15m': 15 * 60 * 1000,
                '30m': 30 * 60 * 1000,
                '1h': 60 * 60 * 1000,
                '4h': 4 * 60 * 60 * 1000,
                '1d': 24 * 60 * 60 * 1000
            }.get(time_interval, 60 * 1000)

            close_time = int(row['timestamp']) + interval_ms - 1

            # Insert data
            cursor.execute("""
                INSERT INTO kline_data (
                    id, symbol, exchange, time_interval, open_time, close_time,
                    open_price, high_price, low_price, close_price, volume,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                record_id, symbol, exchange, time_interval, int(row['timestamp']), close_time,
                float(row['open']), float(row['high']), float(row['low']),
                float(row['close']), float(row['volume']),
                current_time, current_time
            ))

            inserted_count += 1

        conn.commit()
        conn.close()

        logger.info(f"Data save complete: inserted {inserted_count}, skipped {skipped_count} duplicate records")

        return {
            "success": True,
            "inserted": inserted_count,
            "skipped": skipped_count,
            "total": len(df)
        }

    except Exception as e:
        logger.error(f"Failed to save data to database: {str(e)}")
        return {
            "success": False,
            "error": str(e)
        }


@router.post("/upload-kline-data")
async def upload_kline_data(
    file: UploadFile = File(...),
    symbol: str = Form(...),
    exchange: str = Form(...),
    timeInterval: str = Form(...),
    description: Optional[str] = Form(None)
):
    """
    Upload K-line data CSV file
    """
    try:
        logger.info(f"Received data upload request: {file.filename}, symbol={symbol}, exchange={exchange}, timeInterval={timeInterval}")

        # Validate file format
        if not file.filename.endswith('.csv'):
            return JSONResponse(
                content={"success": False, "message": "Only CSV format files are supported"},
                status_code=400
            )

        # Read file content
        content = await file.read()
        df = pd.read_csv(io.StringIO(content.decode('utf-8')))

        logger.info(f"CSV file read successfully, {len(df)} rows total")
        logger.debug(f"CSV columns: {list(df.columns)}")

        # Validate data format
        validation_result = validate_csv_data(df)
        if not validation_result["valid"]:
            return JSONResponse(
                content={
                    "success": False,
                    "message": validation_result["error"],
                    "columns": validation_result.get("columns", [])
                },
                status_code=400
            )

        validated_df = validation_result["data"]

        # Save to database (using unified data service)
        save_result = unified_data_service.save_kline_data(validated_df, symbol, exchange, timeInterval)

        if save_result["success"]:
            return JSONResponse(
                content={
                    "success": True,
                    "message": f"Data uploaded successfully! Inserted {save_result['inserted']} new records, skipped {save_result['skipped']} duplicate records",
                    "data": {
                        "symbol": symbol,
                        "exchange": exchange,
                        "timeInterval": timeInterval,
                        "totalRows": save_result["total"],
                        "insertedRows": save_result["inserted"],
                        "skippedRows": save_result["skipped"],
                        "startTime": validation_result["start_time"],
                        "endTime": validation_result["end_time"]
                    }
                }
            )
        else:
            return JSONResponse(
                content={
                    "success": False,
                    "message": f"Data save failed: {save_result['error']}"
                },
                status_code=500
            )

    except Exception as e:
        logger.error(f"Failed to upload K-line data: {str(e)}", exc_info=True)
        return JSONResponse(
            content={
                "success": False,
                "message": f"Error processing file: {str(e)}"
            },
            status_code=500
        )


@router.get("/data-info")
async def get_data_info():
    """
    Get data information from database

    Returns:
        Database data file information
    """
    try:
        # Get data info from database (using unified data service)
        data_info = unified_data_service.get_available_data_info()

        return {
            "success": True,
            "message": "Data information retrieved successfully",
            "data": {
                "data_source": "database",
                "datasets_count": len(data_info),
                "datasets": data_info
            }
        }

    except Exception as e:
        logger.error(f"Failed to get data information: {str(e)}", exc_info=True)
        return {
            "success": False,
            "message": f"Failed to get data information: {str(e)}"
        }


@router.delete("/delete-kline-data/{symbol}/{exchange}")
async def delete_kline_data(symbol: str, exchange: str, time_interval: str = None):
    """
    Delete K-line data from database

    Args:
        symbol: Trading pair name
        exchange: Exchange name
        time_interval: Time interval (optional)

    Returns:
        Deletion result
    """
    try:
        logger.info(f"Received delete data request: {symbol} {exchange} {time_interval}")

        # Use unified data service to delete data
        delete_result = unified_data_service.delete_kline_data(
            symbol=symbol,
            exchange=exchange,
            time_interval=time_interval
        )

        if delete_result['success']:
            return {
                "success": True,
                "message": f"{delete_result['message']} These data have been removed from backtest data source.",
                "deleted_count": delete_result['deleted_count']
            }
        else:
            return {
                "success": False,
                "message": f"Failed to delete data: {delete_result['error']}"
            }

    except Exception as e:
        logger.error(f"Failed to delete data: {str(e)}", exc_info=True)
        return {
            "success": False,
            "message": f"Failed to delete data: {str(e)}"
        }


@router.get("/data-health")
async def health_check():
    """Data API health check"""
    try:
        # Check database connection
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM kline_data")
        total_records = cursor.fetchone()[0]
        conn.close()

        return {
            "status": "healthy",
            "database": "connected",
            "total_records": total_records
        }
    except Exception as e:
        return {
            "status": "error",
            "error": str(e)
        }
