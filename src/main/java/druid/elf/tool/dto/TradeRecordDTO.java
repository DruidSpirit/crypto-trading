package druid.elf.tool.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TradeRecordDTO {
    private LocalDateTime timestamp;
    private String action; // BUY, SELL
    private Double price;
    private Double quantity;
    private Double balance;
    private Double portfolioValue;
    private Double signalStrength;
    private String reason;
}