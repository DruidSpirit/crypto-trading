package druid.elf.tool.service.strategy;

import druid.elf.tool.entity.TradeSignal;
import org.ta4j.core.BarSeries;
import java.util.Map;

/**
 * 交易策略
 */
public interface TradeStrategy {

    /**
     * 执行策略
     */
    TradeSignal execute(Map<String,BarSeries> seriesMap,String symbol);

    /**
     * 策略名称
     */
    String getStrategyName();
}
