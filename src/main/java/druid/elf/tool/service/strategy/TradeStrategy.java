package druid.elf.tool.service.strategy;

import druid.elf.tool.entity.TradeSignal;
import org.ta4j.core.BarSeries;
import java.util.Map;


public interface TradeStrategy {


    TradeSignal execute(Map<String,BarSeries> seriesMap,String symbol);


    String getStrategyName();
}
