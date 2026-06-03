import pandas as pd
import sqlite3
import os
import logging
from datetime import datetime, timedelta
from typing import Optional, List, Tuple
import numpy as np

logger = logging.getLogger(__name__)


class UnifiedDataService:
    """
    Unified data access service
    - Data uploaded via data management is stored in the database
    - Backtesting reads data from the database
    - Supports CRUD operations on data
    """

    def __init__(self, db_path: str = None):
        """
        Initialize data service

        Args:
            db_path: Database file path
        """
        if db_path is None:
            # Default to project root directory database file
            project_root = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
            self.db_path = os.path.join(project_root, "crypto_trading.db")
        else:
            self.db_path = db_path

        self.ensure_database_exists()

    def get_db_connection(self):
        """Get database connection"""
        return sqlite3.connect(self.db_path)

    def ensure_database_exists(self):
        """Ensure database and tables exist"""
        try:
            conn = self.get_db_connection()
            cursor = conn.cursor()

            # Create kline_data table
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
                    updated_at TEXT NOT NULL,
                    UNIQUE(symbol, exchange, time_interval, open_time)
                )
            """)

            # Create index
            cursor.execute("""
                CREATE INDEX IF NOT EXISTS idx_symbol_interval_time
                ON kline_data(symbol, time_interval, open_time)
            """)

            cursor.execute("""
                CREATE INDEX IF NOT EXISTS idx_symbol_exchange_time
                ON kline_data(symbol, exchange, open_time)
            """)

            conn.commit()
            conn.close()
            logger.info(f"Database tables created successfully: {self.db_path}")

        except Exception as e:
            logger.error(f"Failed to create database tables: {str(e)}")
            raise

    def get_kline_data(self, symbol: str, interval: str = "1h",
                      start_date: str = None, end_date: str = None,
                      exchange: str = None) -> pd.DataFrame:
        """
        Get K-line data - read from database

        Args:
            symbol: Trading pair, e.g. BTCUSDT
            interval: Time interval, e.g. 1h, 4h, 1d
            start_date: Start date, format: YYYY-MM-DD
            end_date: End date, format: YYYY-MM-DD
            exchange: Exchange (optional)

        Returns:
            DataFrame containing OHLCV data
        """
        try:
            conn = self.get_db_connection()

            # Build query conditions
            conditions = ["symbol = ?", "time_interval = ?"]
            params = [symbol, interval]

            if exchange:
                conditions.append("exchange = ?")
                params.append(exchange)

            if start_date:
                start_ts = int(datetime.strptime(start_date, "%Y-%m-%d").timestamp() * 1000)
                conditions.append("open_time >= ?")
                params.append(start_ts)

            if end_date:
                end_ts = int(datetime.strptime(end_date, "%Y-%m-%d").timestamp() * 1000)
                conditions.append("open_time <= ?")
                params.append(end_ts)

            where_clause = " AND ".join(conditions)

            query = f"""
                SELECT open_time, open_price, high_price, low_price, close_price, volume
                FROM kline_data
                WHERE {where_clause}
                ORDER BY open_time ASC
            """

            df = pd.read_sql_query(query, conn, params=params)
            conn.close()

            if df.empty:
                logger.warning(f"No data found: {symbol} {interval} {start_date} to {end_date}")
                return pd.DataFrame()

            # Convert data format
            df['timestamp'] = pd.to_datetime(df['open_time'], unit='ms')
            df.set_index('timestamp', inplace=True)

            # Rename columns to match original format
            df.rename(columns={
                'open_price': 'open',
                'high_price': 'high',
                'low_price': 'low',
                'close_price': 'close'
            }, inplace=True)

            # Keep only needed columns
            df = df[['open', 'high', 'low', 'close', 'volume']]

            # Ensure correct data types
            for col in ['open', 'high', 'low', 'close', 'volume']:
                df[col] = pd.to_numeric(df[col], errors='coerce')

            logger.info(f"Data loaded from database: {symbol} {interval}, {len(df)} records total")
            return df

        except Exception as e:
            logger.error(f"Failed to get data from database: {str(e)}")
            return pd.DataFrame()

    def save_kline_data(self, df: pd.DataFrame, symbol: str, exchange: str,
                       time_interval: str) -> dict:
        """
        Save K-line data to database

        Args:
            df: K-line data DataFrame, must contain timestamp, open, high, low, close, volume columns
            symbol: Trading pair name
            exchange: Exchange name
            time_interval: Time interval

        Returns:
            Save result
        """
        try:
            conn = self.get_db_connection()
            cursor = conn.cursor()

            current_time = datetime.now().isoformat()
            inserted_count = 0
            updated_count = 0
            skipped_count = 0

            for index, row in df.iterrows():
                # Get timestamp
                if isinstance(index, pd.Timestamp):
                    timestamp_ms = int(index.timestamp() * 1000)
                elif 'timestamp' in row:
                    if isinstance(row['timestamp'], pd.Timestamp):
                        timestamp_ms = int(row['timestamp'].timestamp() * 1000)
                    else:
                        timestamp_ms = int(row['timestamp'])
                else:
                    logger.error(f"Unable to get timestamp: {index}")
                    continue

                # Generate unique ID
                record_id = f"{symbol}_{exchange}_{time_interval}_{timestamp_ms}"

                # Calculate close_time
                interval_ms = {
                    '1m': 60 * 1000,
                    '5m': 5 * 60 * 1000,
                    '15m': 15 * 60 * 1000,
                    '30m': 30 * 60 * 1000,
                    '1h': 60 * 60 * 1000,
                    '4h': 4 * 60 * 60 * 1000,
                    '1d': 24 * 60 * 60 * 1000
                }.get(time_interval, 60 * 1000)

                close_time = timestamp_ms + interval_ms - 1

                # Check if record already exists
                cursor.execute("""
                    SELECT id FROM kline_data
                    WHERE symbol = ? AND exchange = ? AND time_interval = ? AND open_time = ?
                """, (symbol, exchange, time_interval, timestamp_ms))

                existing = cursor.fetchone()

                if existing:
                    # Update existing record
                    cursor.execute("""
                        UPDATE kline_data SET
                            close_time = ?, open_price = ?, high_price = ?, low_price = ?,
                            close_price = ?, volume = ?, updated_at = ?
                        WHERE symbol = ? AND exchange = ? AND time_interval = ? AND open_time = ?
                    """, (
                        close_time, float(row['open']), float(row['high']),
                        float(row['low']), float(row['close']), float(row['volume']),
                        current_time, symbol, exchange, time_interval, timestamp_ms
                    ))
                    updated_count += 1
                else:
                    # Insert new record
                    cursor.execute("""
                        INSERT INTO kline_data (
                            id, symbol, exchange, time_interval, open_time, close_time,
                            open_price, high_price, low_price, close_price, volume,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (
                        record_id, symbol, exchange, time_interval, timestamp_ms, close_time,
                        float(row['open']), float(row['high']), float(row['low']),
                        float(row['close']), float(row['volume']),
                        current_time, current_time
                    ))
                    inserted_count += 1

            conn.commit()
            conn.close()

            result = {
                "success": True,
                "inserted": inserted_count,
                "updated": updated_count,
                "skipped": skipped_count,
                "total": len(df)
            }

            logger.info(f"Data save complete: {result}")
            return result

        except Exception as e:
            logger.error(f"Failed to save data to database: {str(e)}")
            return {
                "success": False,
                "error": str(e),
                "inserted": 0,
                "updated": 0,
                "skipped": 0,
                "total": len(df)
            }

    def delete_kline_data(self, symbol: str, exchange: str = None,
                         time_interval: str = None) -> dict:
        """
        Delete K-line data

        Args:
            symbol: Trading pair name
            exchange: Exchange name (optional)
            time_interval: Time interval (optional)

        Returns:
            Deletion result
        """
        try:
            conn = self.get_db_connection()
            cursor = conn.cursor()

            # Build delete conditions
            conditions = ["symbol = ?"]
            params = [symbol]

            if exchange:
                conditions.append("exchange = ?")
                params.append(exchange)

            if time_interval:
                conditions.append("time_interval = ?")
                params.append(time_interval)

            where_clause = " AND ".join(conditions)

            # Count records to be deleted
            count_query = f"SELECT COUNT(*) FROM kline_data WHERE {where_clause}"
            cursor.execute(count_query, params)
            delete_count = cursor.fetchone()[0]

            if delete_count == 0:
                return {
                    "success": True,
                    "deleted_count": 0,
                    "message": "No matching records found"
                }

            # Execute deletion
            delete_query = f"DELETE FROM kline_data WHERE {where_clause}"
            cursor.execute(delete_query, params)

            conn.commit()
            conn.close()

            result = {
                "success": True,
                "deleted_count": delete_count,
                "message": f"Successfully deleted {delete_count} records"
            }

            logger.info(f"Data deletion complete: {result}")
            return result

        except Exception as e:
            logger.error(f"Failed to delete data: {str(e)}")
            return {
                "success": False,
                "error": str(e),
                "deleted_count": 0
            }

    def get_available_data_info(self) -> List[dict]:
        """
        Get all available data information

        Returns:
            Data information list
        """
        try:
            conn = self.get_db_connection()

            query = """
                SELECT
                    symbol,
                    exchange,
                    time_interval,
                    MIN(open_time) as start_time,
                    MAX(open_time) as end_time,
                    COUNT(*) as total_records
                FROM kline_data
                GROUP BY symbol, exchange, time_interval
                ORDER BY symbol, exchange, time_interval
            """

            cursor = conn.cursor()
            cursor.execute(query)
            results = cursor.fetchall()
            conn.close()

            data_info = []
            for row in results:
                symbol, exchange, time_interval, start_time, end_time, total_records = row

                data_info.append({
                    "symbol": symbol,
                    "exchange": exchange,
                    "time_interval": time_interval,
                    "start_date": datetime.fromtimestamp(start_time / 1000).strftime('%Y-%m-%d'),
                    "end_date": datetime.fromtimestamp(end_time / 1000).strftime('%Y-%m-%d'),
                    "total_records": total_records
                })

            logger.info(f"Data info retrieved successfully, {len(data_info)} datasets total")
            return data_info

        except Exception as e:
            logger.error(f"Failed to get data information: {str(e)}")
            return []

    def check_data_exists(self, symbol: str, exchange: str, time_interval: str) -> bool:
        """
        Check if specified data exists

        Args:
            symbol: Trading pair name
            exchange: Exchange name
            time_interval: Time interval

        Returns:
            Whether data exists
        """
        try:
            conn = self.get_db_connection()
            cursor = conn.cursor()

            cursor.execute("""
                SELECT COUNT(*) FROM kline_data
                WHERE symbol = ? AND exchange = ? AND time_interval = ?
            """, (symbol, exchange, time_interval))

            count = cursor.fetchone()[0]
            conn.close()

            return count > 0

        except Exception as e:
            logger.error(f"Failed to check data existence: {str(e)}")
            return False


# Create global instance
unified_data_service = UnifiedDataService()
