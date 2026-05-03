package com.mcit.service;

import com.mcit.repo.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
@EnableScheduling
public class LogCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(LogCleanupService.class);

    @Autowired
    private ActivityLogRepository activityLogRepository;

    // Keep last 1 year of logs
    @Value("${log.cleanup.keep-years:1}")
    private int keepYears;

    // Run at 2 AM on the 1st of every month
    @Value("${log.cleanup.cron:0 0 2 1 * ?}")
    private String cleanupCron;

    @Scheduled(cron = "${log.cleanup.cron:0 0 2 1 * ?}")
    @Transactional
    public void cleanupOldLogs() {
        logger.info("========================================");
        logger.info("Starting scheduled log cleanup...");
        logger.info("Retention Policy: Keep last {} year(s) of logs", keepYears);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffDateTime = now.minus(keepYears, ChronoUnit.YEARS);
        
        logger.info("Cutoff date: {} (deleting logs before this date)", cutoffDateTime);
        
        // Check how many logs will be deleted
        long logsToDelete = activityLogRepository.countLogsOlderThan(cutoffDateTime);
        logger.info("Found {} log records older than {} year(s)", logsToDelete, keepYears);
        
        if (logsToDelete > 0) {
            // Perform the deletion
            int deletedCount = activityLogRepository.deleteLogsOlderThan(cutoffDateTime);
            logger.info("✓ Deleted {} log records (older than {} year(s))", deletedCount, keepYears);
        } else {
            logger.info("No logs found older than {} year(s)", keepYears);
        }
        
        // Verify current retention
        verifyRetentionPolicy();
        
        logger.info("Log cleanup completed");
        logger.info("========================================");
    }

    private void verifyRetentionPolicy() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oldestRemaining = activityLogRepository.getOldestLogTimestamp();
        
        if (oldestRemaining != null) {
            long yearsKept = ChronoUnit.YEARS.between(oldestRemaining, now);
            logger.info("Current retention: {} year(s) of logs (from {} to {})", 
                       yearsKept, oldestRemaining, now);
        }
    }

    @Transactional
    public int manualCleanup(int retentionValue, String unit) {
        ChronoUnit chronoUnit;
        switch (unit.toUpperCase()) {
            case "YEARS":
                chronoUnit = ChronoUnit.YEARS;
                break;
            case "MONTHS":
                chronoUnit = ChronoUnit.MONTHS;
                break;
            case "DAYS":
                chronoUnit = ChronoUnit.DAYS;
                break;
            default:
                throw new IllegalArgumentException("Invalid unit: " + unit);
        }
        
        LocalDateTime cutoff = LocalDateTime.now().minus(retentionValue, chronoUnit);
        int deleted = activityLogRepository.deleteLogsOlderThan(cutoff);
        logger.info("Manual cleanup: deleted {} logs older than {} {}", deleted, retentionValue, unit);
        return deleted;
    }

    public Map<String, Object> getRetentionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oldest = activityLogRepository.getOldestLogTimestamp();
        LocalDateTime oneYearAgo = now.minus(1, ChronoUnit.YEARS);
        
        stats.put("oldestLogTimestamp", oldest);
        stats.put("currentTime", now);
        stats.put("retentionPolicyYears", keepYears);
        
        if (oldest != null) {
            long yearsKept = ChronoUnit.YEARS.between(oldest, now);
            stats.put("yearsKept", yearsKept);
            long logsOlderThanOneYear = activityLogRepository.countLogsOlderThan(oneYearAgo);
            stats.put("logsOlderThanOneYear", logsOlderThanOneYear);
        }
        
        stats.put("totalLogCount", activityLogRepository.count());
        return stats;
    }
}