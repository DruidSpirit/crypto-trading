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
@Comment("交易对信息表")
@Accessors(chain = true)
public class TradingPair implements Serializable {

    @Serial
    private static final long serialVersionUID = -1234567890123456789L;

    @Id
    @Comment("主键ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    private String id;

    @Comment("交易对名称")
    @Column(nullable = false, length = 50)
    private String symbol; // 例如：BTC-USDT, ETH-BTC

    @Comment("基础货币")
    @Column(nullable = false, length = 20)
    private String baseCurrency; // 例如：BTC, ETH

    @Comment("报价货币")
    @Column(nullable = false, length = 20)
    private String quoteCurrency; // 例如：USDT, BTC

    @Comment("交易所名称")
    @Column(nullable = false, length = 20)
    private String exchange; // 字符串，例如：OKX, BINANCE, GATE_IO, BYBIT

    @Comment("交易对状态")
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE"; // 字符串：ACTIVE, INACTIVE

    @Comment("交易类型")
    @Column(length = 20)
    private String instrumentType = "SPOT"; // SPOT, FUTURES 等

    @Comment("上线时间")
    @Column
    private LocalDateTime listingDate; // 上线时间

    @Comment("创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Comment("更新时间")
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // 辅助方法：将 status 转换为 TradingPairStatus 枚举
    public TradingPairStatus getStatusEnum() {
        return TradingPairStatus.valueOf(this.status);
    }

    // 辅助方法：将 exchange 转换为 ExchangeType 枚举
    public ExchangeType getExchangeType() {
        return ExchangeType.valueOf(this.exchange);
    }
}
