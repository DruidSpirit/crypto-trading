package druid.elf.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_files")
@Data
public class StrategyFile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String filename;
    
    @Column(name = "original_filename")
    private String originalFilename;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "display_name", length = 200)
    private String displayName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrategyStatus status = StrategyStatus.INACTIVE;
    
    @Column(name = "upload_time", nullable = false)
    private LocalDateTime uploadTime;
    
    @Column(name = "last_update_time")
    private LocalDateTime lastUpdateTime;
    
    public enum StrategyStatus {
        ACTIVE,     // 已激活
        INACTIVE,   // 未激活  
        UPDATING,   // 更新中
        ERROR       // 错误
    }
    
    @PrePersist
    protected void onCreate() {
        uploadTime = LocalDateTime.now();
        lastUpdateTime = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdateTime = LocalDateTime.now();
    }
}