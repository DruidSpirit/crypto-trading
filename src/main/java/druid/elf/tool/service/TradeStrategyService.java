package druid.elf.tool.service;

import druid.elf.tool.service.strategy.AbstractTradeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import druid.elf.tool.entity.TradeSignal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TradeStrategyService {

    private static final Logger log = LoggerFactory.getLogger(TradeStrategyService.class);
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 生成交易信号
     */
    public List<TradeSignal> generateSignal(Map<String,BarSeries> seriesMap, String symbol) {
        Map<String, AbstractTradeStrategy> strategyBeans = applicationContext.getBeansOfType(AbstractTradeStrategy.class);

        return strategyBeans.values().stream()
                .map(strategy -> {
                    try {
                        return strategy.execute(seriesMap,symbol);
                    } catch (Exception e) {
                        log.error("策略执行异常",e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(tradeSignal -> "BUY".equals(tradeSignal.getSignal()) || "SELL".equals(tradeSignal.getSignal()) )
                .filter(tradeSignal -> tradeSignal.getPrice() != null && tradeSignal.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }
}