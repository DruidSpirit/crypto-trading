package druid.elf.tool.entity;

import druid.elf.tool.enums.ExchangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Comment("交易信号表")
@Accessors(chain = true)
public class TradeSignal implements Serializable {

    @Serial
    private static final long serialVersionUID = -4023589405965629556L;

    @Id
    @Comment("主键ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    private String id;

    @Comment("交易对")
    @Column(nullable = false, length = 20)
    private String symbol; // 例如：BTCUSDT, ETHUSDT

    @Comment("交易信号")
    @Column(nullable = false, length = 10)
    private String signal; // BUY/SELL

    @Comment("策略")
    @Column(length = 50)
    private String strategy;

    @Comment("交易所名称")
    @Column(nullable = false, length = 30)
    private String exchange; // 字符串，例如：OKX, BINANCE

    @Comment("当前价格")
    @Column(nullable = false, precision = 38, scale = 8)
    private BigDecimal price; // 当前价格，支持 8 位小数

    @Comment("买入价格")
    @Column(precision = 38, scale = 8)
    private BigDecimal buyPrice; // 建议买入价格，支持 8 位小数

    @Comment("止盈价格")
    @Column(precision = 38, scale = 8)
    private BigDecimal takeProfit; // 止盈价格，支持 8 位小数

    @Comment("止损价格")
    @Column(precision = 38, scale = 8)
    private BigDecimal stopLoss; // 止损价格，支持 8 位小数

    @Comment("盈亏比")
    @Column(precision = 38, scale = 2)
    private BigDecimal profitLossRatio; // 盈亏比，支持 1 位小数，例如 2.5 表示 2.5:1

    @Comment("信号生成时间")
    @Column(nullable = false)
    private LocalDateTime signalTime;

    @Comment("计划有效期")
    @Column(length = 20)
    private String expiration; // 例如 "2026-02-21"

    @Comment("备注")
    @Column(length = 200)
    private String remark;

    public ExchangeType getExchangeType() {
        return ExchangeType.valueOf(this.exchange);
    }
}