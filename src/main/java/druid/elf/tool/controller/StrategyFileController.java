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
            log.info("Strategy file uploaded successfully: {}", strategyFile.getFilename());
            return ResponseEntity.ok(Map.of("success", true, "data", strategyFile, "message", "Strategy uploaded successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Strategy file upload parameter error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Parameter error", "message", e.getMessage()));
        } catch (IOException e) {
            log.error("Strategy file upload IO error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "File upload failed", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Strategy file upload unknown error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Upload failed", "message", "System error, please try again later"));
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
            log.warn("Strategy download parameter error: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Strategy download IO error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Strategy download unknown error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{id}/reload")
    public ResponseEntity<?> hotReloadStrategy(@PathVariable Long id) {
        try {
            strategyFileService.hotReloadStrategy(id);
            log.info("Strategy file hot-reload successful: {}", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Strategy hot-reload successful"));
        } catch (IllegalArgumentException e) {
            log.warn("Hot-reload parameter error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Parameter error", "message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Hot-reload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Hot-reload failed", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Hot-reload unknown error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Hot-reload failed", "message", "System error, please try again later"));
        }
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStrategyStatus(@PathVariable Long id, @RequestBody Map<String, String> statusRequest) {
        try {
            String status = statusRequest.get("status");
            if (status == null || status.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Parameter error", "message", "status parameter cannot be empty"));
            }
            
            StrategyFile updatedStrategy = strategyFileService.updateStrategyStatus(id, status.trim().toUpperCase());
            log.info("Strategy file status updated: {} -> {}", id, status);
            return ResponseEntity.ok(updatedStrategy);
        } catch (IllegalArgumentException e) {
            log.warn("Update strategy status parameter error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Parameter error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Update strategy status unknown error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Status update failed", "message", "System error, please try again later"));
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStrategy(@PathVariable Long id) {
        try {
            strategyFileService.deleteStrategy(id);
            log.info("Strategy file deleted successfully: {}", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Strategy deleted successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Delete strategy file parameter error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Parameter error", "message", e.getMessage()));
        } catch (IOException e) {
            log.error("Delete strategy file IO error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Delete failed", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Delete strategy file unknown error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Delete failed", "message", "System error, please try again later"));
        }
    }
    
    @PostMapping("/import-builtin")
    public ResponseEntity<?> importBuiltinStrategies() {
        try {
            List<StrategyFile> importedStrategies = strategyFileService.importBuiltinStrategies();
            log.info("Built-in strategy files imported successfully, total {} strategies", importedStrategies.size());
            return ResponseEntity.ok(Map.of(
                "message", "Built-in strategies imported successfully",
                "count", importedStrategies.size(),
                "strategies", importedStrategies
            ));
        } catch (Exception e) {
            log.error("Failed to import built-in strategy files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Import failed", "message", e.getMessage()));
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
            

            if (updateRequest.containsKey("description")) {
                strategy.setDescription(updateRequest.get("description"));
            }
            

            if (updateRequest.containsKey("displayName")) {
                strategy.setDisplayName(updateRequest.get("displayName"));
            }
            
            StrategyFile updatedStrategy = strategyFileService.saveStrategy(strategy);
            log.info("Strategy info updated successfully: {} ", id);
            return ResponseEntity.ok(updatedStrategy);
        } catch (Exception e) {
            log.error("Failed to update strategy info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Update failed", "message", e.getMessage()));
        }
    }
}