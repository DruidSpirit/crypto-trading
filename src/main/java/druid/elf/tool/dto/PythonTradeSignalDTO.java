package druid.elf.tool.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PythonTradeSignalDTO {
    private String signal;
    private BigDecimal price;
    
    @JsonProperty("buy_price")
    private BigDecimal buyPrice;
    
    @JsonProperty("take_profit")
    private BigDecimal takeProfit;
    
    @JsonProperty("stop_loss")
    private BigDecimal stopLoss;
    
    @JsonProperty("profit_loss_ratio")
    private BigDecimal profitLossRatio;
    
    private String expiration;
    
    @JsonProperty("signal_time")
    private LocalDateTime signalTime;
    
    private String remark;
}