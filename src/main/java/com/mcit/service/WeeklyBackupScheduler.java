package com.mcit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

@Component
public class WeeklyBackupScheduler {
    private static final Logger log = LoggerFactory.getLogger(WeeklyBackupScheduler.class);
    private final DbBackupService service;
    @Value("${backup.path}") private String backupPath;
    @Value("${backup.weekly.enabled:true}") private boolean enabled;
    @Value("${backup.weekly.zone:Asia/Kabul}") private String zone;

    public WeeklyBackupScheduler(DbBackupService service) { this.service = service; }

    static LocalDate latestDueDate(ZonedDateTime now) {
        LocalDate friday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        if (now.isBefore(friday.atTime(2, 0).atZone(now.getZone()))) friday = friday.minusWeeks(1);
        return friday;
    }

    // Hourly checks also retry failures and catch up after the backend was offline.
    @Scheduled(cron = "0 0 * * * *", zone = "${backup.weekly.zone:Asia/Kabul}")
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void checkWeeklyBackup() {
        if (!enabled) return;
        try {
            LocalDate due = latestDueDate(ZonedDateTime.now(ZoneId.of(zone)));
            Path marker = Paths.get(backupPath).toAbsolutePath().normalize().resolve(".last-weekly-backup");
            if (Files.exists(marker) && !LocalDate.parse(Files.readString(marker).trim()).isBefore(due)) return;
            service.generateBackup();
            Files.writeString(marker, due.toString());
            log.info("Weekly backup completed for Friday {}", due);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Weekly backup interrupted");
        } catch (Exception e) {
            log.error("Weekly backup failed; will retry at the next hourly check: {}", e.getMessage());
        }
    }
}
