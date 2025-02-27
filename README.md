## 📖 项目简介

**Crypto Kline Crawler & Signal Generator** 是一个开源项目，专注于从各大加密货币交易所的公开API中爬取历史K线数据，并结合用户自定义的交易策略生成交易信号。项目内置了一个简单但实用的交易策略示例，同时提供灵活的扩展性，支持多线程数据拉取和代理配置。

### ✨ 核心功能
- **数据爬取**：从交易所（如默认Gate.io）获取历史K线数据。
- **信号生成**：基于自定义策略生成买入/卖出信号。
- **高性能**：支持多代理、多线程加速数据拉取。
- **Web控制台**：内置管理界面，实时监控爬取和信号状态。

---

## 🚀 快速开始

### Windows
1. 下载并解压项目。
2. 双击 `start.bat` 启动。
3. 双击 `stop.bat` 停止。

### macOS
1. 下载并解压项目。
2. 打开终端，进入项目目录：
   ```bash
   cd /path/to/project
###   启动：
   ```bash
sh start.sh
###   停止：
   ```bash
sh stop.sh
```
> 启动成功后，浏览器会自动打开控制页面。若未自动打开，请手动访问：
🔗 [http://localhost:5567/api/index](http://localhost:5567/api/index)

> ⚠️ **注意**：项目打开后需等待10分钟左右拉取数据，刷新页面即可查看最新交易信号数据。若无法启动，请检查 `5567` 端口是否被占用。可使用以下命令检查并释放：
> ```bash
> netstat -aon | findstr :5567  # Windows
> lsof -i :5567                 # macOS
> ```

---

## 🛠 使用指南

### 数据拉取配置
- **默认交易所**：Gate.io（可在配置文件中切换其他交易所）。
- **代理支持**：
  - 数据拉取较慢时，可添加代理。
  - 支持多个代理，多代理启用多线程爬取，提升效率。
  - **建议**：低配电脑最多配置10个代理，避免性能瓶颈。
- **币种选择**：
  - 可自定义拉取特定交易对。
  - 或选择 `all` 模式，爬取交易所全部数据。
  - ⚠️ **警告**：`all` 模式数据量大，代理不足时请谨慎使用。

### 自定义交易策略
1. **实现步骤**：
   - 继承 `AbstractTradeStrategy` 类。
   - 重写 `doHandle` 方法，返回 `TradeStrategyDTO`。
2. **代码示例**：见下方 [RSI+ATR突破策略示例](#rsi+atr突破策略示例)。

---

## 📈 RSI+ATR突破策略示例

以下是一个自定义的 `RsiAtrBreakoutStrategy`，结合RSI判断趋势方向，ATR和布林带确定突破信号：

```java
package druid.elf.tool.service.strategy.impl;

import druid.elf.tool.enums.KlineInterval;
import druid.elf.tool.service.strategy.AbstractTradeStrategy;
import druid.elf.tool.service.strategy.TradeStrategyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * RSI+ATR突破策略
 * - 4小时RSI判断趋势方向（超买/超卖）
 * - 1小时布林带确定突破信号和止盈
 * - 15分钟ATR设置止损
 */
@Component
@Slf4j
public class RsiAtrBreakoutStrategy extends AbstractTradeStrategy {

    private static final int CRYPTO_SCALE = 8; // 加密货币价格精度：8位小数

    @Override
    protected TradeStrategyDTO doHandle(Map<String, BarSeries> seriesMap) {
        log.info("开始执行RSI+ATR突破策略...");

        // 获取不同时间级别的K线数据
        BarSeries fourHSeries = seriesMap.get(KlineInterval._4H.name());
        BarSeries oneHSeries = seriesMap.get(KlineInterval._1H.name());
        BarSeries fifteenMSeries = seriesMap.get(KlineInterval._15M.name());

        // 数据检查
        if (fourHSeries == null || oneHSeries == null || fifteenMSeries == null) {
            log.warn("K线数据不完整：4H={}, 1H={}, 15M={}", fourHSeries, oneHSeries, fifteenMSeries);
            return null;
        }
        log.info("K线数据加载成功：4H={}条, 1H={}条, 15M={}条",
                fourHSeries.getBarCount(), oneHSeries.getBarCount(), fifteenMSeries.getBarCount());

        // 获取最新索引
        int lastIndex4H = fourHSeries.getEndIndex();
        int lastIndex1H = oneHSeries.getEndIndex();
        int lastIndex15M = fifteenMSeries.getEndIndex();

        // 第一步：4小时RSI判断趋势方向
        log.info("第一步：计算4小时RSI趋势...");
        Indicator<Num> close4H = new ClosePriceIndicator(fourHSeries);
        RSIIndicator rsi4H = new RSIIndicator(close4H, 14);
        Num rsiLast = rsi4H.getValue(lastIndex4H);
        Num rsiPrev = rsi4H.getValue(lastIndex4H - 1);

        boolean isBullish = rsiPrev.doubleValue() < 40 && rsiLast.doubleValue() > 40; // RSI脱离弱势
        boolean isBearish = rsiPrev.doubleValue() > 60 && rsiLast.doubleValue() < 60; // RSI脱离强势

        if (!isBullish && !isBearish) {
            log.info("RSI趋势不明确，策略终止");
            return null;
        }
        log.info("RSI趋势：看多={}，看空={}", isBullish, isBearish);

        // 第二步：1小时布林带判断突破并设置止盈
        log.info("第二步：计算1小时布林带突破信号...");
        Indicator<Num> close1H = new ClosePriceIndicator(oneHSeries);
        BollingerBandsMiddleIndicator bbMiddle1H = new BollingerBandsMiddleIndicator(new SMAIndicator(close1H, 20));
        Indicator<Num> deviation1H = new StandardDeviationIndicator(close1H, 20);
        BollingerBandsUpperIndicator bbUpper1H = new BollingerBandsUpperIndicator(bbMiddle1H, deviation1H, oneHSeries.numOf(2));
        BollingerBandsLowerIndicator bbLower1H = new BollingerBandsLowerIndicator(bbMiddle1H, deviation1H, oneHSeries.numOf(2));

        Num currentPrice = close1H.getValue(lastIndex1H);
        Num bbUpperLast = bbUpper1H.getValue(lastIndex1H);
        Num bbLowerLast = bbLower1H.getValue(lastIndex1H);

        // 判断突破
        boolean breakUpper = isBullish && currentPrice.isGreaterThan(bbUpperLast);
        boolean breakLower = isBearish && currentPrice.isLessThan(bbLowerLast);
        if (!breakUpper && !breakLower) {
            log.info("未检测到布林带突破，策略终止");
            return null;
        }

        BigDecimal buyPrice = new BigDecimal(currentPrice.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
        BigDecimal takeProfit = isBullish 
            ? new BigDecimal(bbUpperLast.multipliedBy(oneHSeries.numOf(1.02)).toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP)
            : new BigDecimal(bbLowerLast.multipliedBy(oneHSeries.numOf(0.98)).toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
        log.info("突破方向：{}，买入价={}，止盈价={}", isBullish ? "上轨" : "下轨", buyPrice, takeProfit);

        // 第三步：15分钟ATR设置止损
        log.info("第三步：计算15分钟ATR止损...");
        ATRIndicator atr15M = new ATRIndicator(fifteenMSeries, 14);
        Num atrValue = atr15M.getValue(lastIndex15M);
        Num stopLossPrice = isBullish 
            ? currentPrice.minus(atrValue.multipliedBy(fifteenMSeries.numOf(2)))
            : currentPrice.plus(atrValue.multipliedBy(fifteenMSeries.numOf(2)));
        BigDecimal stopLoss = new BigDecimal(stopLossPrice.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
        log.info("ATR值={}，止损价={}", atrValue, stopLoss);

        // 计算盈亏比
        BigDecimal profit = takeProfit.subtract(buyPrice).abs();
        BigDecimal loss = buyPrice.subtract(stopLoss).abs();
        BigDecimal profitLossRatio = loss.compareTo(BigDecimal.ZERO) > 0 
            ? profit.divide(loss, CRYPTO_SCALE, RoundingMode.HALF_UP) 
            : BigDecimal.ZERO;
        log.info("盈亏比：{}", profitLossRatio);

        // 构建交易信号
        TradeStrategyDTO dto = new TradeStrategyDTO();
        dto.setSignal(isBullish ? "BUY" : "SELL");
        dto.setPrice(buyPrice);
        dto.setBuyPrice(buyPrice);
        dto.setTakeProfit(takeProfit);
        dto.setStopLoss(stopLoss);
        dto.setProfitLossRatio(profitLossRatio);
        dto.setExpiration(LocalDateTime.now().plusHours(8).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        dto.setRemark("RSI+ATR突破策略");

        log.info("交易信号生成：{}", dto);
        return dto;
    }

    @Override
    public String getStrategyName() {
        return "RsiAtrBreakoutStrategy";
    }
}
```
### 输出示例
```text
信号: BUY
当前价格: 65000.00
买入价格: 65000.00
止盈价格: 66300.00
止损价格: 64000.00
盈亏比: 1.3
有效期: 2025-02-27 20:00:00
备注: RSI+ATR突破策略
```
---

## 🌟 后续计划

1. **引入AI功能**  
   - 利用人工智能动态生成交易策略。
   - 通过机器学习分析历史数据，优化参数并集成到系统中，提升信号准确性。

2. **嵌入Python支持**  
   - 增加Python策略编写支持，用户可用Python开发交易逻辑。
   - 借助Python生态（如Pandas、TA-Lib），增强项目的扩展性和灵活性。

---

## 📂 项目结构
```text
├── src
│   └── druid.elf.tool.service.strategy
│       ├── AbstractTradeStrategy.java  # 交易策略抽象基类
│       ├── TradeStrategyDTO.java       # 交易信号数据对象
│       └── impl
│           └── RsiAtrBreakoutStrategy.java # 示例策略实现
├── start.bat                           # Windows启动脚本
├── stop.bat                            # Windows停止脚本
├── start.sh                            # macOS启动脚本
├── stop.sh                             # macOS停止脚本
```
---

## 🤝 如何贡献

我们欢迎任何形式的贡献！  
- **报告问题**：提交 [Issue](https://github.com/your-repo/issues)，请附上详细描述和复现步骤。  
- **提交代码**：发起 [Pull Request](https://github.com/your-repo/pulls)，请遵循代码规范并添加必要的注释。

---

<div align="center">
  <p>⭐ 如果你喜欢这个项目，请给我们一个 Star！⭐</p>
</div>
