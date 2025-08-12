package druid.elf.tool.controller;

import druid.elf.tool.entity.StrategyFile;
import druid.elf.tool.service.StrategyFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/strategies")
@Slf4j
public class StrategyFileController {
    
    @Autowired
    private StrategyFileService strategyFileService;
    
    @GetMapping
    public ResponseEntity<List<StrategyFile>> getAllStrategies() {
        List<StrategyFile> strategies = strategyFileService.getAllStrategies();
        return ResponseEntity.ok(strategies);
    }
    
    @PostMapping("/upload")
    public ResponseEntity<?> uploadStrategy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        
        try {
            StrategyFile strategyFile = strategyFileService.uploadStrategy(file, description);
            log.info("策略文件上传成功: {}", strategyFile.getFilename());
            return ResponseEntity.ok(strategyFile);
        } catch (IllegalArgumentException e) {
            log.warn("策略文件上传参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "参数错误", "message", e.getMessage()));
        } catch (IOException e) {
            log.error("策略文件上传IO错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文件上传失败", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("策略文件上传未知错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "上传失败", "message", "系统错误，请稍后重试"));
        }
    }
    
    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadStrategy(@PathVariable Long id) {
        try {
            Optional<StrategyFile> strategyOpt = strategyFileService.getStrategyById(id);
            if (strategyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            StrategyFile strategy = strategyOpt.get();
            byte[] fileContent = strategyFileService.downloadStrategy(id);
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + strategy.getOriginalFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            log.warn("下载策略文件参数错误: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("下载策略文件IO错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("下载策略文件未知错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{id}/reload")
    public ResponseEntity<?> hotReloadStrategy(@PathVariable Long id) {
        try {
            strategyFileService.hotReloadStrategy(id);
            log.info("策略文件热更新成功: {}", id);
            return ResponseEntity.ok(Map.of("message", "策略热更新成功"));
        } catch (IllegalArgumentException e) {
            log.warn("热更新策略文件参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "参数错误", "message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("热更新策略文件失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "热更新失败", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("热更新策略文件未知错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "热更新失败", "message", "系统错误，请稍后重试"));
        }
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStrategyStatus(@PathVariable Long id, @RequestBody Map<String, String> statusRequest) {
        try {
            String status = statusRequest.get("status");
            if (status == null || status.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "参数错误", "message", "status参数不能为空"));
            }
            
            StrategyFile updatedStrategy = strategyFileService.updateStrategyStatus(id, status.trim().toUpperCase());
            log.info("策略文件状态更新成功: {} -> {}", id, status);
            return ResponseEntity.ok(updatedStrategy);
        } catch (IllegalArgumentException e) {
            log.warn("更新策略状态参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "参数错误", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("更新策略状态未知错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "状态更新失败", "message", "系统错误，请稍后重试"));
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStrategy(@PathVariable Long id) {
        try {
            strategyFileService.deleteStrategy(id);
            log.info("策略文件删除成功: {}", id);
            return ResponseEntity.ok(Map.of("message", "策略删除成功"));
        } catch (IllegalArgumentException e) {
            log.warn("删除策略文件参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "参数错误", "message", e.getMessage()));
        } catch (IOException e) {
            log.error("删除策略文件IO错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除失败", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("删除策略文件未知错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除失败", "message", "系统错误，请稍后重试"));
        }
    }
    
    @PostMapping("/import-builtin")
    public ResponseEntity<?> importBuiltinStrategies() {
        try {
            List<StrategyFile> importedStrategies = strategyFileService.importBuiltinStrategies();
            log.info("内置策略文件导入成功，共导入 {} 个策略", importedStrategies.size());
            return ResponseEntity.ok(Map.of(
                "message", "内置策略导入成功", 
                "count", importedStrategies.size(),
                "strategies", importedStrategies
            ));
        } catch (Exception e) {
            log.error("导入内置策略文件失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "导入失败", "message", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/info")
    public ResponseEntity<?> updateStrategy(@PathVariable Long id, @RequestBody Map<String, String> updateRequest) {
        try {
            Optional<StrategyFile> strategyOpt = strategyFileService.getStrategyById(id);
            if (strategyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            StrategyFile strategy = strategyOpt.get();
            
            // 更新描述
            if (updateRequest.containsKey("description")) {
                strategy.setDescription(updateRequest.get("description"));
            }
            
            // 更新显示名称
            if (updateRequest.containsKey("displayName")) {
                strategy.setDisplayName(updateRequest.get("displayName"));
            }
            
            StrategyFile updatedStrategy = strategyFileService.saveStrategy(strategy);
            log.info("策略信息更新成功: {} ", id);
            return ResponseEntity.ok(updatedStrategy);
        } catch (Exception e) {
            log.error("更新策略信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "更新失败", "message", e.getMessage()));
        }
    }
}