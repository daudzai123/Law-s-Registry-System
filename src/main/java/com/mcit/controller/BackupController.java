package com.mcit.controller;

import com.mcit.dto.BackupDTO;
import com.mcit.service.ActivityLogService;
import com.mcit.service.CurrentUserInfoService;
import com.mcit.service.DbBackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

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
    public Map<String, Object> downloadBackup() throws IOException, InterruptedException {
        var backup = backupService.generateBackup();

        String currentUsername = currentUserInfoService.getCurrentUserUsername();

        activityLogService.logActivity(
                "BackupDB",
                null,
                "CREATE_BACKUP",
                "Database backup generated successfully",
                currentUsername
        );
        return Map.of("id", backup.getId(), "backupPath", backup.getBackupPath());
    }

    @GetMapping("/download/{fileName:.+}")
    public void BackupDownload(HttpServletResponse response, @PathVariable String fileName) throws IOException {
        backupService.downloadSql(response,fileName);
        String currentUsername = currentUserInfoService.getCurrentUserUsername();

        activityLogService.logActivity(
                "BackupDB",
                null,
                "DOWNLOAD_BACKUP",
                "Downloaded backup file: " + fileName,
                currentUsername
        );
    }

    @PostMapping("/restore/{fileName:.+}")
    public String RestoreBackup(@PathVariable String fileName) throws IOException, InterruptedException {
        String currentUsername = currentUserInfoService.getCurrentUserUsername();
        String result = backupService.restoreDB(fileName);

        activityLogService.logActivity(
                "BackupDB",
                null,
                "RESTORE_BACKUP",
                "Restored backup file: " + fileName,
                currentUsername
        );
        return result;
    }

    @PostMapping("/download-ticket/{fileName:.+}")
    public Map<String, String> createDownloadTicket(@PathVariable String fileName) {
        return Map.of("ticket", backupService.createDownloadTicket(fileName));
    }

    @GetMapping("/download-by-ticket/{ticket}")
    public void downloadByTicket(HttpServletResponse response, @PathVariable String ticket) throws IOException {
        backupService.downloadWithTicket(response, ticket);
    }

    @PostMapping(value = "/restore/upload", consumes = "multipart/form-data")
    public String restoreUpload(@RequestParam("backup") MultipartFile backup)
            throws IOException, InterruptedException {
        String username = currentUserInfoService.getCurrentUserUsername();
        String result = backupService.restoreUpload(backup);
        activityLogService.logActivity("BackupDB", null, "RESTORE_BACKUP",
                "Restored uploaded database backup", username);
        return result;
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
