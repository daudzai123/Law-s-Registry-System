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
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class DbBackupService {

    private final DbBackupRepository backupRepository;
    private final CurrentUserInfoService currentUserInfoService;
    private final FileStorageService fileStorageService;
    @Autowired
    public DbBackupService(DbBackupRepository backupRepository, CurrentUserInfoService currentUserInfoService , FileStorageService fileStorageService) {
        this.backupRepository = backupRepository;
        this.currentUserInfoService = currentUserInfoService;
        this.fileStorageService = fileStorageService;
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
                    dto.setCreator(b.getCreatername() != null ? b.getCreatername().getId() : null);
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

    String timestamp = String.valueOf(System.currentTimeMillis());

    String backupFileName = "backup-" + timestamp + ".sql";
    String zipFileName = "attachments-" + timestamp + ".zip";

    Path backupFilePath = Paths.get(backupPath, backupFileName);
    Path zipFilePath = Paths.get(backupPath, zipFileName);

    // 1️⃣ Backup DB
    ProcessBuilder processBuilder = new ProcessBuilder(
            pgdump,
            "-h", "localhost",
            "-U", username,
            "-d", dbname,
            "-F", "c",
            "-b",
            "-v",
            "-f", backupFilePath.toString()
    );

    processBuilder.environment().put("PGPASSWORD", password);

    Process process = processBuilder.start();
    int exitCode = process.waitFor();

    if (exitCode != 0) {
        throw new RuntimeException("Database backup failed!");
    }

    // 2️⃣ Backup attachments folder
    Path attachmentFolder = fileStorageService.getLawAttachmentPath().getParent(); // "attachment" root
    zipFolder(attachmentFolder, zipFilePath);

    // 3️⃣ Save record
    BackupDB db = new BackupDB();
    db.setBackupPath(backupFileName);
    db.setCreated_at(LocalDateTime.now());
    db.setCreatername(currentUserInfoService.getCurrentUser());

    backupRepository.save(db);

    // 4️⃣ Download SQL only (optional)
    downloadSql(response, backupFileName);

    return db;
}

public String restoreDB(String fileName) throws IOException, InterruptedException {

    String timestamp = fileName.replace("backup-", "").replace(".sql", "");
    String zipFileName = "attachments-" + timestamp + ".zip";

    Path sqlPath = Paths.get(backupPath, fileName);
    Path zipPath = Paths.get(backupPath, zipFileName);

    // 1️⃣ Restore DB
    ProcessBuilder processBuilder = new ProcessBuilder(
            "C:/Program Files/PostgreSQL/17/bin/pg_restore",
            "-h", "localhost",
            "-U", username,
            "-d", dbname,
            "--clean",
            "-v",
            sqlPath.toString()
    );

    processBuilder.environment().put("PGPASSWORD", password);

    Process process = processBuilder.start();
    int exitCode = process.waitFor();

    if (exitCode != 0) {
        return "Restore failed (DB)!";
    }

    // 2️⃣ Restore attachments
    if (Files.exists(zipPath)) {
        Path targetFolder = fileStorageService.getLawAttachmentPath().getParent();
        unzip(zipPath, targetFolder);
    } else {
        return "DB restored, but attachments ZIP not found!";
    }

    return "Restore completed successfully (DB + attachments)!";
}

    private void zipFolder(Path sourceFolder, Path zipPath) throws IOException {
    try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipPath))) {
        Files.walk(sourceFolder)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(sourceFolder.relativize(path).toString());
                    try {
                        zs.putNextEntry(zipEntry);
                        Files.copy(path, zs);
                        zs.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException("Error zipping file: " + path, e);
                    }
                });
    }
}

private void unzip(Path zipFile, Path targetFolder) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            Path newPath = targetFolder.resolve(entry.getName()).normalize();

            if (entry.isDirectory()) {
                Files.createDirectories(newPath);
            } else {
                Files.createDirectories(newPath.getParent());
                Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
}
