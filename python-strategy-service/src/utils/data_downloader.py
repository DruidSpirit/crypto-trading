import pandas as pd
import requests
import time
import os
from datetime import datetime, timedelta
from typing import Optional, List
import logging

logger = logging.getLogger(__name__)


class DataDownloader:
    """Data downloader"""

    def __init__(self, data_dir: str = "data"):
        self.data_dir = data_dir
        os.makedirs(data_dir, exist_ok=True)

    def download_binance_data(self, symbol: str, interval: str = "1h",
                             start_date: str = None, end_date: str = None) -> pd.DataFrame:
        """
        Download historical data from Binance API

        Args:
            symbol: Trading pair, e.g. BTCUSDT
            interval: Time interval, e.g. 1h, 4h, 1d
            start_date: Start date, format: YYYY-MM-DD
            end_date: End date, format: YYYY-MM-DD

        Returns:
            DataFrame containing OHLCV data
        """
        try:
            # Build Binance API URL
            base_url = "https://api.binance.com/api/v3/klines"

            # Convert timestamps
            if start_date:
                start_ts = int(datetime.strptime(start_date, "%Y-%m-%d").timestamp() * 1000)
            else:
                # Default to 4 years ago
                start_ts = int((datetime.now() - timedelta(days=4*365)).timestamp() * 1000)

            if end_date:
                end_ts = int(datetime.strptime(end_date, "%Y-%m-%d").timestamp() * 1000)
            else:
                end_ts = int(datetime.now().timestamp() * 1000)

            all_data = []
            current_start = start_ts

            # Download data in batches (max 1000 per batch)
            while current_start < end_ts:
                params = {
                    "symbol": symbol,
                    "interval": interval,
                    "startTime": current_start,
                    "endTime": end_ts,
                    "limit": 1000
                }

                logger.info(f"Downloading {symbol} data from {datetime.fromtimestamp(current_start/1000)}")

                response = requests.get(base_url, params=params)
                response.raise_for_status()

                data = response.json()
                if not data:
                    break

                all_data.extend(data)

                # Update start time for next batch
                current_start = data[-1][0] + 1

                # Avoid too frequent requests
                time.sleep(0.1)

            if not all_data:
                logger.warning(f"No data retrieved for {symbol}")
                return pd.DataFrame()

            # Convert to DataFrame
            df = pd.DataFrame(all_data, columns=[
                'timestamp', 'open', 'high', 'low', 'close', 'volume',
                'close_time', 'quote_volume', 'count', 'taker_buy_volume',
                'taker_buy_quote_volume', 'ignore'
            ])

            # Data type conversion
            df['timestamp'] = pd.to_datetime(df['timestamp'], unit='ms')
            numeric_columns = ['open', 'high', 'low', 'close', 'volume']
            for col in numeric_columns:
                df[col] = pd.to_numeric(df[col])

            # Set time index
            df.set_index('timestamp', inplace=True)

            # Keep only needed columns
            df = df[['open', 'high', 'low', 'close', 'volume']]

            logger.info(f"Successfully downloaded {symbol} data, {len(df)} records total")
            return df

        except Exception as e:
            logger.error(f"Failed to download {symbol} data: {str(e)}")
            raise

    def save_data(self, df: pd.DataFrame, symbol: str, interval: str = "1h"):
        """Save data to local file"""
        filename = f"{symbol}_{interval}.csv"
        filepath = os.path.join(self.data_dir, filename)
        df.to_csv(filepath)
        logger.info(f"Data saved to: {filepath}")
        return filepath

    def load_data(self, symbol: str, interval: str = "1h") -> Optional[pd.DataFrame]:
        """Load data from local file"""
        filename = f"{symbol}_{interval}.csv"
        filepath = os.path.join(self.data_dir, filename)

        if os.path.exists(filepath):
            df = pd.read_csv(filepath, index_col=0, parse_dates=True)
            logger.info(f"Loaded data from local file: {filepath}, {len(df)} records total")
            return df
        else:
            logger.warning(f"Local file not found: {filepath}")
            return None

    def get_data(self, symbol: str, interval: str = "1h",
                 start_date: str = None, end_date: str = None,
                 force_download: bool = False) -> pd.DataFrame:
        """
        Get data (prefer loading from local, download if not available)

        Args:
            symbol: Trading pair
            interval: Time interval
            start_date: Start date
            end_date: End date
            force_download: Whether to force re-download

        Returns:
            DataFrame
        """
        if not force_download:
            df = self.load_data(symbol, interval)
            if df is not None and len(df) > 0:
                # Filter data by date range
                if start_date:
                    df = df[df.index >= start_date]
                if end_date:
                    df = df[df.index <= end_date]
                return df

        # Download new data
        df = self.download_binance_data(symbol, interval, start_date, end_date)
        if not df.empty:
            self.save_data(df, symbol, interval)

        return df

    def download_multiple_symbols(self, symbols: List[str], interval: str = "1h",
                                 start_date: str = None, end_date: str = None):
        """Batch download data for multiple trading pairs"""
        for symbol in symbols:
            try:
                logger.info(f"Starting download for {symbol} data")
                df = self.download_binance_data(symbol, interval, start_date, end_date)
                if not df.empty:
                    self.save_data(df, symbol, interval)
                logger.info(f"{symbol} data download complete")
                time.sleep(1)  # Avoid too frequent requests
            except Exception as e:
                logger.error(f"Failed to download {symbol}: {str(e)}")
                continue
