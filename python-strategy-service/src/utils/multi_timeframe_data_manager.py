import pandas as pd
import numpy as np
from typing import Dict, List, Optional
from datetime import datetime, timedelta
from src.utils.data_downloader import DataDownloader
from src.services.unified_data_service import unified_data_service
from src.models.dto import KlineDataDTO
import logging

logger = logging.getLogger(__name__)


class MultiTimeframeDataManager:
    """Multi-timeframe data manager - uses unified data service"""

    def __init__(self, data_dir: str = "data"):
        self.data_downloader = DataDownloader(data_dir)  # Retained for downloading new data
        self.unified_data_service = unified_data_service  # Use unified data service

        # Timeframe mapping - starting from finest granularity
        self.timeframe_hierarchy = {
            '1m': {'pandas_freq': '1T', 'binance_interval': '1m', 'minutes': 1},
            '5m': {'pandas_freq': '5T', 'binance_interval': '5m', 'minutes': 5},
            '15m': {'pandas_freq': '15T', 'binance_interval': '15m', 'minutes': 15},
            '30m': {'pandas_freq': '30T', 'binance_interval': '30m', 'minutes': 30},
            '1h': {'pandas_freq': '1H', 'binance_interval': '1h', 'minutes': 60},
            '4h': {'pandas_freq': '4H', 'binance_interval': '4h', 'minutes': 240},
            '1d': {'pandas_freq': '1D', 'binance_interval': '1d', 'minutes': 1440},
            '1w': {'pandas_freq': '1W', 'binance_interval': '1w', 'minutes': 10080}
        }

        # Strategy timeframe mapping
        self.strategy_timeframe_mapping = {
            '_1M': '1m',
            '_5M': '5m',
            '_15M': '15m',
            '_30M': '30m',
            '_1H': '1h',
            '_4H': '4h',
            '_1D': '1d',
            '_1W': '1w'
        }

    def download_minute_data(self, symbol: str, days_back: int = 30,
                           start_date: str = None, end_date: str = None,
                           force_download: bool = False) -> pd.DataFrame:
        """
        Get minute-level data - prefer from database, download and save if not available

        Args:
            symbol: Trading pair
            days_back: Lookback days (default 30, approximately 43200 minute records)
            start_date: Start date (format: YYYY-MM-DD)
            end_date: End date (format: YYYY-MM-DD)
            force_download: Force re-download

        Returns:
            DataFrame: Minute-level K-line data
        """
        try:
            logger.info(f"Fetching {symbol} minute-level data, lookback {days_back} days")

            # Calculate date range
            if not start_date or not end_date:
                end_date = datetime.now().strftime('%Y-%m-%d')
                start_date = (datetime.now() - timedelta(days=days_back)).strftime('%Y-%m-%d')

            # First try to get from database
            if not force_download:
                logger.info(f"Trying to fetch {symbol} minute-level data from database")
                db_data = self.unified_data_service.get_kline_data(
                    symbol=symbol,
                    interval='1m',
                    start_date=start_date,
                    end_date=end_date
                )

                if not db_data.empty:
                    logger.info(f"Retrieved {len(db_data)} minute-level records from database")
                    return db_data
                else:
                    logger.info(f"No {symbol} minute-level data in database, starting download")

            # If not in database or forced, download new data
            logger.info(f"Downloading {symbol} minute-level data: {start_date} to {end_date}")

            minute_data = self.data_downloader.download_binance_data(
                symbol=symbol,
                interval='1m',
                start_date=start_date,
                end_date=end_date
            )

            if minute_data.empty:
                logger.warning(f"Unable to download {symbol} minute-level data")
                return pd.DataFrame()

            # Save to database
            logger.info(f"Saving {symbol} minute-level data to database")
            save_result = self.unified_data_service.save_kline_data(
                df=minute_data,
                symbol=symbol,
                exchange='BINANCE',  # Default exchange
                time_interval='1m'
            )

            if save_result['success']:
                logger.info(f"Minute-level data saved successfully: inserted {save_result['inserted']}, updated {save_result['updated']}")
            else:
                logger.error(f"Failed to save minute-level data: {save_result['error']}")

            return minute_data

        except Exception as e:
            logger.error(f"Failed to get minute-level data: {str(e)}")
            return pd.DataFrame()

    def resample_to_multiple_timeframes(self, minute_data: pd.DataFrame,
                                      required_timeframes: List[str]) -> Dict[str, pd.DataFrame]:
        """
        Resample minute-level data to multiple timeframes

        Args:
            minute_data: Minute-level data
            required_timeframes: List of required timeframes, e.g. ['5m', '1h', '1d']

        Returns:
            Data dictionary for each timeframe
        """
        timeframe_data = {}

        for timeframe in required_timeframes:
            if timeframe not in self.timeframe_hierarchy:
                logger.warning(f"Unsupported timeframe: {timeframe}")
                continue

            freq = self.timeframe_hierarchy[timeframe]['pandas_freq']

            # Resample data
            resampled = minute_data.resample(freq).agg({
                'open': 'first',
                'high': 'max',
                'low': 'min',
                'close': 'last',
                'volume': 'sum'
            }).dropna()

            logger.info(f"Resampled to {timeframe}: {len(resampled)} records")
            timeframe_data[timeframe] = resampled

        return timeframe_data

    def prepare_strategy_data(self, symbol: str, days_back: int = 90,
                            strategy_timeframes: List[str] = None) -> Dict[str, List[KlineDataDTO]]:
        """
        Prepare multi-timeframe data for strategy - significantly increase data volume

        Args:
            symbol: Trading pair
            days_back: Lookback days (default 90, provides sufficient data)
            strategy_timeframes: Strategy required timeframes, e.g. ['_1M', '_5M', '_1H', '_1D']

        Returns:
            Multi-timeframe data in strategy format
        """
        if strategy_timeframes is None:
            # Default to provide multiple timeframes from minute to daily
            strategy_timeframes = ['_1M', '_5M', '_15M', '_1H', '_4H', '_1D']

        logger.info(f"Preparing strategy data: {symbol}, timeframes: {strategy_timeframes}")

        # 1. Get minute-level base data
        minute_data = self.download_minute_data(symbol, days_back=days_back)
        if minute_data.empty:
            return {}

        # 2. Convert strategy timeframes to standard format
        required_timeframes = []
        for strategy_tf in strategy_timeframes:
            if strategy_tf in self.strategy_timeframe_mapping:
                standard_tf = self.strategy_timeframe_mapping[strategy_tf]
                required_timeframes.append(standard_tf)

        # 3. Resample to multiple timeframes
        timeframe_data = self.resample_to_multiple_timeframes(minute_data, required_timeframes)

        # 4. Convert to strategy required format
        strategy_data = {}
        for strategy_tf in strategy_timeframes:
            if strategy_tf not in self.strategy_timeframe_mapping:
                continue

            standard_tf = self.strategy_timeframe_mapping[strategy_tf]
            if standard_tf not in timeframe_data:
                continue

            df = timeframe_data[standard_tf]
            kline_list = []

            for timestamp, row in df.iterrows():
                kline_dto = KlineDataDTO(
                    openTime=int(timestamp.timestamp() * 1000),
                    closeTime=int((timestamp + pd.Timedelta(self.timeframe_hierarchy[standard_tf]['pandas_freq'])).timestamp() * 1000),
                    openPrice=str(row['open']),
                    highPrice=str(row['high']),
                    lowPrice=str(row['low']),
                    closePrice=str(row['close']),
                    volume=str(row['volume']),
                    # Provide default values for missing fields
                    quoteAssetVolume=str(row['volume'] * row['close']),  # Estimate quote asset volume
                    numberOfTrades=100,  # Default trade count
                    takerBuyBaseAssetVolume=str(float(row['volume']) * 0.5),  # Estimate taker buy volume
                    takerBuyQuoteAssetVolume=str(float(row['volume']) * float(row['close']) * 0.5)  # Estimate taker buy quote volume
                )
                kline_list.append(kline_dto)

            strategy_data[strategy_tf] = kline_list
            logger.info(f"Strategy data {strategy_tf}: {len(kline_list)} records")

        return strategy_data

    def get_enhanced_backtest_data(self, symbol: str, days_back: int = 180) -> Dict:
        """
        Get enhanced backtest data - large volume high-precision data for backtesting

        Args:
            symbol: Trading pair
            days_back: Lookback days (default 180, approximately 259200 minute records)

        Returns:
            Dictionary containing multi-timeframe data
        """
        logger.info(f"Fetching {symbol} enhanced backtest data, lookback {days_back} days")

        # 1. Get minute-level base data (large volume)
        minute_data = self.download_minute_data(symbol, days_back=days_back)
        if minute_data.empty:
            return {}

        # 2. Generate all common timeframes
        all_timeframes = ['1m', '5m', '15m', '30m', '1h', '4h', '1d']
        timeframe_data = self.resample_to_multiple_timeframes(minute_data, all_timeframes)

        # 3. Prepare base data for backtest (using minute-level)
        base_data = minute_data.copy()

        # 4. Prepare multi-timeframe data for strategy
        strategy_timeframes = ['_1M', '_5M', '_15M', '_30M', '_1H', '_4H', '_1D', '_1W']
        strategy_data = {}

        for strategy_tf in strategy_timeframes:
            if strategy_tf not in self.strategy_timeframe_mapping:
                continue

            standard_tf = self.strategy_timeframe_mapping[strategy_tf]
            if standard_tf not in timeframe_data:
                continue

            df = timeframe_data[standard_tf]
            kline_list = []

            for timestamp, row in df.iterrows():
                kline_dto = KlineDataDTO(
                    openTime=int(timestamp.timestamp() * 1000),
                    closeTime=int((timestamp + pd.Timedelta(
                        self.timeframe_hierarchy[standard_tf]['pandas_freq']
                    )).timestamp() * 1000),
                    openPrice=str(row['open']),
                    highPrice=str(row['high']),
                    lowPrice=str(row['low']),
                    closePrice=str(row['close']),
                    volume=str(row['volume']),
                    # Provide default values for missing fields
                    quoteAssetVolume=str(row['volume'] * row['close']),
                    numberOfTrades=100,
                    takerBuyBaseAssetVolume=str(float(row['volume']) * 0.5),
                    takerBuyQuoteAssetVolume=str(float(row['volume']) * float(row['close']) * 0.5)
                )
                kline_list.append(kline_dto)

            strategy_data[strategy_tf] = kline_list

        logger.info(f"Enhanced backtest data preparation complete:")
        logger.info(f"  Base data (minute-level): {len(base_data)} records")
        for tf, data in strategy_data.items():
            logger.info(f"  Strategy data {tf}: {len(data)} records")

        return {
            'base_data': base_data,
            'strategy_data': strategy_data,
            'timeframe_data': timeframe_data
        }

    def create_rolling_window_data(self, strategy_data: Dict[str, List[KlineDataDTO]],
                                  window_size: int = 1000) -> List[Dict[str, List[KlineDataDTO]]]:
        """
        Create rolling window data for simulating real-time data access during backtesting

        Args:
            strategy_data: Strategy multi-timeframe data
            window_size: Window size (provides sufficient historical data for indicator calculation)

        Returns:
            Rolling window data list
        """
        if not strategy_data:
            return []

        # Find the shortest timeframe as baseline
        min_length = min(len(data) for data in strategy_data.values())

        rolling_data = []
        for i in range(window_size, min_length):
            window_data = {}
            for timeframe, kline_list in strategy_data.items():
                # Calculate window data volume for this timeframe
                tf_window_size = min(window_size, len(kline_list))
                if timeframe == '_1M':  # Minute-level data uses full window
                    window_data[timeframe] = kline_list[:i+1]
                else:  # Other timeframes adjust window proportionally
                    ratio = len(kline_list) / min_length
                    adjusted_window = int(tf_window_size * ratio)
                    end_idx = int(i * ratio) + 1
                    start_idx = max(0, end_idx - adjusted_window)
                    window_data[timeframe] = kline_list[start_idx:end_idx]

            rolling_data.append(window_data)

        logger.info(f"Created {len(rolling_data)} rolling windows, each window contains up to {window_size} base records")
        return rolling_data
