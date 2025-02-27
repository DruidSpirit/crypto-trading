package druid.elf.tool.repository;

import druid.elf.tool.entity.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignal, String>, JpaSpecificationExecutor<TradeSignal> {

    @Modifying
    @Query("DELETE FROM TradeSignal ts WHERE ts.signalTime < :dateTime")
    long deleteBySignalTimeBefore(LocalDateTime dateTime);
}
