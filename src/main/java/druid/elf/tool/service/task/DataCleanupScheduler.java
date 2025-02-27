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

    // 每月1号凌晨1点执行清理任务
    @Scheduled(cron = "0 0 1 1 * ?")
    public void cleanExpiredTradeSignals() {
        try {
            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();
            // 计算一个月前的时间点
            LocalDateTime oneMonthAgo = now.minusMonths(1);

            log.info("开始执行过期交易信号清理任务，清理时间点早于: {}", oneMonthAgo);

            // 执行删除操作，返回删除的记录数
            long deletedCount = tradeSignalRepository.deleteBySignalTimeBefore(oneMonthAgo);

            log.info("清理任务完成，共删除 {} 条过期交易信号记录", deletedCount);

        } catch (Exception e) {
            log.error("清理过期交易信号时发生错误", e);
        }
    }
}
