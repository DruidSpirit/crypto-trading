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
    
    @Value("${strategy.upload.path:python-strategy-service/src/strategies}")
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

        validateFile(file);
        

        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        

        String originalFilename = file.getOriginalFilename();
        String filename = generateUniqueFilename(originalFilename);
        Path filePath = uploadDir.resolve(filename);
        

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        

        StrategyFile strategyFile = new StrategyFile();
        strategyFile.setFilename(filename);
        strategyFile.setOriginalFilename(originalFilename);
        strategyFile.setFilePath(filePath.toString());
        strategyFile.setFileSize(file.getSize());
        strategyFile.setDescription(description);
        strategyFile.setStatus(StrategyFile.StrategyStatus.INACTIVE);
        
        StrategyFile savedStrategy = strategyFileRepository.save(strategyFile);
        

        try {
            pythonNotificationService.notifyStrategyUpload(savedStrategy);
            log.info("Successfully notified Python project of new strategy upload: {}", filename);
        } catch (Exception e) {
            log.warn("Failed to notify Python project of new strategy upload: {} - does not affect file upload", filename);
            log.debug("Python notification failure details:", e);
        }
        
        return savedStrategy;
    }
    
    public void deleteStrategy(Long id) throws IOException {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("Strategy file not found");
        }

        StrategyFile strategy = strategyOpt.get();


        Path filePath = Paths.get(strategy.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("Deleted strategy file: {}", strategy.getFilename());
        }
        

        strategyFileRepository.delete(strategy);
        

        try {
            pythonNotificationService.notifyStrategyDelete(strategy);
            log.info("Successfully notified Python project of strategy deletion: {}", strategy.getFilename());
        } catch (Exception e) {
            log.warn("Failed to notify Python project of strategy deletion: {} - does not affect deletion", strategy.getFilename());
            log.debug("Python notification failure details:", e);
        }
    }
    
    public void hotReloadStrategy(Long id) {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("Strategy file not found");
        }

        StrategyFile strategy = strategyOpt.get();


        strategy.setStatus(StrategyFile.StrategyStatus.UPDATING);
        strategyFileRepository.save(strategy);

        try {

            pythonNotificationService.notifyStrategyHotReload(strategy);


            strategy.setStatus(StrategyFile.StrategyStatus.ACTIVE);
            strategyFileRepository.save(strategy);

            log.info("Strategy hot-reload successful: {}", strategy.getFilename());
        } catch (Exception e) {

            strategy.setStatus(StrategyFile.StrategyStatus.ERROR);
            strategyFileRepository.save(strategy);

            log.error("Strategy hot-reload failed: {}", strategy.getFilename(), e);
            throw new RuntimeException("Failed to notify Python project", e);
        }
    }
    
    public StrategyFile updateStrategyStatus(Long id, String status) {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("Strategy file not found");
        }


        StrategyFile.StrategyStatus newStatus;
        try {
            newStatus = StrategyFile.StrategyStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status value: " + status + ". Supported statuses: ACTIVE, INACTIVE, UPDATING, ERROR");
        }

        StrategyFile strategy = strategyOpt.get();
        StrategyFile.StrategyStatus oldStatus = strategy.getStatus();

        strategy.setStatus(newStatus);
        StrategyFile updatedStrategy = strategyFileRepository.save(strategy);

        log.info("Strategy status updated: {} - {} -> {}", strategy.getFilename(), oldStatus, newStatus);


        if (newStatus == StrategyFile.StrategyStatus.ACTIVE && oldStatus != StrategyFile.StrategyStatus.ACTIVE) {
            try {
                pythonNotificationService.notifyStrategyHotReload(strategy);
                log.info("Successfully notified Python project of strategy activation: {}", strategy.getFilename());
            } catch (Exception e) {
                log.warn("Failed to notify Python project of strategy activation: {} - does not affect status update", strategy.getFilename());
                log.debug("Python notification failure details:", e);
            }
        }
        
        return updatedStrategy;
    }
    
    public byte[] downloadStrategy(Long id) throws IOException {
        Optional<StrategyFile> strategyOpt = strategyFileRepository.findById(id);
        if (strategyOpt.isEmpty()) {
            throw new IllegalArgumentException("Strategy file not found");
        }

        StrategyFile strategy = strategyOpt.get();
        Path filePath = Paths.get(strategy.getFilePath());

        if (!Files.exists(filePath)) {
            throw new IOException("Strategy file not found: " + strategy.getFilename());
        }
        
        return Files.readAllBytes(filePath);
    }
    
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".py")) {
            throw new IllegalArgumentException("Only Python files (.py) are supported");
        }


        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size cannot exceed 10MB");
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
        

        int counter = 1;
        while (strategyFileRepository.existsByFilename(filename)) {
            filename = baseFilename + "_" + counter + extension;
            counter++;
        }
        
        return filename;
    }
    
    public List<StrategyFile> importBuiltinStrategies() throws IOException {
        List<StrategyFile> importedStrategies = new ArrayList<>();
        

        Map<String, Map<String, String>> builtinStrategies = new HashMap<>();
        
        Map<String, String> elderIntradayInfo = new HashMap<>();
        elderIntradayInfo.put("description", "Elder triple filter intraday strategy (same-day close)");
        elderIntradayInfo.put("displayName", "Elder Intraday Strategy");
        builtinStrategies.put("python-strategy-service/src/strategies/elder_intraday_strategy.py", elderIntradayInfo);
        
        Map<String, String> elderSwingInfo = new HashMap<>();
        elderSwingInfo.put("description", "Elder triple filter swing trading strategy (hold 3-10 days)");
        elderSwingInfo.put("displayName", "Elder Swing Strategy");
        builtinStrategies.put("python-strategy-service/src/strategies/elder_swing_strategy.py", elderSwingInfo);
        
        for (Map.Entry<String, Map<String, String>> entry : builtinStrategies.entrySet()) {
            String filePath = entry.getKey();
            Map<String, String> strategyInfo = entry.getValue();
            String description = strategyInfo.get("description");
            String displayName = strategyInfo.get("displayName");
            Path sourcePath = Paths.get(filePath);
            
            if (!Files.exists(sourcePath)) {
                log.warn("Built-in strategy file not found: {}", filePath);
                continue;
            }
            
            String fileName = sourcePath.getFileName().toString();
            

            Optional<StrategyFile> existingStrategy = strategyFileRepository.findByOriginalFilename(fileName);
            if (existingStrategy.isPresent()) {
                StrategyFile existing = existingStrategy.get();
                if (existing.getDisplayName() == null || existing.getDisplayName().trim().isEmpty()) {

                    existing.setDisplayName(displayName);
                    strategyFileRepository.save(existing);
                    importedStrategies.add(existing);
                    log.info("Updated built-in strategy display name: {} -> {}", fileName, displayName);
                } else {
                    log.info("Built-in strategy already exists with display name, skipping: {}", fileName);
                }
                continue;
            }
            
            try {

                byte[] fileContent = Files.readAllBytes(sourcePath);
                long fileSize = fileContent.length;
                

                StrategyFile strategyFile = new StrategyFile();
                strategyFile.setFilename(fileName);
                strategyFile.setOriginalFilename(fileName);
                strategyFile.setFilePath(filePath);
                strategyFile.setFileSize(fileSize);
                strategyFile.setDescription(description);
                strategyFile.setDisplayName(displayName);
                strategyFile.setStatus(StrategyFile.StrategyStatus.ACTIVE);
                
                StrategyFile savedStrategy = strategyFileRepository.save(strategyFile);
                importedStrategies.add(savedStrategy);
                
                log.info("Successfully imported built-in strategy: {}", fileName);


                try {
                    pythonNotificationService.notifyStrategyHotReload(savedStrategy);
                    log.info("Successfully notified Python project of built-in strategy: {}", fileName);
                } catch (Exception e) {
                    log.warn("Failed to notify Python project of built-in strategy: {} - does not affect import", fileName);
                    log.debug("Python notification failure details:", e);
                }
                
            } catch (Exception e) {
                log.error("Failed to import built-in strategy: {}", fileName, e);
            }
        }
        
        return importedStrategies;
    }
}
