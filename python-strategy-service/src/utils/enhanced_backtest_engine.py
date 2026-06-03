import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from typing import List, Optional, Dict, Any
import logging
from src.models.backtest_dto import BacktestResult, TradeRecord
from src.strategies.strategy_factory import StrategyFactory
from src.utils.sqlite_data_manager import SQLiteDataManager

logger = logging.getLogger(__name__)


class EnhancedBacktestEngine:
    """Enhanced backtest engine - supports SQLite database and multiple timeframes"""

    def __init__(self, initial_balance: float = 10000.0, db_path: str = "crypto_trading.db"):
        self.initial_balance = initial_balance
        self.balance = initial_balance
        self.position = 0.0  # Holding quantity
        self.position_value = 0.0  # Position value
        self.trade_records: List[TradeRecord] = []
        self.portfolio_values = []  # Record portfolio value at each time point

        # SQLite data manager
        self.db_manager = SQLiteDataManager(db_path)

        logger.info(f"Enhanced backtest engine initialized, initial capital: {initial_balance}")

    def reset(self):
        """Reset backtest state"""
        self.balance = self.initial_balance
        self.position = 0.0
        self.position_value = 0.0
        self.trade_records = []
        self.portfolio_values = []

    def execute_trade(self, timestamp: datetime, action: str, price: float,
                     quantity: float = None, signal_strength: float = None,
                     reason: str = None):
        """
        Execute trade

        Args:
            timestamp: Trade time
            action: Trade action BUY/SELL
            price: Trade price
            quantity: Trade quantity (if None, calculated proportionally)
            signal_strength: Signal strength
            reason: Trade reason
        """
        if action == "BUY":
            if quantity is None:
                # If no quantity specified, use all cash to buy
                quantity = self.balance / price

            cost = quantity * price
            if cost <= self.balance:
                self.balance -= cost
                self.position += quantity
                self.position_value = self.position * price

                trade_record = TradeRecord(
                    timestamp=timestamp,
                    action=action,
                    price=price,
                    quantity=quantity,
                    balance=self.balance,
                    portfolio_value=self.balance + self.position_value,
                    signal_strength=signal_strength,
                    reason=reason
                )
                self.trade_records.append(trade_record)
                logger.debug(f"BUY: {quantity:.6f} @ {price:.2f}, balance: {self.balance:.2f}")

        elif action == "SELL":
            if quantity is None:
                # If no quantity specified, sell all positions
                quantity = self.position

            if quantity <= self.position:
                proceeds = quantity * price
                self.balance += proceeds
                self.position -= quantity
                self.position_value = self.position * price

                trade_record = TradeRecord(
                    timestamp=timestamp,
                    action=action,
                    price=price,
                    quantity=quantity,
                    balance=self.balance,
                    portfolio_value=self.balance + self.position_value,
                    signal_strength=signal_strength,
                    reason=reason
                )
                self.trade_records.append(trade_record)
                logger.debug(f"SELL: {quantity:.6f} @ {price:.2f}, balance: {self.balance:.2f}")

    def update_portfolio_value(self, timestamp: datetime, current_price: float):
        """Update portfolio value"""
        self.position_value = self.position * current_price
        total_value = self.balance + self.position_value
        self.portfolio_values.append({
            'timestamp': timestamp,
            'total_value': total_value,
            'cash': self.balance,
            'position_value': self.position_value,
            'price': current_price
        })

    def run_database_backtest(self, symbol: str, strategy_name: str, timeframe: str = '1m',
                            days_back: int = 7, strategy_params: Dict[str, Any] = None) -> BacktestResult:
        """
        Run database-based backtest

        Args:
            symbol: Trading pair
            strategy_name: Strategy name
            timeframe: Timeframe
            days_back: Lookback days
            strategy_params: Strategy parameters

        Returns:
            Backtest result
        """
        logger.info(f"Starting database backtest for {strategy_name} strategy")
        logger.info(f"Symbol: {symbol}, timeframe: {timeframe}, lookback: {days_back} days")

        # Reset state
        self.reset()

        # Create strategy instance
        strategy = StrategyFactory.create_strategy(strategy_name)
        if strategy is None:
            raise ValueError(f"Strategy not found: {strategy_name}")

        # Get historical data from database
        end_time = datetime.now()
        start_time = end_time - timedelta(days=days_back)

        logger.info(f"Query time range: {start_time} to {end_time}")

        # Get K-line data
        klines_data = self.db_manager.get_klines(
            symbol=symbol,
            timeframe=timeframe,
            start_time=start_time,
            end_time=end_time
        )

        if not klines_data:
            raise ValueError(f"No historical data found for {symbol} {timeframe}")

        logger.info(f"Retrieved historical data: {len(klines_data)} K-lines")

        # Convert to DataFrame
        df = pd.DataFrame(klines_data)
        df['open_time'] = pd.to_datetime(df['open_time'])
        df.set_index('open_time', inplace=True)

        # Prepare strategy data format
        data_list = []
        for timestamp, row in df.iterrows():
            data_list.append({
                'timestamp': timestamp.isoformat(),
                'open': float(row['open_price']),
                'high': float(row['high_price']),
                'low': float(row['low_price']),
                'close': float(row['close_price']),
                'volume': float(row['volume'])
            })

        # Iterate through historical data for backtesting
        for i, row_data in enumerate(data_list):
            timestamp = pd.to_datetime(row_data['timestamp'])
            current_price = row_data['close']

            # Update portfolio value
            self.update_portfolio_value(timestamp, current_price)

            # Prepare historical data for strategy (data before current time point)
            kline_data = data_list[:i+1]

            # Skip if insufficient data
            if len(kline_data) < 50:  # Need enough data to calculate indicators
                continue

            try:
                # Execute strategy
                signal = strategy.execute(symbol, kline_data)

                if signal and signal.signal:
                    if signal.signal == "BUY" and self.balance > 0:
                        # Buy signal and has cash
                        self.execute_trade(
                            timestamp=timestamp,
                            action="BUY",
                            price=current_price,
                            signal_strength=getattr(signal, 'strength', None),
                            reason=f"Strategy signal: {signal.signal}"
                        )

                    elif signal.signal == "SELL" and self.position > 0:
                        # Sell signal and has position
                        self.execute_trade(
                            timestamp=timestamp,
                            action="SELL",
                            price=current_price,
                            signal_strength=getattr(signal, 'strength', None),
                            reason=f"Strategy signal: {signal.signal}"
                        )

            except Exception as e:
                logger.warning(f"Strategy execution failed at {timestamp}: {str(e)}")
                continue

            # Output progress every 500 data points
            if i % 500 == 0 and i > 0:
                logger.info(f"Backtest progress: {i}/{len(data_list)} ({i/len(data_list)*100:.1f}%)")

        # Calculate final results
        final_price = data_list[-1]['close']
        final_position_value = self.position * final_price
        final_portfolio_value = self.balance + final_position_value

        # Calculate performance metrics
        performance_metrics = self._calculate_performance_metrics(df)

        # Build backtest result
        result = BacktestResult(
            strategy_name=strategy_name,
            symbol=symbol,
            start_date=df.index[0].strftime('%Y-%m-%d %H:%M:%S'),
            end_date=df.index[-1].strftime('%Y-%m-%d %H:%M:%S'),
            initial_balance=self.initial_balance,
            final_balance=self.balance,
            final_portfolio_value=final_portfolio_value,
            total_return=final_portfolio_value - self.initial_balance,
            total_return_pct=(final_portfolio_value - self.initial_balance) / self.initial_balance * 100,
            max_drawdown=performance_metrics['max_drawdown'],
            max_drawdown_pct=performance_metrics['max_drawdown_pct'],
            sharpe_ratio=performance_metrics['sharpe_ratio'],
            total_trades=len(self.trade_records),
            winning_trades=performance_metrics['winning_trades'],
            losing_trades=performance_metrics['losing_trades'],
            win_rate=performance_metrics['win_rate'],
            avg_win=performance_metrics['avg_win'],
            avg_loss=performance_metrics['avg_loss'],
            profit_factor=performance_metrics['profit_factor'],
            trade_records=self.trade_records,
            performance_metrics=performance_metrics
        )

        logger.info(f"Database backtest complete:")
        logger.info(f"  Data volume: {len(data_list)} K-lines")
        logger.info(f"  Timeframe: {timeframe}")
        logger.info(f"  Total return: {result.total_return:.2f} ({result.total_return_pct:.2f}%)")
        logger.info(f"  Total trades: {result.total_trades}")
        logger.info(f"  Win rate: {result.win_rate:.1f}%")

        return result

    def run_multi_timeframe_backtest(self, symbol: str, strategy_name: str,
                                   primary_timeframe: str = '1m', days_back: int = 7,
                                   strategy_params: Dict[str, Any] = None) -> BacktestResult:
        """
        Run multi-timeframe backtest

        Args:
            symbol: Trading pair
            strategy_name: Strategy name
            primary_timeframe: Primary timeframe
            days_back: Lookback days
            strategy_params: Strategy parameters

        Returns:
            Backtest result
        """
        logger.info(f"Starting multi-timeframe backtest for {strategy_name} strategy")
        logger.info(f"Symbol: {symbol}, primary timeframe: {primary_timeframe}")

        # Reset state
        self.reset()

        # Create strategy instance
        strategy = StrategyFactory.create_strategy(strategy_name)
        if strategy is None:
            raise ValueError(f"Strategy not found: {strategy_name}")

        # Get multi-timeframe data
        timeframes = ['1m', '5m', '15m', '1h']
        end_time = datetime.now()
        start_time = end_time - timedelta(days=days_back)

        multi_tf_data = {}
        for tf in timeframes:
            klines = self.db_manager.get_klines(symbol, tf, start_time, end_time)
            if klines:
                df = pd.DataFrame(klines)
                df['open_time'] = pd.to_datetime(df['open_time'])
                df.set_index('open_time', inplace=True)
                multi_tf_data[tf] = df

        if not multi_tf_data:
            raise ValueError(f"No multi-timeframe data found for {symbol}")

        # Use primary timeframe for backtest loop
        primary_data = multi_tf_data.get(primary_timeframe)
        if primary_data is None:
            raise ValueError(f"Primary timeframe {primary_timeframe} data not found")

        logger.info(f"Multi-timeframe data volume: {[f'{tf}:{len(df)}' for tf, df in multi_tf_data.items()]}")

        # Prepare multi-timeframe data format
        for i, (timestamp, row) in enumerate(primary_data.iterrows()):
            current_price = float(row['close_price'])

            # Update portfolio value
            self.update_portfolio_value(timestamp, current_price)

            # Skip if insufficient data
            if i < 50:
                continue

            try:
                # Build multi-timeframe data structure
                multi_data = {}
                for tf, tf_data in multi_tf_data.items():
                    # Get data before current time point
                    historical_tf_data = tf_data[tf_data.index <= timestamp]
                    if not historical_tf_data.empty:
                        # Convert to strategy format
                        tf_data_list = []
                        for ts, r in historical_tf_data.iterrows():
                            tf_data_list.append({
                                'timestamp': ts.isoformat(),
                                'open': float(r['open_price']),
                                'high': float(r['high_price']),
                                'low': float(r['low_price']),
                                'close': float(r['close_price']),
                                'volume': float(r['volume'])
                            })
                        multi_data[tf] = tf_data_list

                # Execute strategy (pass multi-timeframe data)
                signal = strategy.execute(symbol, multi_data)

                if signal and signal.signal:
                    if signal.signal == "BUY" and self.balance > 0:
                        self.execute_trade(
                            timestamp=timestamp,
                            action="BUY",
                            price=current_price,
                            signal_strength=getattr(signal, 'strength', None),
                            reason=f"Multi-TF signal: {signal.signal}"
                        )

                    elif signal.signal == "SELL" and self.position > 0:
                        self.execute_trade(
                            timestamp=timestamp,
                            action="SELL",
                            price=current_price,
                            signal_strength=getattr(signal, 'strength', None),
                            reason=f"Multi-TF signal: {signal.signal}"
                        )

            except Exception as e:
                logger.warning(f"Multi-timeframe strategy execution failed at {timestamp}: {str(e)}")
                continue

            # Output progress every 200 data points
            if i % 200 == 0 and i > 0:
                logger.info(f"Multi-timeframe backtest progress: {i}/{len(primary_data)} ({i/len(primary_data)*100:.1f}%)")

        # Calculate final results
        final_price = float(primary_data.iloc[-1]['close_price'])
        final_position_value = self.position * final_price
        final_portfolio_value = self.balance + final_position_value

        # Calculate performance metrics
        performance_metrics = self._calculate_performance_metrics(primary_data)

        # Build backtest result
        result = BacktestResult(
            strategy_name=strategy_name,
            symbol=symbol,
            start_date=primary_data.index[0].strftime('%Y-%m-%d %H:%M:%S'),
            end_date=primary_data.index[-1].strftime('%Y-%m-%d %H:%M:%S'),
            initial_balance=self.initial_balance,
            final_balance=self.balance,
            final_portfolio_value=final_portfolio_value,
            total_return=final_portfolio_value - self.initial_balance,
            total_return_pct=(final_portfolio_value - self.initial_balance) / self.initial_balance * 100,
            max_drawdown=performance_metrics['max_drawdown'],
            max_drawdown_pct=performance_metrics['max_drawdown_pct'],
            sharpe_ratio=performance_metrics['sharpe_ratio'],
            total_trades=len(self.trade_records),
            winning_trades=performance_metrics['winning_trades'],
            losing_trades=performance_metrics['losing_trades'],
            win_rate=performance_metrics['win_rate'],
            avg_win=performance_metrics['avg_win'],
            avg_loss=performance_metrics['avg_loss'],
            profit_factor=performance_metrics['profit_factor'],
            trade_records=self.trade_records,
            performance_metrics=performance_metrics
        )

        logger.info(f"Multi-timeframe backtest complete:")
        logger.info(f"  Primary timeframe: {primary_timeframe} ({len(primary_data)} records)")
        logger.info(f"  Multi-timeframes: {list(multi_tf_data.keys())}")
        logger.info(f"  Total return: {result.total_return:.2f} ({result.total_return_pct:.2f}%)")
        logger.info(f"  Total trades: {result.total_trades}")

        return result

    def _calculate_performance_metrics(self, data: pd.DataFrame) -> Dict[str, Any]:
        """Calculate performance metrics"""
        metrics = {}

        if not self.portfolio_values:
            return {
                'max_drawdown': 0,
                'max_drawdown_pct': 0,
                'sharpe_ratio': 0,
                'winning_trades': 0,
                'losing_trades': 0,
                'win_rate': 0,
                'avg_win': 0,
                'avg_loss': 0,
                'profit_factor': 0
            }

        # Convert to DataFrame for calculation
        pv_df = pd.DataFrame(self.portfolio_values)
        pv_df.set_index('timestamp', inplace=True)

        # Calculate returns
        returns = pv_df['total_value'].pct_change().dropna()

        # Max drawdown
        rolling_max = pv_df['total_value'].expanding().max()
        drawdown = (pv_df['total_value'] - rolling_max) / rolling_max
        metrics['max_drawdown'] = abs(drawdown.min()) * self.initial_balance
        metrics['max_drawdown_pct'] = abs(drawdown.min()) * 100

        # Sharpe ratio
        if len(returns) > 1 and returns.std() != 0:
            metrics['sharpe_ratio'] = (returns.mean() / returns.std()) * np.sqrt(252 * 24 * 60)  # Minute-level annualized
        else:
            metrics['sharpe_ratio'] = 0

        # Trade statistics
        if len(self.trade_records) >= 2:
            # Calculate profit/loss for each trade
            trade_pnl = []
            buy_price = None

            for trade in self.trade_records:
                if trade.action == "BUY":
                    buy_price = trade.price
                elif trade.action == "SELL" and buy_price is not None:
                    pnl = (trade.price - buy_price) * trade.quantity
                    trade_pnl.append(pnl)
                    buy_price = None

            if trade_pnl:
                winning_trades = [pnl for pnl in trade_pnl if pnl > 0]
                losing_trades = [pnl for pnl in trade_pnl if pnl < 0]

                metrics['winning_trades'] = len(winning_trades)
                metrics['losing_trades'] = len(losing_trades)
                metrics['win_rate'] = len(winning_trades) / len(trade_pnl) * 100
                metrics['avg_win'] = np.mean(winning_trades) if winning_trades else 0
                metrics['avg_loss'] = abs(np.mean(losing_trades)) if losing_trades else 0

                if metrics['avg_loss'] > 0:
                    metrics['profit_factor'] = metrics['avg_win'] / metrics['avg_loss']
                else:
                    metrics['profit_factor'] = float('inf') if metrics['avg_win'] > 0 else 0
            else:
                metrics.update({
                    'winning_trades': 0,
                    'losing_trades': 0,
                    'win_rate': 0,
                    'avg_win': 0,
                    'avg_loss': 0,
                    'profit_factor': 0
                })
        else:
            metrics.update({
                'winning_trades': 0,
                'losing_trades': 0,
                'win_rate': 0,
                'avg_win': 0,
                'avg_loss': 0,
                'profit_factor': 0
            })

        return metrics

    def get_database_summary(self) -> Dict[str, Any]:
        """Get database overview"""
        return {
            'database_info': self.db_manager.get_database_info(),
            'data_summary': self.db_manager.get_data_summary()
        }
