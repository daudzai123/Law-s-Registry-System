package com.mcit.controller;

import com.mcit.dto.BackupDTO;
import com.mcit.service.ActivityLogService;
import com.mcit.service.CurrentUserInfoService;
import com.mcit.service.DbBackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/backup")
public class BackupController{
    private final DbBackupService backupService;
    private final ActivityLogService activityLogService;
    private final CurrentUserInfoService currentUserInfoService;

    @Autowired
    public BackupController(DbBackupService backupService, ActivityLogService activityLogService, CurrentUserInfoService currentUserInfoService) {
        this.backupService = backupService;
        this.activityLogService = activityLogService;
        this.currentUserInfoService = currentUserInfoService;
    }


    @GetMapping("/all")
    public List<BackupDTO> allBackups(){
        return backupService.getAllBackup();
    }

    @GetMapping("/create")
    public void downloadBackup(HttpServletResponse response) throws IOException, InterruptedException {
        backupService.generateBackup(response);

        String currentUsername = currentUserInfoService.getCurrentUserUsername();

        activityLogService.logActivity(
                "BackupDB",
                null,
                "CREATE_BACKUP",
                "Database backup generated successfully",
                currentUsername
        );
    }

    @GetMapping("/download/{fileName:.+}")
    public String BackupDownload(HttpServletResponse response, @PathVariable String fileName) throws IOException {
        backupService.downloadSql(response,fileName);
        String currentUsername = currentUserInfoService.getCurrentUserUsername();

        activityLogService.logActivity(
                "BackupDB",
                null,
                "DOWNLOAD_BACKUP",
                "Downloaded backup file: " + fileName,
                currentUsername
        );
        return "Backup downloaded successfully";
    }

    @PostMapping("/restore/{fileName:.+}")
    public String RestoreBackup(@PathVariable String fileName) throws IOException, InterruptedException {
        String currentUsername = currentUserInfoService.getCurrentUserUsername();

        activityLogService.logActivity(
                "BackupDB",
                null,
                "RESTORE_BACKUP",
                "Restored backup file: " + fileName,
                currentUsername
        );
        return backupService.restoreDB(fileName);
    }

    // DELETE API to remove a backup
    @DeleteMapping("/delete/{id}")
    public String deleteBackup(@PathVariable Long id) {
        String currentUsername = currentUserInfoService.getCurrentUserUsername();

        activityLogService.logActivity(
                "BackupDB",
                id,
                "DELETE_BACKUP",
                "Deleted backup with ID: " + id,
                currentUsername
        );

        return backupService.deleteBackup(id);
    }
}