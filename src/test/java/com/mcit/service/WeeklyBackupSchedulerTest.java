package com.mcit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyBackupSchedulerTest {
    @TempDir Path root;
    @Test void fridayBoundaryUsesKabulTime() {
        ZoneId zone = ZoneId.of("Asia/Kabul");
        assertEquals(LocalDate.of(2026, 9, 4), WeeklyBackupScheduler.latestDueDate(ZonedDateTime.of(2026, 9, 11, 1, 59, 0, 0, zone)));
        assertEquals(LocalDate.of(2026, 9, 11), WeeklyBackupScheduler.latestDueDate(ZonedDateTime.of(2026, 9, 11, 2, 0, 0, 0, zone)));
    }
    @Test void catchesUpRetriesFailureAndDoesNotRepeatSuccess() throws Exception {
        DbBackupService service = mock(DbBackupService.class);
        when(service.generateBackup()).thenThrow(new IOException("test failure")).thenReturn(null);
        WeeklyBackupScheduler scheduler = new WeeklyBackupScheduler(service);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "zone", "Asia/Kabul");
        ReflectionTestUtils.setField(scheduler, "backupPath", root.toString());
        scheduler.checkWeeklyBackup();
        assertFalse(Files.exists(root.resolve(".last-weekly-backup")));
        scheduler.checkWeeklyBackup();
        scheduler.checkWeeklyBackup();
        verify(service, times(2)).generateBackup();
        assertTrue(Files.exists(root.resolve(".last-weekly-backup")));
    }
}
