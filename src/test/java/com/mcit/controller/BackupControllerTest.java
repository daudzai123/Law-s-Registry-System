package com.mcit.controller;

import com.mcit.entity.BackupDB;
import com.mcit.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BackupControllerTest {
    DbBackupService service = mock(DbBackupService.class);
    ActivityLogService log = mock(ActivityLogService.class);
    CurrentUserInfoService user = mock(CurrentUserInfoService.class);
    org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new BackupController(service, log, user)).build();

    @Test void createReturnsFilenameForSeparateDownload() throws Exception {
        BackupDB backup = new BackupDB(); backup.setId(1L); backup.setBackupPath("backup-123.sql");
        when(service.generateBackup()).thenReturn(backup);
        mvc.perform(get("/api/backup/create")).andExpect(status().isOk())
                .andExpect(jsonPath("$.backupPath").value("backup-123.sql"));
    }

    @Test void multipartRestoreUsesUploadHandler() throws Exception {
        when(service.restoreUpload(any())).thenReturn("Restored");
        mvc.perform(multipart("/api/backup/restore/upload")
                .file(new MockMultipartFile("backup", "backup.zip", "application/zip", "PK".getBytes())))
                .andExpect(status().isOk()).andExpect(content().string("Restored"));
        verify(service, never()).restoreDB(anyString());
    }

    @Test void downloadDoesNotAppendTextToArchive() throws Exception {
        doAnswer(i -> {
            jakarta.servlet.http.HttpServletResponse response = i.getArgument(0);
            response.getOutputStream().write("PGDMP".getBytes());
            return null;
        }).when(service).downloadSql(any(), eq("backup-123.sql"));
        mvc.perform(get("/api/backup/download/backup-123.sql"))
                .andExpect(status().isOk()).andExpect(content().bytes("PGDMP".getBytes()));
    }
}
