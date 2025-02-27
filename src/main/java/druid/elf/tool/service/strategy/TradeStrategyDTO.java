package druid.elf.tool.service.strategy;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TradeStrategyDTO {

    // 交易信号
    private String signal; // BUY/SELL

    // 当前价格
    private BigDecimal price; // 当前价格，使用BigDecimal表示最新价格

    // 买入价格
    private BigDecimal buyPrice; // 建议买入价格

    // 止盈价格
    private BigDecimal takeProfit; // 止盈价格

    // 止损价格
    private BigDecimal stopLoss; // 止损价格

    //  盈亏比
    private BigDecimal profitLossRatio;

    // 计划有效期
    private String expiration; // 计划有效期，例如 "2026-02-21"

    private LocalDateTime signalTime; // 信号生成时间

    // 备注
    private String remark;
}