package druid.elf.tool.service.task;

import druid.elf.tool.repository.TradeSignalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
public class DataCleanupScheduler {

    @Autowired
    private TradeSignalRepository tradeSignalRepository;


    @Scheduled(cron = "0 0 1 1 * ?")
    public void cleanExpiredTradeSignals() {
        try {

            LocalDateTime now = LocalDateTime.now();

            LocalDateTime oneMonthAgo = now.minusMonths(1);

            log.info("Starting expired trading signal cleanup, cutoff date: {}", oneMonthAgo);


            long deletedCount = tradeSignalRepository.deleteBySignalTimeBefore(oneMonthAgo);

            log.info("Cleanup task completed, deleted {} expired trading signal records", deletedCount);

        } catch (Exception e) {
            log.error("Error occurred while cleaning up expired trading signals", e);
        }
    }
}
