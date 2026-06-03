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
        log.info("Starting Elder triple filter swing trading strategy...");

        try {

            Map<String, List<KlineDataDTO>> klineData = convertBarSeriesToKlineData(seriesMap);


            StrategyRequestDTO request = new StrategyRequestDTO();
            request.setSymbol("BTCUSDT");
            request.setStrategyName("ElderSwingStrategy");
            request.setKlineData(klineData);


            PythonTradeSignalDTO pythonResult = pythonStrategyClient.executeStrategy(request);

            if (pythonResult == null) {
                log.info("Elder Swing Strategy did not return a trading signal");
                return null;
            }


            return convertPythonResultToTradeStrategyDTO(pythonResult);

        } catch (Exception e) {
            log.error("Elder Swing Strategy adapter execution failed", e);
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
                

                long timeInterval = getTimeIntervalByPeriod(interval);
                long currentTime = System.currentTimeMillis();
                klineDto.setOpenTime(currentTime - (series.getBarCount() - i) * timeInterval);
                klineDto.setCloseTime(klineDto.getOpenTime() + timeInterval);
                

                klineDto.setOpenPrice(BigDecimal.valueOf(bar.getOpenPrice().doubleValue()));
                klineDto.setHighPrice(BigDecimal.valueOf(bar.getHighPrice().doubleValue()));
                klineDto.setLowPrice(BigDecimal.valueOf(bar.getLowPrice().doubleValue()));
                klineDto.setClosePrice(BigDecimal.valueOf(bar.getClosePrice().doubleValue()));
                klineDto.setVolume(BigDecimal.valueOf(bar.getVolume().doubleValue()));
                

                klineDto.setQuoteAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue()));
                klineDto.setNumberOfTrades(100);
                klineDto.setTakerBuyBaseAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.6));
                klineDto.setTakerBuyQuoteAssetVolume(BigDecimal.valueOf(bar.getVolume().doubleValue() * 0.6));

                klineList.add(klineDto);
            }

            klineData.put(interval, klineList);
            log.info("Converting {} interval K-line data, {} bars total", interval, klineList.size());
        }

        return klineData;
    }

    private long getTimeIntervalByPeriod(String interval) {
        switch (interval) {
            case "_1W":
                return 7 * 24 * 60 * 60 * 1000L;
            case "_1D":
                return 24 * 60 * 60 * 1000L;
            case "_4H":
                return 4 * 60 * 60 * 1000L;
            case "_1H":
                return 60 * 60 * 1000L;
            case "_30M":
                return 30 * 60 * 1000L;
            case "_15M":
                return 15 * 60 * 1000L;
            case "_5M":
                return 5 * 60 * 1000L;
            default:
                return 15 * 60 * 1000L;
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

        log.info("Elder Swing Strategy result: {}", dto);
        return dto;
    }

    @Override
    public String getStrategyName() {
        return "Elder triple filter swing trading strategy";
    }
}