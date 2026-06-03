package druid.elf.tool.service.strategy;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TradeStrategyDTO {


    private String signal; // BUY/SELL


    private BigDecimal price;


    private BigDecimal buyPrice;


    private BigDecimal takeProfit;


    private BigDecimal stopLoss;


    private BigDecimal profitLossRatio;


    private String expiration;

    private LocalDateTime signalTime;


    private String remark;
}