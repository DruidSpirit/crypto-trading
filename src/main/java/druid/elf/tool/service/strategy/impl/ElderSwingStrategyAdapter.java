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
 * 埃尔德三重过滤波段交易策略适配器（持仓3-10天）
 */
@Component
@Slf4j
public class ElderSwingStrategyAdapter extends AbstractTradeStrategy {

    private final PythonStrategyClientRestTemplate pythonStrategyClient;

    @Autowired
    public ElderSwingStrategyAdapter(PythonStrategyClientRestTemplate pythonStrategyClient) {
        this.pythonStrategyClient = pythonStrategyClient;
    }

    @Override
    protected TradeStrategyDTO doHandle(Map<String, BarSeries> seriesMap) {
        log.info("开始执行埃尔德三重过滤波段交易策略...");

        try {
            // 转换数据格式 - 波段策略需要周线、日线、4小时线
            Map<String, List<KlineDataDTO>> klineData = convertBarSeriesToKlineData(seriesMap);

            // 构建请求
            StrategyRequestDTO request = new StrategyRequestDTO();
            request.setSymbol("BTCUSDT");
            request.setStrategyName("ElderSwingStrategy");
            request.setKlineData(klineData);

            // 调用Python服务
            PythonTradeSignalDTO pythonResult = pythonStrategyClient.executeStrategy(request);

            if (pythonResult == null) {
                log.info("埃尔德波段策略未返回交易信号");
                return null;
            }

            // 转换返回结果
            return convertPythonResultToTradeStrategyDTO(pythonResult);

        } catch (Exception e) {
            log.error("埃尔德波段策略适配器执行失败", e);
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
                
                // 根据不同时间周期设置不同的时间间隔
                long timeInterval = getTimeIntervalByPeriod(interval);
                long currentTime = System.currentTimeMillis();
                klineDto.setOpenTime(currentTime - (series.getBarCount() - i) * timeInterval);
                klineDto.setCloseTime(klineDto.getOpenTime() + timeInterval);
                
                // 设置价格数据
                klineDto.setOpenPrice(BigDecimal.valueOf(bar.getOpenPrice().doubleValue()));
                klineDto.setHighPrice(BigDecimal.valueOf(bar.getHighPrice().doubleValue()));
                klineDto.setLowPrice(BigDecimal.valueOf(bar.getLowPrice().doubleValue()));
                klineDto.setClosePrice(BigDecimal.valueOf(bar.getClosePrice().doubleValue()));
                klineDto.setVolume(BigDecimal.valueOf(bar.getVolume().doubleValue()));
                
                // 设置默认值
                klineDto.setQuoteAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue()));
                klineDto.setNumberOfTrades(100);
                klineDto.setTakerBuyBaseAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.6));
                klineDto.setTakerBuyQuoteAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.6));

                klineList.add(klineDto);
            }

            klineData.put(interval, klineList);
            log.info("转换{}间隔的K线数据，共{}条", interval, klineList.size());
        }

        return klineData;
    }

    private long getTimeIntervalByPeriod(String interval) {
        switch (interval) {
            case "_1W":
                return 7 * 24 * 60 * 60 * 1000L; // 1周
            case "_1D":
                return 24 * 60 * 60 * 1000L; // 1天
            case "_4H":
                return 4 * 60 * 60 * 1000L; // 4小时
            case "_1H":
                return 60 * 60 * 1000L; // 1小时
            case "_30M":
                return 30 * 60 * 1000L; // 30分钟
            case "_15M":
                return 15 * 60 * 1000L; // 15分钟
            case "_5M":
                return 5 * 60 * 1000L; // 5分钟
            default:
                return 15 * 60 * 1000L; // 默认15分钟
        }
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

        log.info("埃尔德波段策略结果：{}", dto);
        return dto;
    }

    @Override
    public String getStrategyName() {
        return "埃尔德三重过滤波段交易策略";
    }
}