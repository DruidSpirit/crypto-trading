package druid.elf.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "settings")
@Comment("设置表")
@Accessors(chain = true)
public class Settings implements Serializable {

    @Serial
    private static final long serialVersionUID = -1234567890123456789L;

    @Id
    @Comment("主键ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    @Column(name = "id")
    private String id;

    @Comment("加密货币抓取模式")
    @Column(name = "crypto_mode", length = 10, nullable = false)
    private String cryptoMode = "custom"; // 默认值为"custom"，可选"all"

    @Comment("数据抓取频率（分钟）")
    @Column(name = "fetch_frequency")
    private Integer fetchFrequency;

    @Comment("加密货币代号列表")
    @ElementCollection
    @CollectionTable(name = "settings_crypto_symbols", joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "crypto_symbol")
    private List<String> cryptoSymbols = new ArrayList<>();

    @Comment("交易所类型设置")
    @ElementCollection
    @CollectionTable(name = "settings_exchange_types", joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "exchange_type")
    private List<String> exchangeTypes = new ArrayList<>();

    @Comment("关联的代理列表")
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "settings_id")
    private List<SettingsProxy> proxies = new ArrayList<>();
}