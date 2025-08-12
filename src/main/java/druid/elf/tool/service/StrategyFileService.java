package druid.elf.tool.service;

import druid.elf.tool.entity.StrategyFile;
import druid.elf.tool.repository.StrategyFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class StrategyFileService {
    
    @Autowired
    private StrategyFileRepository strategyFileRepository;
    
    @Autowired
    private PythonStrategyNotificationService pythonNotificationService;
    
    @Value("${strategy.upload.path:C:/Users/druid/PycharmProjects/crypto-trading-strategy/strategies}")
    private String uploadPath;
    
    public List<StrategyFile> getAllStrategies() {
        return strategyFileRepository.findAllByOrderByUploadTimeDesc();
    }
    
    public Optional<StrategyFile> getStrategyById(Long id) {
        return strategyFileRepository.findById(id);
    }
    
    public StrategyFile saveStrategy(StrategyFile strategyFile) {
        return strategyFileRepository.save(strategyFile);
    }
    
    public StrategyFile uploadStrategy(MultipartFile file, String description) throws IOException {
        // 验证文件
        validateFile(file);
        
        // 确保上传目录存在
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        
        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String filename = generateUniqueFilename(originalFilename);
        Path filePath = uploadDir.resolve(filename);
        
        // 保存文件到指定目录
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // 保存文件信息到数据库
        StrategyFile strategyFile = new StrategyFile();
        strategyFile.setFilename(filename);
        strategyFile.setOriginalFilename(originalFilename);
        strategyFile.setFilePath(filePath.toString());
        strategyFile.setFileSize(file.getSize());
        strategyFile.setDescription(description);
        strategyFile.setStatus(StrategyFile.StrategyStatus.INACTIVE);
        
        StrategyFile savedStrategy = strategyFileRepository.save(strategyFile);
        
        // 通知Python项目有新策略文件上传（异步操作，不影响主流程）
        try {
            pythonNotificationService.notifyStrategyUpload(savedStrategy);
            log.info("成功通知Python项目新策略上传: {}", filename);
        } catch (Exception e) {
            log.warn("通知Python项目新策略上传失败: {} - 这不影响文件上传功能", filename);
            log.debug("Python通知失败详情:", e);
        }
        
        return savedStrategy;
    }
    
    public void deleteStrategy(Long id) throws IOException {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("策略文件不存在");
        }
        
        StrategyFile strategy = strategyOpt.get();
        
        // 删除物理文件
        Path filePath = Paths.get(strategy.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("删除策略文件: {}", strategy.getFilename());
        }
        
        // 从数据库中删除记录
        strategyFileRepository.delete(strategy);
        
        // 通知Python项目删除策略（异步操作，不影响主流程）
        try {
            pythonNotificationService.notifyStrategyDelete(strategy);
            log.info("成功通知Python项目删除策略: {}", strategy.getFilename());
        } catch (Exception e) {
            log.warn("通知Python项目删除策略失败: {} - 这不影响删除功能", strategy.getFilename());
            log.debug("Python通知失败详情:", e);
        }
    }
    
    public void hotReloadStrategy(Long id) {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("策略文件不存在");
        }
        
        StrategyFile strategy = strategyOpt.get();
        
        // 更新状态为更新中
        strategy.setStatus(StrategyFile.StrategyStatus.UPDATING);
        strategyFileRepository.save(strategy);
        
        try {
            // 通知Python项目热更新策略
            pythonNotificationService.notifyStrategyHotReload(strategy);
            
            // 更新状态为激活
            strategy.setStatus(StrategyFile.StrategyStatus.ACTIVE);
            strategyFileRepository.save(strategy);
            
            log.info("成功热更新策略: {}", strategy.getFilename());
        } catch (Exception e) {
            // 更新状态为错误
            strategy.setStatus(StrategyFile.StrategyStatus.ERROR);
            strategyFileRepository.save(strategy);
            
            log.error("热更新策略失败: {}", strategy.getFilename(), e);
            throw new RuntimeException("通知Python项目失败", e);
        }
    }
    
    public StrategyFile updateStrategyStatus(Long id, String status) {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("策略文件不存在");
        }
        
        // 验证状态值
        StrategyFile.StrategyStatus newStatus;
        try {
            newStatus = StrategyFile.StrategyStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态值: " + status + "。支持的状态: ACTIVE, INACTIVE, UPDATING, ERROR");
        }
        
        StrategyFile strategy = strategyOpt.get();
        StrategyFile.StrategyStatus oldStatus = strategy.getStatus();
        
        strategy.setStatus(newStatus);
        StrategyFile updatedStrategy = strategyFileRepository.save(strategy);
        
        log.info("策略状态更新: {} - {} -> {}", strategy.getFilename(), oldStatus, newStatus);
        
        // 如果状态变为ACTIVE，尝试通知Python项目（可选）
        if (newStatus == StrategyFile.StrategyStatus.ACTIVE && oldStatus != StrategyFile.StrategyStatus.ACTIVE) {
            try {
                pythonNotificationService.notifyStrategyHotReload(strategy);
                log.info("成功通知Python项目策略激活: {}", strategy.getFilename());
            } catch (Exception e) {
                log.warn("通知Python项目策略激活失败: {} - 这不影响状态更新", strategy.getFilename());
                log.debug("Python通知失败详情:", e);
            }
        }
        
        return updatedStrategy;
    }
    
    public byte[] downloadStrategy(Long id) throws IOException {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("策略文件不存在");
        }
        
        StrategyFile strategy = strategyOpt.get();
        Path filePath = Paths.get(strategy.getFilePath());
        
        if (!Files.exists(filePath)) {
            throw new IOException("策略文件不存在: " + strategy.getFilename());
        }
        
        return Files.readAllBytes(filePath);
    }
    
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".py")) {
            throw new IllegalArgumentException("只支持Python文件(.py)");
        }
        
        // 检查文件大小（10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }
    }
    
    private String generateUniqueFilename(String originalFilename) {
        String extension = "";
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = originalFilename.substring(lastDot);
            originalFilename = originalFilename.substring(0, lastDot);
        }
        
        String baseFilename = originalFilename.replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = baseFilename + extension;
        
        // 检查文件名是否重复
        int counter = 1;
        while (strategyFileRepository.existsByFilename(filename)) {
            filename = baseFilename + "_" + counter + extension;
            counter++;
        }
        
        return filename;
    }
    
    public List<StrategyFile> importBuiltinStrategies() throws IOException {
        List<StrategyFile> importedStrategies = new ArrayList<>();
        
        // 定义内置策略文件路径、描述和中文名称
        Map<String, Map<String, String>> builtinStrategies = new HashMap<>();
        
        Map<String, String> elderIntradayInfo = new HashMap<>();
        elderIntradayInfo.put("description", "埃尔德三重过滤日内短线策略（当日平仓）");
        elderIntradayInfo.put("displayName", "埃尔德日内策略");
        builtinStrategies.put("C:/Users/druid/PycharmProjects/crypto-trading-strategy/src/strategies/elder_intraday_strategy.py", elderIntradayInfo);
        
        Map<String, String> elderSwingInfo = new HashMap<>();
        elderSwingInfo.put("description", "埃尔德三重过滤波段交易策略（持仓3-10天）");
        elderSwingInfo.put("displayName", "埃尔德波段策略");
        builtinStrategies.put("C:/Users/druid/PycharmProjects/crypto-trading-strategy/src/strategies/elder_swing_strategy.py", elderSwingInfo);
        
        for (Map.Entry<String, Map<String, String>> entry : builtinStrategies.entrySet()) {
            String filePath = entry.getKey();
            Map<String, String> strategyInfo = entry.getValue();
            String description = strategyInfo.get("description");
            String displayName = strategyInfo.get("displayName");
            Path sourcePath = Paths.get(filePath);
            
            if (!Files.exists(sourcePath)) {
                log.warn("内置策略文件不存在: {}", filePath);
                continue;
            }
            
            String fileName = sourcePath.getFileName().toString();
            
            // 检查是否已经导入过，如果存在但displayName为空，则更新displayName
            Optional<StrategyFile> existingStrategy = strategyFileRepository.findByOriginalFilename(fileName);
            if (existingStrategy.isPresent()) {
                StrategyFile existing = existingStrategy.get();
                if (existing.getDisplayName() == null || existing.getDisplayName().trim().isEmpty()) {
                    // 更新现有策略的displayName
                    existing.setDisplayName(displayName);
                    strategyFileRepository.save(existing);
                    importedStrategies.add(existing);
                    log.info("更新内置策略的中文名称: {} -> {}", fileName, displayName);
                } else {
                    log.info("内置策略已存在且已有中文名称，跳过: {}", fileName);
                }
                continue;
            }
            
            try {
                // 读取文件内容
                byte[] fileContent = Files.readAllBytes(sourcePath);
                long fileSize = fileContent.length;
                
                // 创建策略文件记录
                StrategyFile strategyFile = new StrategyFile();
                strategyFile.setFilename(fileName);
                strategyFile.setOriginalFilename(fileName);
                strategyFile.setFilePath(filePath);
                strategyFile.setFileSize(fileSize);
                strategyFile.setDescription(description);
                strategyFile.setDisplayName(displayName);
                strategyFile.setStatus(StrategyFile.StrategyStatus.ACTIVE); // 内置策略默认为激活状态
                
                StrategyFile savedStrategy = strategyFileRepository.save(strategyFile);
                importedStrategies.add(savedStrategy);
                
                log.info("成功导入内置策略: {}", fileName);
                
                // 通知Python项目有新策略
                try {
                    pythonNotificationService.notifyStrategyHotReload(savedStrategy);
                    log.info("成功通知Python项目内置策略: {}", fileName);
                } catch (Exception e) {
                    log.warn("通知Python项目内置策略失败: {} - 这不影响导入功能", fileName);
                    log.debug("Python通知失败详情:", e);
                }
                
            } catch (Exception e) {
                log.error("导入内置策略失败: {}", fileName, e);
            }
        }
        
        return importedStrategies;
    }
}