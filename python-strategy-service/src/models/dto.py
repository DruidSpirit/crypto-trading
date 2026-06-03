from pydantic import BaseModel, Field
from typing import Dict, List, Optional
from decimal import Decimal
from datetime import datetime


class KlineDataDTO(BaseModel):
    """K-line data DTO"""
    openTime: int
    openPrice: Decimal
    highPrice: Decimal
    lowPrice: Decimal
    closePrice: Decimal
    volume: Decimal
    closeTime: int
    quoteAssetVolume: Decimal
    numberOfTrades: int
    takerBuyBaseAssetVolume: Decimal
    takerBuyQuoteAssetVolume: Decimal


class StrategyRequestDTO(BaseModel):
    """Strategy request DTO"""
    symbol: str
    strategyName: str
    klineData: Dict[str, List[KlineDataDTO]]


class TradeStrategyDTO(BaseModel):
    """Trading strategy result DTO"""
    signal: str = Field(..., description="Trading signal BUY/SELL")
    price: Decimal = Field(..., description="Current price")
    buy_price: Decimal = Field(..., description="Suggested buy price")
    take_profit: Decimal = Field(..., description="Take profit price")
    stop_loss: Decimal = Field(..., description="Stop loss price")
    profit_loss_ratio: Decimal = Field(..., description="Profit/loss ratio")
    expiration: str = Field(..., description="Signal validity period")
    signal_time: Optional[datetime] = Field(default=None, description="Signal generation time")
    remark: Optional[str] = Field(default=None, description="Remark")


class StrategyResponseDTO(BaseModel):
    """Strategy response DTO"""
    success: bool
    data: Optional[TradeStrategyDTO] = None
    message: Optional[str] = None
