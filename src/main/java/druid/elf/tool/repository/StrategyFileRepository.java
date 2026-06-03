package druid.elf.tool.repository;

import druid.elf.tool.entity.StrategyFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StrategyFileRepository extends JpaRepository<StrategyFile, Long> {
    
    Optional<StrategyFile> findByFilename(String filename);
    
    List<StrategyFile> findAllByOrderByUploadTimeDesc();
    
    boolean existsByFilename(String filename);
    
    boolean existsByOriginalFilename(String originalFilename);
    
    Optional<StrategyFile> findByOriginalFilename(String originalFilename);
}