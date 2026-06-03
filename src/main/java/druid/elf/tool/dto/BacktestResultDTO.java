package druid.elf.tool.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BacktestResultDTO {
    private String strategyName;
    private String symbol;
    private String startDate;
    private String endDate;
    private Double initialBalance;
    private Double finalBalance;
    private Double finalPortfolioValue;
    private Double totalReturn;
    private Double totalReturnPct;
    private Double maxDrawdown;
    private Double maxDrawdownPct;
    private Double sharpeRatio;
    private Integer totalTrades;
    private Integer winningTrades;
    private Integer losingTrades;
    private Double winRate;
    private Double avgWin;
    private Double avgLoss;
    private Double profitFactor;
    private List<TradeRecordDTO> tradeRecords;
    private Map<String, Object> performanceMetrics;
}