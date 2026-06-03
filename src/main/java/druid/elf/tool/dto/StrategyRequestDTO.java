package druid.elf.tool.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StrategyRequestDTO {
    private String symbol;
    
    @JsonProperty("strategyName")
    private String strategyName;
    
    @JsonProperty("klineData")
    private Map<String, List<KlineDataDTO>> klineData;
}