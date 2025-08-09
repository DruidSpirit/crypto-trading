package druid.elf.tool.service.strategy.impl;

import druid.elf.tool.dto.KlineDataDTO;
import druid.elf.tool.dto.PythonTradeSignalDTO;
import druid.elf.tool.dto.StrategyRequestDTO;
import druid.elf.tool.service.client.PythonStrategyClientRestTemplate;
import druid.elf.tool.service.strategy.AbstractTradeStrategy;
import druid.elf.tool.service.strategy.TradeStrategyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python策略适配器
 * 将Java的BarSeries数据转换为Python服务所需的格式，并调用Python策略服务
 */
@Component
@Slf4j
public class PythonStrategyAdapter extends AbstractTradeStrategy {

    private final PythonStrategyClientRestTemplate pythonStrategyClient;
    private final String strategyName;

    // 使用@Autowired注入，默认策略名称
    @Autowired
    public PythonStrategyAdapter(PythonStrategyClientRestTemplate pythonStrategyClient) {
        this.pythonStrategyClient = pythonStrategyClient;
        this.strategyName = "SimpleMacdCrossStrategy";
    }

    @Override
    protected TradeStrategyDTO doHandle(Map<String, BarSeries> seriesMap) {
        return executeStrategy("BTCUSDT", seriesMap);
    }

    public TradeStrategyDTO executeStrategy(String symbol, Map<String, BarSeries> seriesMap) {
        log.info("开始执行Python策略适配器，策略名称：{}，交易对：{}", strategyName, symbol);

        try {
            // 转换数据格式
            Map<String, List<KlineDataDTO>> klineData = convertBarSeriesToKlineData(seriesMap);

            // 构建请求
            StrategyRequestDTO request = new StrategyRequestDTO();
            request.setSymbol(symbol);
            request.setStrategyName(strategyName);
            request.setKlineData(klineData);

            // 调用Python服务
            PythonTradeSignalDTO pythonResult = pythonStrategyClient.executeStrategy(request);

            if (pythonResult == null) {
                log.info("Python策略服务未返回交易信号");
                return null;
            }

            // 转换返回结果
            return convertPythonResultToTradeStrategyDTO(pythonResult);

        } catch (Exception e) {
            log.error("Python策略适配器执行失败", e);
            return null;
        }
    }

    private Map<String, List<KlineDataDTO>> convertBarSeriesToKlineData(Map<String, BarSeries> seriesMap) {
        Map<String, List<KlineDataDTO>> klineData = new HashMap<>();

        for (Map.Entry<String, BarSeries> entry : seriesMap.entrySet()) {
            String interval = entry.getKey();
            BarSeries series = entry.getValue();

            List<KlineDataDTO> klineList = new ArrayList<>();
            for (int i = 0; i < series.getBarCount(); i++) {
                Bar bar = series.getBar(i);
                KlineDataDTO klineDto = new KlineDataDTO();
                
                // 设置时间戳（毫秒）- 使用当前时间作为基准生成时间戳
                long currentTime = System.currentTimeMillis();
                long timeInterval = 15 * 60 * 1000; // 15分钟间隔
                klineDto.setOpenTime(currentTime - (series.getBarCount() - i) * timeInterval);
                klineDto.setCloseTime(klineDto.getOpenTime() + timeInterval);
                
                // 设置价格数据
                klineDto.setOpenPrice(BigDecimal.valueOf(bar.getOpenPrice().doubleValue()));
                klineDto.setHighPrice(BigDecimal.valueOf(bar.getHighPrice().doubleValue()));
                klineDto.setLowPrice(BigDecimal.valueOf(bar.getLowPrice().doubleValue()));
                klineDto.setClosePrice(BigDecimal.valueOf(bar.getClosePrice().doubleValue()));
                klineDto.setVolume(BigDecimal.valueOf(bar.getVolume().doubleValue()));
                
                // 设置默认值（如果系列中没有这些数据）
                klineDto.setQuoteAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue()));
                klineDto.setNumberOfTrades(0);
                klineDto.setTakerBuyBaseAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.5));
                klineDto.setTakerBuyQuoteAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.5));

                klineList.add(klineDto);
            }

            klineData.put(interval, klineList);
            log.info("转换{}间隔的K线数据，共{}条", interval, klineList.size());
        }

        return klineData;
    }

    private TradeStrategyDTO convertPythonResultToTradeStrategyDTO(PythonTradeSignalDTO pythonResult) {
        TradeStrategyDTO dto = new TradeStrategyDTO();
        dto.setSignal(pythonResult.getSignal());
        dto.setPrice(pythonResult.getPrice());
        dto.setBuyPrice(pythonResult.getBuyPrice());
        dto.setTakeProfit(pythonResult.getTakeProfit());
        dto.setStopLoss(pythonResult.getStopLoss());
        dto.setProfitLossRatio(pythonResult.getProfitLossRatio());
        dto.setExpiration(pythonResult.getExpiration());
        dto.setRemark(pythonResult.getRemark());

        log.info("转换Python结果为TradeStrategyDTO：{}", dto);
        return dto;
    }

    @Override
    public String getStrategyName() {
        return "PythonStrategyAdapter-" + strategyName;
    }
}