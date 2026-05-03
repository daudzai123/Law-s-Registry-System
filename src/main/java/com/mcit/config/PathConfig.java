// package com.mcit.config;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Component;
// import jakarta.annotation.PostConstruct;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;

// @Component
// public class PathConfig {
    
//     @Value("${file.storage.base-path:}")
//     private String configuredBasePath;
    
//     @Value("${backup.path:}")
//     private String configuredBackupPath;
    
//     @Value("${attachments.path:}")
//     private String configuredAttachmentsPath;
    
//     @Value("${pgdump.address:}")
//     private String configuredPgdumpPath;
    
//     private String resolvedBasePath;
//     private String resolvedBackupPath;
//     private String resolvedAttachmentsPath;
//     private String resolvedPgdumpPath;
    
//     @PostConstruct
//     public void init() {
//         // Resolve base storage path
//         if (configuredBasePath == null || configuredBasePath.trim().isEmpty()) {
//             String userHome = System.getProperty("user.home");
//             String os = System.getProperty("os.name").toLowerCase();
            
//             if (os.contains("win")) {
//                 resolvedBasePath = Paths.get(userHome, "LawMIS", "attachment").toString();
//             } else {
//                 resolvedBasePath = Paths.get(userHome, "LawMIS", "attachment").toString();
//             }
//         } else {
//             resolvedBasePath = configuredBasePath;
//         }
        
//         // Resolve backup path
//         if (configuredBackupPath == null || configuredBackupPath.trim().isEmpty()) {
//             String os = System.getProperty("os.name").toLowerCase();
//             if (os.contains("win")) {
//                 resolvedBackupPath = Paths.get(System.getProperty("user.home"), "LawMIS", "backups").toString();
//             } else {
//                 resolvedBackupPath = Paths.get("/opt", "lawmis", "backups").toString();
//             }
//         } else {
//             resolvedBackupPath = configuredBackupPath;
//         }
        
//         // Resolve attachments path (same as base storage path)
//         if (configuredAttachmentsPath == null || configuredAttachmentsPath.trim().isEmpty()) {
//             resolvedAttachmentsPath = resolvedBasePath;
//         } else {
//             resolvedAttachmentsPath = configuredAttachmentsPath;
//         }
        
//         // Resolve pg_dump path
//         if (configuredPgdumpPath == null || configuredPgdumpPath.trim().isEmpty()) {
//             resolvedPgdumpPath = detectPostgresPath();
//         } else {
//             resolvedPgdumpPath = configuredPgdumpPath;
//         }
        
//         // Create directories
//         createDirectories();
        
//         // Print configuration
//         printConfiguration();
//     }
    
//     private String detectPostgresPath() {
//         String os = System.getProperty("os.name").toLowerCase();
        
//         if (os.contains("win")) {
//             String[] possiblePaths = {
//                 "C:/Program Files/PostgreSQL/17/bin/pg_dump.exe",
//                 "C:/Program Files/PostgreSQL/16/bin/pg_dump.exe",
//                 "C:/Program Files/PostgreSQL/15/bin/pg_dump.exe",
//                 "C:/Program Files/PostgreSQL/14/bin/pg_dump.exe",
//                 "C:/Program Files (x86)/PostgreSQL/17/bin/pg_dump.exe"
//             };
            
//             for (String path : possiblePaths) {
//                 if (Files.exists(Paths.get(path))) {
//                     return path;
//                 }
//             }
            
//             return "C:/Program Files/PostgreSQL/17/bin/pg_dump.exe";
//         } else {
//             String[] possiblePaths = {
//                 "/usr/bin/pg_dump",
//                 "/usr/local/bin/pg_dump",
//                 "/opt/PostgreSQL/17/bin/pg_dump",
//                 "/usr/pgsql-17/bin/pg_dump"
//             };
            
//             for (String path : possiblePaths) {
//                 if (Files.exists(Paths.get(path))) {
//                     return path;
//                 }
//             }
            
//             return "/usr/bin/pg_dump";
//         }
//     }
    
//     public String getPgRestorePath() {
//         String pgdumpPath = getPgdumpPath();
//         return pgdumpPath.replace("pg_dump", "pg_restore");
//     }
    
//     private void createDirectories() {
//         try {
//             Files.createDirectories(Paths.get(resolvedBasePath));
//             Files.createDirectories(Paths.get(resolvedBasePath, "laws"));
//             Files.createDirectories(Paths.get(resolvedBasePath, "profileImages"));
//             Files.createDirectories(Paths.get(resolvedBackupPath));
            
//             System.out.println("✓ Directories created successfully");
//         } catch (Exception e) {
//             System.err.println("✗ Failed to create directories: " + e.getMessage());
//         }
//     }
    
//     private void printConfiguration() {
//         System.out.println("\n========== PATH CONFIGURATION ==========");
//         System.out.println("OS: " + System.getProperty("os.name"));
//         System.out.println("User Home: " + System.getProperty("user.home"));
//         System.out.println("Base Storage Path: " + resolvedBasePath);
//         System.out.println("Backup Path: " + resolvedBackupPath);
//         System.out.println("Attachments Path: " + resolvedAttachmentsPath);
//         System.out.println("pg_dump Path: " + resolvedPgdumpPath);
//         System.out.println("pg_restore Path: " + getPgRestorePath());
//         System.out.println("========================================\n");
//     }
    
//     // Getters
//     public String getBasePath() { return resolvedBasePath; }
//     public String getBackupPath() { return resolvedBackupPath; }
//     public String getAttachmentsPath() { return resolvedAttachmentsPath; }
//     public String getPgdumpPath() { return resolvedPgdumpPath; }
// }