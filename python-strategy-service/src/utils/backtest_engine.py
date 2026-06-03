import pandas as pd
import numpy as np
from datetime import datetime
from typing import List, Optional, Dict, Any
import logging
from src.models.backtest_dto import BacktestResult, TradeRecord
from src.strategies.strategy_factory import StrategyFactory
from src.utils.multi_timeframe_data_manager import MultiTimeframeDataManager

logger = logging.getLogger(__name__)


class BacktestEngine:
    """Enhanced backtest engine - supports minute-level data and multiple timeframes"""

    def __init__(self, initial_balance: float = 10000.0, data_dir: str = "data"):
        self.initial_balance = initial_balance
        self.balance = initial_balance
        self.position = 0.0  # Holding quantity
        self.position_value = 0.0  # Position value
        self.trade_records: List[TradeRecord] = []
        self.portfolio_values = []  # Record portfolio value at each time point

        # Multi-timeframe data manager
        self.data_manager = MultiTimeframeDataManager(data_dir)

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

    def run_backtest(self, symbol: str, data: pd.DataFrame, strategy_name: str,
                    strategy_params: Dict[str, Any] = None) -> BacktestResult:
        """
        Run backtest

        Args:
            symbol: Trading pair
            data: Historical price data
            strategy_name: Strategy name
            strategy_params: Strategy parameters

        Returns:
            Backtest result
        """
        logger.info(f"Starting backtest for {strategy_name} strategy, symbol: {symbol}")

        # Reset state
        self.reset()

        # Create strategy instance
        strategy = StrategyFactory.create_strategy(strategy_name)
        if strategy is None:
            raise ValueError(f"Strategy not found: {strategy_name}")

        # Prepare data
        data_list = []
        for timestamp, row in data.iterrows():
            data_list.append({
                'timestamp': timestamp.isoformat() if hasattr(timestamp, 'isoformat') else str(timestamp),
                'open': float(row['open']),
                'high': float(row['high']),
                'low': float(row['low']),
                'close': float(row['close']),
                'volume': float(row['volume'])
            })

        # Iterate through historical data
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
                # Generate simple signal for compatibility with existing strategy interface
                # Based on simple moving average crossover strategy
                signal = self._generate_simple_signal(kline_data, current_price)

                if signal:
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

        # Calculate final results
        final_price = data_list[-1]['close']
        final_position_value = self.position * final_price
        final_portfolio_value = self.balance + final_position_value

        # Calculate performance metrics
        performance_metrics = self._calculate_performance_metrics(data)

        # Build backtest result
        result = BacktestResult(
            strategy_name=strategy_name,
            symbol=symbol,
            start_date=data.index[0].strftime('%Y-%m-%d'),
            end_date=data.index[-1].strftime('%Y-%m-%d'),
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

        logger.info(f"Backtest complete, total return: {result.total_return:.2f} ({result.total_return_pct:.2f}%)")
        return result

    def run_enhanced_backtest(self, symbol: str, strategy_name: str,
                            days_back: int = 90, min_data_points: int = 2000,
                            strategy_params: Dict[str, Any] = None) -> BacktestResult:
        """
        Run enhanced backtest - uses minute-level data and multiple timeframes

        Args:
            symbol: Trading pair
            strategy_name: Strategy name
            days_back: Lookback days (default 90, provides large data)
            min_data_points: Minimum data points (default 2000, much more than original 50)
            strategy_params: Strategy parameters

        Returns:
            Backtest result
        """
        logger.info(f"Starting enhanced backtest for {strategy_name} strategy, symbol: {symbol}, lookback: {days_back} days")

        # Reset state
        self.reset()

        # Create strategy instance
        strategy = StrategyFactory.create_strategy(strategy_name)
        if strategy is None:
            raise ValueError(f"Strategy not found: {strategy_name}")

        # Get enhanced data
        enhanced_data = self.data_manager.get_enhanced_backtest_data(symbol, days_back=days_back)
        if not enhanced_data or 'base_data' not in enhanced_data:
            raise ValueError(f"Unable to get enhanced data for {symbol}")

        base_data = enhanced_data['base_data']
        strategy_data = enhanced_data['strategy_data']

        logger.info(f"Retrieved base data: {len(base_data)} minute-level records")

        # Create rolling window data
        rolling_windows = self.data_manager.create_rolling_window_data(
            strategy_data, window_size=min_data_points
        )

        logger.info(f"Created {len(rolling_windows)} rolling windows")

        if len(rolling_windows) == 0:
            raise ValueError("Insufficient rolling window data, please increase lookback days")

        # Iterate through each time window for backtesting
        for i, window_data in enumerate(rolling_windows):
            # Get current time point
            if '_1M' in window_data and len(window_data['_1M']) > 0:
                current_kline = window_data['_1M'][-1]  # Latest minute data
                timestamp = pd.to_datetime(current_kline.openTime, unit='ms')
                current_price = float(current_kline.closePrice)
            else:
                continue

            # Update portfolio value
            self.update_portfolio_value(timestamp, current_price)

            try:
                # Execute strategy (now with large amount of historical data support)
                signal = strategy.execute(symbol, window_data)

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

            # Output progress every 1000 windows
            if i % 1000 == 0:
                logger.info(f"Backtest progress: {i}/{len(rolling_windows)} ({i/len(rolling_windows)*100:.1f}%)")

        # Calculate final results
        if '_1M' in strategy_data and len(strategy_data['_1M']) > 0:
            final_kline = strategy_data['_1M'][-1]
            final_price = float(final_kline.closePrice)
        else:
            final_price = base_data['close'].iloc[-1]

        final_position_value = self.position * final_price
        final_portfolio_value = self.balance + final_position_value

        # Calculate performance metrics
        performance_metrics = self._calculate_performance_metrics(base_data)

        # Build backtest result
        result = BacktestResult(
            strategy_name=strategy_name,
            symbol=symbol,
            start_date=base_data.index[0].strftime('%Y-%m-%d'),
            end_date=base_data.index[-1].strftime('%Y-%m-%d'),
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

        logger.info(f"Enhanced backtest complete:")
        logger.info(f"  Data volume: {len(base_data)} minute-level records")
        logger.info(f"  Backtest windows: {len(rolling_windows)}")
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
            metrics['sharpe_ratio'] = (returns.mean() / returns.std()) * np.sqrt(252)  # Annualized
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

    def _generate_simple_signal(self, kline_data: List[Dict], current_price: float):
        """
        Generate simple trading signal (based on moving average)
        """
        if len(kline_data) < 20:
            return None

        # Calculate simple moving averages
        closes = [float(k['close']) for k in kline_data[-20:]]
        sma_short = sum(closes[-5:]) / 5  # 5-period short MA
        sma_long = sum(closes[-20:]) / 20  # 20-period long MA

        # Simple golden cross / death cross strategy
        if sma_short > sma_long and current_price > sma_short:
            # Golden cross and price above short MA, buy signal
            from dataclasses import dataclass
            @dataclass
            class SimpleSignal:
                signal: str
                strength: float = 0.5
            return SimpleSignal(signal="BUY")
        elif sma_short < sma_long and current_price < sma_short:
            # Death cross and price below short MA, sell signal
            from dataclasses import dataclass
            @dataclass
            class SimpleSignal:
                signal: str
                strength: float = 0.5
            return SimpleSignal(signal="SELL")

        return None
