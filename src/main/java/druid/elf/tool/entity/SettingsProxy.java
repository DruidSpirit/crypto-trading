package druid.elf.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;

@Data
@Entity
@Table(name = "settings_proxy")
@Comment("Proxy table")
@Accessors(chain = true)
public class SettingsProxy implements Serializable {

    private static final long serialVersionUID = -987654300898765432L;

    @Id
    @Comment("Primary key ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    @Column(name = "id")
    private String id;

    @Comment("Proxy IP address")
    @Column(name = "ip", length = 50, nullable = false)
    private String ip;

    @Comment("Proxy port")
    @Column(name = "port", nullable = false)
    private Integer port;

    @Comment("Proxy type")
    @Column(name = "type", length = 10, nullable = false)
    private String type = "SOCKS5";

    @Comment("Proxy username")
    @Column(name = "username", length = 50)
    private String username;

    @Comment("Proxy password")
    @Column(name = "password", length = 50)
    private String password;

    @Comment("Parent settings ID")
    @Column(name = "settings_id", insertable = false, updatable = false)
    private String settingsId;
}