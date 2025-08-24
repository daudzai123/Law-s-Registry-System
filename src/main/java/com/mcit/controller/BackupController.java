package com.mcit.controller;

import com.mcit.dto.BackupDTO;
import com.mcit.entity.BackupDB;
import com.mcit.service.DbBackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/backup")
public class BackupController{
    private final DbBackupService backupService;

    @Autowired
    public BackupController(DbBackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/all")
    public List<BackupDTO> allBackups(){
        return backupService.getAllBackup();
    }

   @GetMapping("/create")
   public void downloadBackup(HttpServletResponse response) throws IOException, InterruptedException {
        backupService.generateBackup(response);
   }

   @RequestMapping("/{fileName:.+}")
   public String BackupDownload(HttpServletResponse response, @PathVariable String fileName) throws IOException {
       backupService.downloadSql(response,fileName);
       return "Backup downloaded successfully";
   }

    @RequestMapping("/restore/{fileName:.+}")
    public String RestoreBackup(@PathVariable String fileName) throws IOException, InterruptedException {
        return backupService.restoreDB(fileName);
    }

    // DELETE API to remove a backup
    @DeleteMapping("/delete/{id}")
    public String deleteBackup(@PathVariable Long id) {
        return backupService.deleteBackup(id);
    }
}