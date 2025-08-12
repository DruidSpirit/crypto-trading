package druid.elf.tool.dto;

import lombok.Data;
import java.util.Map;

@Data
public class BacktestRequestDTO {
    private String strategyName;
    private String symbol;
    private String startDate;
    private String endDate;
    private Double initialBalance = 10000.0;
    private String timeframe = "1h";
    private Map<String, Object> strategyParams;
}