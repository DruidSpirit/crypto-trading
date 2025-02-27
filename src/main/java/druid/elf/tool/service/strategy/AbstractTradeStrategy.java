package druid.elf.tool.service.strategy;

import cn.hutool.core.bean.BeanUtil;
import druid.elf.tool.entity.TradeSignal;
import org.ta4j.core.BarSeries;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交易策略公共抽象类
 */
public abstract class AbstractTradeStrategy implements TradeStrategy{

    protected abstract TradeStrategyDTO doHandle(Map<String,BarSeries> seriesMap);

    @Override
    public TradeSignal execute(Map<String,BarSeries> seriesMap,String symbol) {
        TradeStrategyDTO strategy = doHandle(seriesMap);
        if (strategy== null) return null;
        TradeSignal tradeSignal = new TradeSignal();
        tradeSignal
                .setSymbol(symbol)
                .setSignal(strategy.getSignal())
                .setSignalTime(LocalDateTime.now())
                .setBuyPrice(strategy.getBuyPrice())
                .setTakeProfit(strategy.getTakeProfit())
                .setPrice(strategy.getPrice())
                .setStrategy(this.getStrategyName())
                .setExpiration(strategy.getExpiration())
                .setStopLoss(strategy.getStopLoss())
                .setRemark(strategy.getRemark())
                .setProfitLossRatio(strategy.getProfitLossRatio())
        ;
        return tradeSignal;
    }
}
