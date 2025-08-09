package druid.elf.tool.service.strategy.impl;

import druid.elf.tool.dto.KlineDataDTO;
import druid.elf.tool.dto.PythonTradeSignalDTO;
import druid.elf.tool.dto.StrategyRequestDTO;
import druid.elf.tool.enums.KlineInterval;
import druid.elf.tool.service.client.PythonStrategyClientRestTemplate;
import druid.elf.tool.service.strategy.AbstractTradeStrategy;
import druid.elf.tool.service.strategy.TradeStrategyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "strategy.enabled.python-macd", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PythonMacdCrossStrategy extends AbstractTradeStrategy {

    private final PythonStrategyClientRestTemplate pythonStrategyClient;

    @Override
    protected TradeStrategyDTO doHandle(Map<String, BarSeries> seriesMap) {
        log.info("开始执行Python MACD策略...");

        // 检查Python服务健康状态
        if (!pythonStrategyClient.isHealthy()) {
            log.error("Python策略服务不可用");
            return null;
        }

        // 转换K线数据为DTO
        StrategyRequestDTO request = new StrategyRequestDTO();
        request.setSymbol("BTCUSDT"); // 默认交易对
        request.setStrategyName("SimpleMacdCrossStrategy"); // 设置策略名称
        request.setKlineData(convertBarSeriesToDTO(seriesMap));

        try {
            // 调用Python策略服务
            PythonTradeSignalDTO pythonResponse = pythonStrategyClient.executeStrategy(request);
            
            if (pythonResponse == null) {
                log.info("Python策略服务未生成交易信号");
                return null;
            }

            // 转换Python响应为Java DTO
            TradeStrategyDTO dto = new TradeStrategyDTO();
            dto.setSignal(pythonResponse.getSignal());
            dto.setPrice(pythonResponse.getPrice());
            dto.setBuyPrice(pythonResponse.getBuyPrice());
            dto.setTakeProfit(pythonResponse.getTakeProfit());
            dto.setStopLoss(pythonResponse.getStopLoss());
            dto.setProfitLossRatio(pythonResponse.getProfitLossRatio());
            dto.setExpiration(pythonResponse.getExpiration());
            dto.setRemark(pythonResponse.getRemark() + " (via Python)");
            dto.setSignalTime(LocalDateTime.now());

            log.info("Python策略执行成功，交易信号：{}", dto);
            return dto;
            
        } catch (Exception e) {
            log.error("Python策略执行失败", e);
            return null;
        }
    }

    private Map<String, List<KlineDataDTO>> convertBarSeriesToDTO(Map<String, BarSeries> seriesMap) {
        Map<String, List<KlineDataDTO>> klineDataMap = new HashMap<>();
        
        for (Map.Entry<String, BarSeries> entry : seriesMap.entrySet()) {
            String interval = entry.getKey();
            BarSeries series = entry.getValue();
            
            List<KlineDataDTO> klineDataList = new ArrayList<>();
            for (int i = 0; i < series.getBarCount(); i++) {
                Bar bar = series.getBar(i);
                KlineDataDTO klineData = new KlineDataDTO();
                
                // 设置时间戳（毫秒）- 使用当前时间作为基准生成时间戳  
                long currentTime = System.currentTimeMillis();
                long timeInterval = 15 * 60 * 1000; // 15分钟间隔
                klineData.setOpenTime(currentTime - (series.getBarCount() - i) * timeInterval);
                klineData.setCloseTime(klineData.getOpenTime() + timeInterval);
                
                // 设置价格数据
                klineData.setOpenPrice(java.math.BigDecimal.valueOf(bar.getOpenPrice().doubleValue()));
                klineData.setHighPrice(java.math.BigDecimal.valueOf(bar.getHighPrice().doubleValue()));
                klineData.setLowPrice(java.math.BigDecimal.valueOf(bar.getLowPrice().doubleValue()));
                klineData.setClosePrice(java.math.BigDecimal.valueOf(bar.getClosePrice().doubleValue()));
                klineData.setVolume(java.math.BigDecimal.valueOf(bar.getVolume().doubleValue()));
                
                // 设置默认值
                klineData.setQuoteAssetVolume(java.math.BigDecimal.valueOf(bar.getVolume().doubleValue()));
                klineData.setNumberOfTrades(0);
                klineData.setTakerBuyBaseAssetVolume(java.math.BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.5));
                klineData.setTakerBuyQuoteAssetVolume(java.math.BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.5));
                
                klineDataList.add(klineData);
            }
            
            klineDataMap.put(interval, klineDataList);
        }
        
        return klineDataMap;
    }

    @Override
    public String getStrategyName() {
        return "PythonMacdCrossStrategy";
    }
}