package druid.elf.tool.service.strategy.impl;

import druid.elf.tool.enums.KlineInterval;
import druid.elf.tool.service.strategy.AbstractTradeStrategy;
import druid.elf.tool.service.strategy.TradeStrategyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
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
 * 简单MACD金叉死叉买入卖出短期波动策略实现类
 * - 4小时MACD判断趋势（即将金叉或刚金叉不久）
 * - 1小时布林带设置止盈
 * - 15分钟布林带直接使用上下轨确定买入价格，ATR设置止损
 * - 新增逻辑：做多时若买入价高于当前价用当前价，做空时若买入价低于当前价用当前价
 */
@Component
@Slf4j
public class SimpleMacdCrossStrategy extends AbstractTradeStrategy {

    // 加密货币价格精度：8位小数
    private static final int CRYPTO_SCALE = 8;

    @Override
    protected TradeStrategyDTO doHandle(Map<String, BarSeries> seriesMap) {
        log.info("开始执行简单MACD金叉死叉短期波动策略...");

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

        // 第一步：4小时MACD判断趋势（即将金叉或刚金叉不久）
        log.info("第一步：计算4小时MACD趋势...");
        Indicator<Num> close4H = new ClosePriceIndicator(fourHSeries);
        MACDIndicator macd4H = new MACDIndicator(close4H, 12, 26);
        Indicator<Num> signalLine4H = new EMAIndicator(macd4H, 9);
        // 添加MACD柱指标 (MACD线 - 信号线)
        Indicator<Num> histogram4H = new Indicator<Num>() {
            @Override
            public Num getValue(int index) {
                return macd4H.getValue(index).minus(signalLine4H.getValue(index));
            }
            @Override
            public BarSeries getBarSeries() {
                return fourHSeries;
            }
            @Override
            public Num numOf(Number number) {
                return fourHSeries.numOf(number);
            }
        };

        Num macdLast = macd4H.getValue(lastIndex4H);
        Num signalLast = signalLine4H.getValue(lastIndex4H);
        Num macdPrev = macd4H.getValue(lastIndex4H - 1);
        Num signalPrev = signalLine4H.getValue(lastIndex4H - 1);
        Num histLast = histogram4H.getValue(lastIndex4H);
        Num histPrev = histogram4H.getValue(lastIndex4H - 1);

        // 获取当前价格并设定动态阈值：当前价格的 1%
        Num currentPrice = fourHSeries.getBar(lastIndex4H).getClosePrice();
        Num threshold = currentPrice.multipliedBy(fourHSeries.numOf(0.01)); // 1%
        log.info("当前价格：{}，动态阈值：{}", currentPrice, threshold);

        // 计算MACD线与信号线的差值
        Num diffLast = macdLast.minus(signalLast);  // 当前差值
        Num diffPrev = macdPrev.minus(signalPrev);  // 前一周期差值

        // 判断是否即将金叉或刚金叉不久
        boolean isBullish = false;
        boolean isBearish = false;

        // 条件1：即将金叉（MACD线接近信号线且趋势向上，且MACD线在柱线下方）
        boolean approachingGoldenCross = diffPrev.isLessThan(fourHSeries.numOf(0))  // 前周期MACD低于信号线
                && diffLast.abs().isLessThan(threshold)  // 当前差距小于动态阈值 (1%)
                && diffLast.isGreaterThan(diffPrev)      // 差距缩小，趋势向上
                && macdLast.isLessThan(histLast);        // MACD线在柱线下方

        // 条件2：刚金叉不久（检查最近3个周期内是否有金叉，且MACD线在柱线下方）
        boolean recentGoldenCross = false;
        for (int i = 0; i < Math.min(3, lastIndex4H); i++) {
            Num macd = macd4H.getValue(lastIndex4H - i);
            Num signal = signalLine4H.getValue(lastIndex4H - i);
            Num macdBefore = macd4H.getValue(lastIndex4H - i - 1);
            Num signalBefore = signalLine4H.getValue(lastIndex4H - i - 1);
            Num hist = histogram4H.getValue(lastIndex4H - i);
            if (macdBefore.isLessThan(signalBefore)
                    && macd.isGreaterThan(signal)
                    && macd.isLessThan(hist)) {  // 添加MACD线在柱线下方的条件
                recentGoldenCross = true;
                break;
            }
        }

        isBullish = approachingGoldenCross || recentGoldenCross;
        // 判断死叉（MACD线在柱线上方）
        isBearish = diffPrev.isGreaterThan(fourHSeries.numOf(0))  // 前周期MACD高于信号线
                && diffLast.abs().isLessThan(threshold)  // 当前差距小于动态阈值 (1%)
                && diffLast.isLessThan(diffPrev)         // 差距缩小，趋势向下
                && macdLast.isGreaterThan(histLast);     // MACD线在柱线上方

        log.info("MACD值：前值={} 当前值={}，信号线：前值={} 当前值={}，柱线：前值={} 当前值={}",
                macdPrev, macdLast, signalPrev, signalLast, histPrev, histLast);
        log.info("差值：前值={} 当前值={}，看多={}，看空={}", diffPrev, diffLast, isBullish, isBearish);

        if (!isBullish && !isBearish) {
            log.info("MACD趋势不明确，策略终止");
            return null;
        }

        // 第二步：1小时布林带设置止盈
        log.info("第二步：计算1小时布林带止盈目标...");
        Indicator<Num> close1H = new ClosePriceIndicator(oneHSeries);
        BollingerBandsMiddleIndicator bbMiddle1H = new BollingerBandsMiddleIndicator(new SMAIndicator(close1H, 20));
        Indicator<Num> deviation1H = new StandardDeviationIndicator(close1H, 20);
        BollingerBandsUpperIndicator bbUpper1H = new BollingerBandsUpperIndicator(bbMiddle1H, deviation1H, oneHSeries.numOf(2));
        BollingerBandsLowerIndicator bbLower1H = new BollingerBandsLowerIndicator(bbMiddle1H, deviation1H, oneHSeries.numOf(2));

        Num takeProfitPrice = isBullish ? bbUpper1H.getValue(lastIndex1H) : bbLower1H.getValue(lastIndex1H);
        BigDecimal takeProfit = new BigDecimal(takeProfitPrice.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
        log.info("止盈价格：{}", takeProfit);

        // 第三步：15分钟布林带确定买入价格和止损
        log.info("第三步：计算15分钟布林带买入价格和止损...");
        Indicator<Num> close15M = new ClosePriceIndicator(fifteenMSeries);
        BollingerBandsMiddleIndicator bbMiddle15M = new BollingerBandsMiddleIndicator(new SMAIndicator(close15M, 20));
        Indicator<Num> deviation15M = new StandardDeviationIndicator(close15M, 20);
        BollingerBandsUpperIndicator bbUpper15M = new BollingerBandsUpperIndicator(bbMiddle15M, deviation15M, fifteenMSeries.numOf(2));
        BollingerBandsLowerIndicator bbLower15M = new BollingerBandsLowerIndicator(bbMiddle15M, deviation15M, fifteenMSeries.numOf(2));

        Num bbUpperLast15M = bbUpper15M.getValue(lastIndex15M);
        Num bbLowerLast15M = bbLower15M.getValue(lastIndex15M);

        // 直接根据布林带上下轨确定初始买入价格
        Num buyPriceNumInitial = isBullish ? bbLowerLast15M : bbUpperLast15M;

        // 获取当前价格（基于4小时K线的最新收盘价）
        BigDecimal currentPriceBD = new BigDecimal(currentPrice.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);

        // 调整买入价格逻辑
        Num buyPriceNum;
        if (isBullish) {
            // 做多：如果初始买入价高于当前价格，使用当前价格
            buyPriceNum = buyPriceNumInitial.isGreaterThan(currentPrice) ? currentPrice : buyPriceNumInitial;
            log.info("做多调整：初始买入价={}，当前价={}，最终买入价={}",
                    buyPriceNumInitial, currentPrice, buyPriceNum);
        } else {
            // 做空：如果初始买入价低于当前价格，使用当前价格
            buyPriceNum = buyPriceNumInitial.isLessThan(currentPrice) ? currentPrice : buyPriceNumInitial;
            log.info("做空调整：初始买入价={}，当前价={}，最终买入价={}",
                    buyPriceNumInitial, currentPrice, buyPriceNum);
        }

        BigDecimal buyPrice = new BigDecimal(buyPriceNum.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
        log.info("15分钟布林带上轨={} 下轨={}，调整后买入价格={}", bbUpperLast15M, bbLowerLast15M, buyPrice);

        // 计算止损
        ATRIndicator atr15M = new ATRIndicator(fifteenMSeries, 14);
        Num atrValue = atr15M.getValue(lastIndex15M);
        BigDecimal atr = new BigDecimal(atrValue.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
        Num stopLossPrice;

        if (isBullish) {
            Num prevLow = fifteenMSeries.getBar(lastIndex15M - 1).getLowPrice();
            BigDecimal prevLowPrice = new BigDecimal(prevLow.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
            if (prevLowPrice.compareTo(buyPrice) > 0) {
                stopLossPrice = buyPriceNum.minus(atrValue.multipliedBy(fifteenMSeries.numOf(1.5)));
                log.info("做多止损调整：前低点={}高于买入价={}，使用买入价-1.5*ATR计算止损={}",
                        prevLowPrice, buyPrice, stopLossPrice);
            } else {
                stopLossPrice = prevLow.minus(atrValue.multipliedBy(fifteenMSeries.numOf(1.5)));
                log.info("做多止损：基于前低点={}，止损价格={}", prevLowPrice, stopLossPrice);
            }
        } else {
            Num prevHigh = fifteenMSeries.getBar(lastIndex15M - 1).getHighPrice();
            BigDecimal prevHighPrice = new BigDecimal(prevHigh.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
            if (prevHighPrice.compareTo(buyPrice) < 0) {
                stopLossPrice = buyPriceNum.plus(atrValue.multipliedBy(fifteenMSeries.numOf(1.5)));
                log.info("做空止损调整：前高点={}低于买入价={}，使用买入价+1.5*ATR计算止损={}",
                        prevHighPrice, buyPrice, stopLossPrice);
            } else {
                stopLossPrice = prevHigh.plus(atrValue.multipliedBy(fifteenMSeries.numOf(1.5)));
                log.info("做空止损：基于前高点={}，止损价格={}", prevHighPrice, stopLossPrice);
            }
        }
        BigDecimal stopLoss = new BigDecimal(stopLossPrice.toString()).setScale(CRYPTO_SCALE, RoundingMode.HALF_UP);
        log.info("ATR值：{}，止损价格：{}", atr, stopLoss);

        // 计算盈亏比
        BigDecimal profitLossRatio;
        if (isBullish) {
            BigDecimal profit = takeProfit.subtract(buyPrice);
            BigDecimal loss = buyPrice.subtract(stopLoss);
            if (loss.compareTo(BigDecimal.ZERO) > 0) {
                profitLossRatio = profit.divide(loss, CRYPTO_SCALE, RoundingMode.HALF_UP);
            } else {
                profitLossRatio = BigDecimal.ZERO; // 避免除以零的情况
            }
        } else {
            BigDecimal profit = buyPrice.subtract(takeProfit);
            BigDecimal loss = stopLoss.subtract(buyPrice);
            if (loss.compareTo(BigDecimal.ZERO) > 0) {
                profitLossRatio = profit.divide(loss, CRYPTO_SCALE, RoundingMode.HALF_UP);
            } else {
                profitLossRatio = BigDecimal.ZERO; // 避免除以零的情况
            }
        }
        log.info("盈亏比计算：买入价={}，止盈价={}，止损价={}，盈亏比={}",
                buyPrice, takeProfit, stopLoss, profitLossRatio);

        // 确保风险收益比至少为1:2
        BigDecimal profit = takeProfit.subtract(buyPrice).abs();
        BigDecimal loss = buyPrice.subtract(stopLoss).abs();
        boolean riskRewardValid = profit.divide(loss, CRYPTO_SCALE, RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(2)) >= 0;

        log.info("入场价格：{}，预期盈利：{}，预期亏损：{}，风险收益比：{}",
                buyPrice, profit, loss, profit.divide(loss, CRYPTO_SCALE, RoundingMode.HALF_UP));
        if (!riskRewardValid) {
            log.info("风险收益比不足1:2，策略终止");
            return null;
        }

        // 构建交易信号DTO
        TradeStrategyDTO dto = new TradeStrategyDTO();
        dto.setSignal(isBullish ? "BUY" : "SELL");
        dto.setPrice(currentPriceBD);
        dto.setBuyPrice(buyPrice);
        dto.setTakeProfit(takeProfit);
        dto.setStopLoss(stopLoss);
        dto.setProfitLossRatio(profitLossRatio); // 设置盈亏比

        // 设置有效期为当前时间加12小时
        LocalDateTime expirationTime = LocalDateTime.now().plusHours(12);
        String expiration = expirationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        dto.setExpiration(expiration);
        log.info("交易信号有效期设置为：{}", expiration);

        dto.setRemark("简单MACD金叉死叉短期波动策略");

        log.info("交易信号生成：{}", dto);
        return dto;
    }

    @Override
    public String getStrategyName() {
        return "SimpleMacdCrossStrategy";
    }
}