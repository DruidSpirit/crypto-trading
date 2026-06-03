from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any, Union
from datetime import datetime


class BacktestRequestDTO(BaseModel):
    """Backtest request DTO"""
    model_config = {"extra": "allow"}

    strategy_name: str = Field(..., description="Strategy name")
    symbol: str = Field(..., description="Trading pair, e.g. BTCUSDT")
    start_date: Union[str, None] = Field(default=None, description="Start date")
    end_date: Union[str, None] = Field(default=None, description="End date")
    initial_balance: float = Field(default=10000.0, description="Initial balance")
    timeframe: str = Field(default="1h", description="Timeframe")
    strategy_params: Union[Dict[str, Any], None] = Field(default=None, description="Strategy parameters")
    task_id: Union[str, None] = Field(default=None, description="Task ID")


class TradeRecord(BaseModel):
    """Trade record"""
    timestamp: datetime
    action: str  # BUY, SELL
    price: float
    quantity: float
    balance: float
    portfolio_value: float
    signal_strength: Optional[float] = None
    reason: Optional[str] = None


class BacktestResult(BaseModel):
    """Backtest result"""
    strategy_name: str
    symbol: str
    start_date: str
    end_date: str
    initial_balance: float
    final_balance: float
    final_portfolio_value: float
    total_return: float
    total_return_pct: float
    max_drawdown: float
    max_drawdown_pct: float
    sharpe_ratio: float
    total_trades: int
    winning_trades: int
    losing_trades: int
    win_rate: float
    avg_win: float
    avg_loss: float
    profit_factor: float
    trade_records: List[TradeRecord]
    performance_metrics: Dict[str, Any]


class BacktestResponseDTO(BaseModel):
    """Backtest response DTO"""
    success: bool
    message: str = ""
    data: Optional[BacktestResult] = None
