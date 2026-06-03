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
@Comment("Trading signal table")
@Accessors(chain = true)
public class TradeSignal implements Serializable {

    @Serial
    private static final long serialVersionUID = -4023589405965629556L;

    @Id
    @Comment("Primary key ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    private String id;

    @Comment("Trading pair")
    @Column(nullable = false, length = 20)
    private String symbol;

    @Comment("Trading signal")
    @Column(nullable = false, length = 10)
    private String signal; // BUY/SELL

    @Comment("Strategy")
    @Column(length = 50)
    private String strategy;

    @Comment("Exchange name")
    @Column(nullable = false, length = 30)
    private String exchange;

    @Comment("Current price")
    @Column(nullable = false, precision = 38, scale = 8)
    private BigDecimal price;

    @Comment("Buy price")
    @Column(precision = 38, scale = 8)
    private BigDecimal buyPrice;

    @Comment("Take profit price")
    @Column(precision = 38, scale = 8)
    private BigDecimal takeProfit;

    @Comment("Stop loss price")
    @Column(precision = 38, scale = 8)
    private BigDecimal stopLoss;

    @Comment("Profit/loss ratio")
    @Column(precision = 38, scale = 2)
    private BigDecimal profitLossRatio;

    @Comment("Signal generation time")
    @Column(nullable = false)
    private LocalDateTime signalTime;

    @Comment("Expiration time")
    @Column(length = 20)
    private String expiration;

    @Comment("Remark")
    @Column(length = 200)
    private String remark;

    public ExchangeType getExchangeType() {
        return ExchangeType.valueOf(this.exchange);
    }
}