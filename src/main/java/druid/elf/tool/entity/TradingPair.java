package druid.elf.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import druid.elf.tool.enums.TradingPairStatus;
import druid.elf.tool.enums.ExchangeType;

@Data
@Entity
@Table(name = "trading_pairs")
@Comment("Trading pair information table")
@Accessors(chain = true)
public class TradingPair implements Serializable {

    @Serial
    private static final long serialVersionUID = -1234567890123456789L;

    @Id
    @Comment("Primary key ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    private String id;

    @Comment("Trading pair name")
    @Column(nullable = false, length = 50)
    private String symbol;

    @Comment("Base currency")
    @Column(nullable = false, length = 20)
    private String baseCurrency;

    @Comment("Quote currency")
    @Column(nullable = false, length = 20)
    private String quoteCurrency;

    @Comment("Exchange name")
    @Column(nullable = false, length = 20)
    private String exchange;

    @Comment("Trading pair status")
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Comment("Instrument type")
    @Column(length = 20)
    private String instrumentType = "SPOT";

    @Comment("Listing time")
    @Column
    private LocalDateTime listingDate;

    @Comment("Created time")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Comment("Updated time")
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();


    public TradingPairStatus getStatusEnum() {
        return TradingPairStatus.valueOf(this.status);
    }


    public ExchangeType getExchangeType() {
        return ExchangeType.valueOf(this.exchange);
    }
}
