package com.mcit.service;

import com.mcit.repo.DbBackupRepository;
import com.mcit.entity.BackupDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.io.*;
import java.util.List;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DbBackupServiceTest {
    @TempDir Path root;
    DbBackupRepository repository = mock(DbBackupRepository.class);
    BackupProcessRunner runner = mock(BackupProcessRunner.class);
    DbBackupService service;

    @BeforeEach void setup() throws Exception {
        FileStorageService storage = mock(FileStorageService.class);
        Path laws = Files.createDirectories(root.resolve("attachments/laws"));
        when(storage.getLawAttachmentPath()).thenReturn(laws);
        service = new DbBackupService(repository, mock(CurrentUserInfoService.class), storage, runner);
        ReflectionTestUtils.setField(service, "backupPath", root.resolve("backups").toString());
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:postgresql://db.example:5433/lawDB");
        ReflectionTestUtils.setField(service, "username", "postgres");
        ReflectionTestUtils.setField(service, "pgdump", "pg_dump");
        ReflectionTestUtils.setField(service, "pgrestore", "pg_restore");
        when(repository.save(any(BackupDB.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test void backupUsesConfiguredConnectionAndWorksWithoutLogin() throws Exception {
        doAnswer(i -> {
            List<String> command = i.getArgument(0);
            assertTrue(command.contains("db.example"));
            assertTrue(command.contains("5433"));
            Files.writeString(Path.of(command.get(command.indexOf("-f") + 1)), "PGDMP");
            return null;
        }).when(runner).run(anyList());
        BackupDB backup = service.generateBackup();
        assertNull(backup.getCreatername());
        assertTrue(Files.exists(root.resolve("backups").resolve(backup.getBackupPath())));
    }

    @Test void failedBackupDoesNotSaveRecord() throws Exception {
        doThrow(new IOException("failed")).when(runner).run(anyList());
        assertThrows(IOException.class, () -> service.generateBackup());
        verify(repository, never()).save(any());
        try (var files = Files.list(root.resolve("backups"))) { assertEquals(0, files.count()); }
    }

    @Test void invalidUploadNeverRunsRestore() {
        assertThrows(IllegalArgumentException.class, () -> service.restoreUpload(
                new MockMultipartFile("database", "test.sql", "text/plain", "SELECT 1".getBytes()), null));
        verifyNoInteractions(runner);
    }

    @Test void localRestoreIsAtomicAndTemporaryFileIsDeleted() throws Exception {
        var commands = org.mockito.ArgumentCaptor.forClass(List.class);
        ByteArrayOutputStream zipData = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipData)) {
            ZipEntry entry = new ZipEntry("laws/test.pdf");
            entry.setComment("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
            zip.putNextEntry(entry); zip.write("test".getBytes()); zip.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> service.restoreUpload(
                new MockMultipartFile("database", "PGDMPexample".getBytes()),
                new MockMultipartFile("attachments", zipData.toByteArray())));
    }

    @Test void zipTraversalRejectedBeforeDatabaseChanges() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(data)) {
            zip.putNextEntry(new ZipEntry("../outside.txt"));
            zip.write(1); zip.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> service.restoreUpload(
                new MockMultipartFile("database", "PGDMPexample".getBytes()),
                new MockMultipartFile("attachments", data.toByteArray())));
        verifyNoInteractions(runner);
    }

    @Test void rejectsPathsOutsideBackupDirectory() {
        assertThrows(IllegalArgumentException.class, () -> service.restoreDB("../other.sql"));
        verifyNoInteractions(runner);
    }
}
