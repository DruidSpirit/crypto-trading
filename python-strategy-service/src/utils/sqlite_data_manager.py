import sqlite3
import pandas as pd
import numpy as np
from typing import List, Dict, Optional, Tuple
from datetime import datetime, timedelta
import logging
import os
from contextlib import contextmanager

logger = logging.getLogger(__name__)


class SQLiteDataManager:
    """SQLite data manager - lightweight alternative to PostgreSQL"""

    def __init__(self, db_path: str = "crypto_trading.db"):
        self.db_path = db_path
        self._create_tables()

        # Timeframe mapping
        self.timeframe_mapping = {
            '1m': '1m',
            '5m': '5m',
            '15m': '15m',
            '30m': '30m',
            '1h': '1h',
            '4h': '4h',
            '1d': '1d',
            '3d': '3d',
            '5d': '5d',
            '1w': '1w',
            '1M': '1M'
        }

    @contextmanager
    def get_connection(self):
        """Get SQLite connection (context manager)"""
        conn = None
        try:
            conn = sqlite3.connect(self.db_path)
            conn.row_factory = sqlite3.Row  # Make results accessible by column name
            yield conn
        except Exception as e:
            if conn:
                conn.rollback()
            logger.error(f"SQLite connection error: {e}")
            raise
        finally:
            if conn:
                conn.close()

    def _create_tables(self):
        """Create database tables"""
        with self.get_connection() as conn:
            cursor = conn.cursor()

            # Create trading pairs table
            cursor.execute('''
            CREATE TABLE IF NOT EXISTS symbols (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL UNIQUE,
                base_asset TEXT NOT NULL,
                quote_asset TEXT NOT NULL,
                status TEXT DEFAULT 'TRADING',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            ''')

            # Create K-line data table
            cursor.execute('''
            CREATE TABLE IF NOT EXISTS klines (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                timeframe TEXT NOT NULL,
                open_time TIMESTAMP NOT NULL,
                close_time TIMESTAMP NOT NULL,
                open_price DECIMAL(20, 8) NOT NULL,
                high_price DECIMAL(20, 8) NOT NULL,
                low_price DECIMAL(20, 8) NOT NULL,
                close_price DECIMAL(20, 8) NOT NULL,
                volume DECIMAL(20, 8) NOT NULL,
                quote_volume DECIMAL(20, 8),
                trade_count INTEGER,
                taker_buy_volume DECIMAL(20, 8),
                taker_buy_quote_volume DECIMAL(20, 8),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                UNIQUE(symbol, timeframe, open_time)
            )
            ''')

            # Create index
            cursor.execute('''
            CREATE INDEX IF NOT EXISTS idx_klines_symbol_timeframe_time
            ON klines (symbol, timeframe, open_time DESC)
            ''')

            cursor.execute('''
            CREATE INDEX IF NOT EXISTS idx_klines_symbol_time
            ON klines (symbol, open_time DESC)
            ''')

            # Create data statistics table
            cursor.execute('''
            CREATE TABLE IF NOT EXISTS data_statistics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                timeframe TEXT NOT NULL,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP NOT NULL,
                record_count BIGINT NOT NULL,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                UNIQUE(symbol, timeframe)
            )
            ''')

            # Insert default trading pairs
            cursor.execute('''
            INSERT OR IGNORE INTO symbols (symbol, base_asset, quote_asset)
            VALUES (?, ?, ?)
            ''', ('BTCUSDT', 'BTC', 'USDT'))

            cursor.execute('''
            INSERT OR IGNORE INTO symbols (symbol, base_asset, quote_asset)
            VALUES (?, ?, ?)
            ''', ('ETHUSDT', 'ETH', 'USDT'))

            conn.commit()
            logger.info("SQLite database tables created successfully")

    def test_connection(self) -> bool:
        """Test database connection"""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT sqlite_version();")
                version = cursor.fetchone()[0]
                logger.info(f"SQLite connection successful: version {version}")
                return True
        except Exception as e:
            logger.error(f"SQLite connection failed: {e}")
            return False

    def bulk_insert_klines(self, klines_data: List[Dict], batch_size: int = 1000) -> int:
        """Bulk insert K-line data"""
        if not klines_data:
            return 0

        inserted_count = 0

        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                # Prepare bulk insert statement
                insert_sql = '''
                INSERT OR REPLACE INTO klines (
                    symbol, timeframe, open_time, close_time,
                    open_price, high_price, low_price, close_price,
                    volume, quote_volume, trade_count,
                    taker_buy_volume, taker_buy_quote_volume
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                '''

                # Process data in batches
                for i in range(0, len(klines_data), batch_size):
                    batch = klines_data[i:i + batch_size]

                    # Prepare data tuples
                    values = []
                    for kline in batch:
                        values.append((
                            kline['symbol'],
                            kline['timeframe'],
                            kline['open_time'],
                            kline['close_time'],
                            float(kline['open_price']),
                            float(kline['high_price']),
                            float(kline['low_price']),
                            float(kline['close_price']),
                            float(kline['volume']),
                            float(kline.get('quote_volume', 0)),
                            int(kline.get('trade_count', 0)),
                            float(kline.get('taker_buy_volume', 0)),
                            float(kline.get('taker_buy_quote_volume', 0))
                        ))

                    # Execute bulk insert
                    cursor.executemany(insert_sql, values)
                    inserted_count += len(batch)

                    logger.info(f"Bulk insert progress: {inserted_count}/{len(klines_data)}")

                conn.commit()
                logger.info(f"Bulk insert complete, inserted {inserted_count} K-line data records")

        except Exception as e:
            logger.error(f"Bulk insert failed: {e}")
            raise

        return inserted_count

    def get_klines(self, symbol: str, timeframe: str,
                   start_time: datetime = None, end_time: datetime = None,
                   limit: int = None) -> List[Dict]:
        """Query K-line data"""

        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                # Build query conditions
                conditions = ["symbol = ?", "timeframe = ?"]
                params = [symbol, timeframe]

                if start_time:
                    conditions.append("open_time >= ?")
                    params.append(start_time)

                if end_time:
                    conditions.append("open_time <= ?")
                    params.append(end_time)

                where_clause = " AND ".join(conditions)

                # Build complete query
                query = f'''
                SELECT symbol, timeframe, open_time, close_time,
                       open_price, high_price, low_price, close_price,
                       volume, quote_volume, trade_count,
                       taker_buy_volume, taker_buy_quote_volume
                FROM klines
                WHERE {where_clause}
                ORDER BY open_time ASC
                '''

                if limit:
                    query += f" LIMIT {limit}"

                cursor.execute(query, params)
                rows = cursor.fetchall()

                # Convert to list of dictionaries
                result = []
                for row in rows:
                    result.append({
                        'symbol': row['symbol'],
                        'timeframe': row['timeframe'],
                        'open_time': row['open_time'],
                        'close_time': row['close_time'],
                        'open_price': row['open_price'],
                        'high_price': row['high_price'],
                        'low_price': row['low_price'],
                        'close_price': row['close_price'],
                        'volume': row['volume'],
                        'quote_volume': row['quote_volume'],
                        'trade_count': row['trade_count'],
                        'taker_buy_volume': row['taker_buy_volume'],
                        'taker_buy_quote_volume': row['taker_buy_quote_volume']
                    })

                logger.info(f"Queried {len(result)} {symbol} {timeframe} K-line data records")
                return result

        except Exception as e:
            logger.error(f"Failed to query K-line data: {e}")
            return []

    def update_data_statistics(self, symbol: str, timeframe: str):
        """Update data statistics"""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                # Query statistics
                cursor.execute('''
                SELECT MIN(open_time), MAX(open_time), COUNT(*)
                FROM klines
                WHERE symbol = ? AND timeframe = ?
                ''', (symbol, timeframe))

                result = cursor.fetchone()
                if result and result[2] > 0:
                    start_time, end_time, count = result

                    # Update or insert statistics
                    cursor.execute('''
                    INSERT OR REPLACE INTO data_statistics
                    (symbol, timeframe, start_time, end_time, record_count)
                    VALUES (?, ?, ?, ?, ?)
                    ''', (symbol, timeframe, start_time, end_time, count))

                    conn.commit()
                    logger.info(f"Updated statistics: {symbol} {timeframe} - {count} records")

        except Exception as e:
            logger.error(f"Failed to update data statistics: {e}")

    def get_data_summary(self) -> Dict:
        """Get data overview"""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                cursor.execute('''
                SELECT symbol, timeframe, start_time, end_time, record_count, last_updated
                FROM data_statistics
                ORDER BY symbol, timeframe
                ''')

                rows = cursor.fetchall()
                summary = {}

                for row in rows:
                    symbol = row['symbol']
                    timeframe = row['timeframe']

                    if symbol not in summary:
                        summary[symbol] = {}

                    summary[symbol][timeframe] = {
                        'start_time': row['start_time'],
                        'end_time': row['end_time'],
                        'record_count': row['record_count'],
                        'last_updated': row['last_updated']
                    }

                return summary

        except Exception as e:
            logger.error(f"Failed to get data overview: {e}")
            return {}

    def cleanup_duplicate_data(self, symbol: str, timeframe: str) -> int:
        """Clean up duplicate data"""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                # Delete duplicate data, keep the latest
                cursor.execute('''
                DELETE FROM klines
                WHERE id NOT IN (
                    SELECT MIN(id)
                    FROM klines
                    WHERE symbol = ? AND timeframe = ?
                    GROUP BY symbol, timeframe, open_time
                ) AND symbol = ? AND timeframe = ?
                ''', (symbol, timeframe, symbol, timeframe))

                deleted_count = cursor.rowcount
                conn.commit()

                if deleted_count > 0:
                    logger.info(f"Cleaned up duplicate data: {symbol} {timeframe} - deleted {deleted_count} records")

                return deleted_count

        except Exception as e:
            logger.error(f"Failed to clean up duplicate data: {e}")
            return 0

    def get_database_info(self) -> Dict:
        """Get database information"""
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()

                # Get table information
                cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
                tables = [row[0] for row in cursor.fetchall()]

                # Get record counts for each table
                table_stats = {}
                for table in tables:
                    cursor.execute(f"SELECT COUNT(*) FROM {table};")
                    count = cursor.fetchone()[0]
                    table_stats[table] = count

                # Get database file size
                db_size = os.path.getsize(self.db_path) if os.path.exists(self.db_path) else 0

                return {
                    'database_type': 'SQLite',
                    'database_path': self.db_path,
                    'database_size_mb': db_size / (1024 * 1024),
                    'tables': tables,
                    'table_stats': table_stats
                }

        except Exception as e:
            logger.error(f"Failed to get database information: {e}")
            return {}
