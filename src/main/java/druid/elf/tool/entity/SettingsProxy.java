package druid.elf.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;

@Data
@Entity
@Table(name = "settings_proxy") // 修改表名为 settings_proxy
@Comment("代理表")
@Accessors(chain = true)
public class SettingsProxy implements Serializable {

    private static final long serialVersionUID = -987654300898765432L;

    @Id
    @Comment("主键ID")
    @GeneratedValue(generator = "snowFlake")
    @GenericGenerator(name = "snowFlake", strategy = "druid.elf.tool.util.SnowIdGenerator")
    @Column(name = "id")
    private String id;

    @Comment("代理IP地址")
    @Column(name = "ip", length = 50, nullable = false)
    private String ip;

    @Comment("代理端口")
    @Column(name = "port", nullable = false)
    private Integer port;

    @Comment("代理类型")
    @Column(name = "type", length = 10, nullable = false)
    private String type = "SOCKS5"; // 默认值为"SOCKS5"，可选"HTTP"、"HTTPS"

    @Comment("代理账号")
    @Column(name = "username", length = 50)
    private String username;

    @Comment("代理密码")
    @Column(name = "password", length = 50)
    private String password;

    @Comment("所属设置ID")
    @Column(name = "settings_id", insertable = false, updatable = false)
    private String settingsId;
}