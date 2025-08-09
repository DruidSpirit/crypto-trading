package druid.elf.tool.repository;

import druid.elf.tool.entity.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignal, String>, JpaSpecificationExecutor<TradeSignal> {

    @Modifying
    @Query("DELETE FROM TradeSignal ts WHERE ts.signalTime < :dateTime")
    long deleteBySignalTimeBefore(LocalDateTime dateTime);

    /**
     * 根据信号类型统计数量
     */
    @Query("SELECT COUNT(ts) FROM TradeSignal ts WHERE ts.signal = :signal")
    long countBySignal(String signal);

    /**
     * 统计不同交易对的数量
     */
    @Query("SELECT COUNT(DISTINCT ts.symbol) FROM TradeSignal ts")
    long countDistinctSymbols();

    /**
     * 获取按月统计的信号数据
     */
    @Query("SELECT " +
           "YEAR(ts.signalTime) as year, " +
           "MONTH(ts.signalTime) as month, " +
           "ts.signal as signal, " +
           "COUNT(ts) as count " +
           "FROM TradeSignal ts " +
           "WHERE ts.signalTime >= :startDate " +
           "GROUP BY YEAR(ts.signalTime), MONTH(ts.signalTime), ts.signal " +
           "ORDER BY YEAR(ts.signalTime), MONTH(ts.signalTime)")
    List<Map<String, Object>> getMonthlySignalStats(LocalDateTime startDate);
}
