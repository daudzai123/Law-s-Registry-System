package com.mcit.service;

import com.mcit.dto.BackupDTO;
import com.mcit.entity.BackupDB;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.DbBackupRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DbBackupService {

    private final DbBackupRepository backupRepository;
    private final CurrentUserInfoService currentUserInfoService;

    @Autowired
    public DbBackupService(DbBackupRepository backupRepository, CurrentUserInfoService currentUserInfoService) {
        this.backupRepository = backupRepository;
        this.currentUserInfoService = currentUserInfoService;
    }

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${datasource.name}")
    private String dbname;

    @Value("${pgdump.address}")
    private String pgdump;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${backup.path}")
    private String backupPath;

    // Return DTOs instead of entities
    public List<BackupDTO> getAllBackup() {
        return backupRepository.findAll()
                .stream()
                .map(b -> {
                    BackupDTO dto = new BackupDTO();
                    dto.setId(b.getId());
                    dto.setBackupPath(b.getBackupPath());
                    dto.setCreated_at(b.getCreated_at());
                    dto.setCreator(b.getCreator() != null ? b.getCreator().getId() : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public String deleteBackup(Long id) {
        BackupDB db = backupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Backup not found!"));
        Path path = Paths.get(backupPath, db.getBackupPath());
        try {
            Files.deleteIfExists(path); // avoids exception if file doesn't exist
            backupRepository.delete(db);
            return "Backup deleted successfully.";
        } catch (IOException e) {
            System.err.println("Error deleting file: " + e.getMessage());
            // Still delete DB record to avoid orphan
            backupRepository.delete(db);
            return "Backup file could not be deleted, but DB record removed.";
        }

    }

    public void downloadSql(HttpServletResponse response, String fileName) throws IOException {
        Path backupFilePath = Paths.get(backupPath, fileName);
        if (Files.exists(backupFilePath)) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            try (InputStream is = Files.newInputStream(backupFilePath);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) != -1) {
                    os.write(buffer, 0, length);
                }
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Backup file not found!");
        }
    }

    public BackupDB generateBackup(HttpServletResponse response) throws IOException, InterruptedException {
        String backupFileName = "backup-" + System.currentTimeMillis() + ".sql";
        String backupFilePath = Paths.get(backupPath, backupFileName).toString();

        ProcessBuilder processBuilder = new ProcessBuilder(
                pgdump,
                "-h", "localhost",
                "-U", username,
                "-d", dbname,
                "-F", "c",
                "-b",
                "-v",
                "-f", backupFilePath
        );
        processBuilder.environment().put("PGPASSWORD", password);

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            BackupDB db = new BackupDB();
            db.setBackupPath(backupFileName);
            db.setCreated_at(LocalDateTime.now());
            db.setCreator(currentUserInfoService.getCurrentUser());
            backupRepository.save(db);
            System.out.println("Backup completed successfully!");
            downloadSql(response, backupFileName);
        } else {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Backup process failed!");
            System.err.println("Backup failed!");
        }
        return null;
    }

    public String restoreDB(String fileName) throws IOException, InterruptedException {
        String restoreFilePath = Paths.get(backupPath, fileName).toString();

        ProcessBuilder processBuilder = new ProcessBuilder(
                "C:/Program Files/PostgreSQL/17/bin/pg_restore",
                "-h", "localhost",
                "-U", username,
                "-d", dbname,
                "--clean",
                "-v",
                restoreFilePath
        );
        processBuilder.environment().put("PGPASSWORD", password);

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("Restore completed successfully!");
            return "Restore completed successfully!";
        } else {
            System.err.println("Restore failed!");
            return "Restore failed!";
        }
    }
}
