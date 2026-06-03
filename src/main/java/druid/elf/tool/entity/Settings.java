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
@Comment("Settings table")
@Accessors(chain = true)
public class Settings implements Serializable {

    @Serial
    private static final long serialVersionUID = -1234567890123456789L;

    @Id
    @Comment("Primary key ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    @Column(name = "id")
    private String id;

    @Comment("Crypto fetch mode")
    @Column(name = "crypto_mode", length = 10, nullable = false)
    private String cryptoMode = "custom";

    @Comment("Data fetch frequency (minutes)")
    @Column(name = "fetch_frequency")
    private Integer fetchFrequency;

    @Comment("Crypto symbol list")
    @ElementCollection
    @CollectionTable(name = "settings_crypto_symbols", joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "crypto_symbol")
    private List<String> cryptoSymbols = new ArrayList<>();

    @Comment("Exchange type settings")
    @ElementCollection
    @CollectionTable(name = "settings_exchange_types", joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "exchange_type")
    private List<String> exchangeTypes = new ArrayList<>();

    @Comment("Associated proxy list")
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "settings_id")
    private List<SettingsProxy> proxies = new ArrayList<>();
}