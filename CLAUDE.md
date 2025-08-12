# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Maven Commands
- **Build project**: `mvn clean compile`
- **Run application**: `mvn spring-boot:run`
- **Run tests**: `mvn test`
- **Package JAR**: `mvn clean package`

### Quick Start Scripts
- **Windows**: Run `start.bat` or `start-all-services.bat` (includes Python service)
- **Linux/macOS**: Run `./start.sh` or `./start-all-services.sh`
- **Stop**: Run `stop.bat` or `./stop.sh`

### Testing
- **Integration tests**: `test-integration.bat`
- **Elder strategy test**: `test-elder-integration.bat`
- **Syntax check**: `check-syntax.bat`
- **Compile check**: `compile-check.bat`

## Architecture Overview

This is a **Spring Boot cryptocurrency trading application** that crawls K-line data from exchanges and generates trading signals using custom strategies.

### Core Components

1. **Exchange Data Layer** (`service.exchangedata`)
   - Abstract base: `AbstractExchangeDataService`
   - Implementations: `BinanceDataService`, `BybitDataService`, `GateIoDataService`, `OkxDataService`
   - Builder pattern: `ExchangeDataServiceBuilder`

2. **Trading Strategy System** (`service.strategy`)
   - Base class: `AbstractTradeStrategy` - all custom strategies inherit from this
   - Interface: `TradeStrategy`
   - DTO: `TradeStrategyDTO` - signal data structure
   - Implementations: `ElderIntradayStrategyAdapter`, `ElderSwingStrategyAdapter`

3. **Data Services**
   - `DataService` - K-line data management
   - `TradingPairService` - trading pair management
   - `TradeSignalService` - signal processing
   - `BacktestService` - strategy backtesting

4. **Client Integration**
   - `PythonStrategyClient` - interfaces with Python strategy service
   - External Python service runs on port 8000/8001

### Key Configuration

- **Application port**: 5567
- **Database**: H2 file-based (./db/tool)
- **H2 Console**: http://localhost:5567/h2-console
- **Python service URL**: http://localhost:8000
- **Strategy upload path**: `C:/Users/druid/PycharmProjects/crypto-trading-strategy/strategies`

### Data Flow
1. **Data Collection**: Exchange services fetch K-line data via REST APIs
2. **Strategy Execution**: `TradeStrategyService` runs strategies against collected data
3. **Signal Generation**: Strategies return `TradeStrategyDTO` objects
4. **Persistence**: Signals stored as `TradeSignal` entities
5. **Web Interface**: Thymeleaf templates serve trading dashboard

## Adding New Trading Strategies

1. Extend `AbstractTradeStrategy`
2. Implement `doHandle(Map<String,BarSeries> seriesMap)` method
3. Implement `getStrategyName()` method
4. Return `TradeStrategyDTO` with signal data
5. Use TA4J indicators for technical analysis

Example strategy template:
```java
@Component
public class MyStrategy extends AbstractTradeStrategy {
    @Override
    protected TradeStrategyDTO doHandle(Map<String,BarSeries> seriesMap) {
        // Access different timeframe data
        BarSeries series1H = seriesMap.get(KlineInterval._1H.name());
        // Implement strategy logic
        // Return TradeStrategyDTO with signal
    }
    
    @Override
    public String getStrategyName() {
        return "MyStrategy";
    }
}
```

## Important Dependencies

- **Spring Boot 3.4.2** (Java 17)
- **TA4J 0.15** - Technical analysis library
- **H2 Database** - Embedded database
- **Hutool** - Java utility library
- **OkHttp 4.12.0** - HTTP client for exchange APIs
- **Lombok** - Reduces boilerplate code

## Development Notes

- Application automatically opens browser to http://localhost:5567/api/index on startup
- Supports proxy configuration for data fetching
- Uses scheduled tasks for data cleanup and trading pair updates
- Integrates with external Python strategy service for advanced algorithms