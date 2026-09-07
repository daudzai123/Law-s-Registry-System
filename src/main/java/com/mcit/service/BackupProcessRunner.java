package com.mcit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class BackupProcessRunner {
    @Value("${spring.datasource.password}") private String password;
    @Value("${backup.process-timeout-seconds:1800}") private long timeoutSeconds;

    public void run(List<String> command) throws IOException, InterruptedException {
        command = new java.util.ArrayList<>(command);
        command.set(0, resolveExecutable(command.get(0)));
        Path log = Files.createTempFile("lawmis-backup-", ".log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("PGPASSWORD", password);
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            try {
                process = builder.start();
            } catch (IOException e) {
                throw new IOException("Cannot start PostgreSQL tool: " + command.get(0)
                        + ". Install PostgreSQL client tools or configure PG_DUMP_PATH / PG_RESTORE_PATH.");
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IOException("PostgreSQL backup/restore timed out after " + timeoutSeconds + " seconds.");
            }
            if (process.exitValue() != 0) {
                String detail;
                try (var input = Files.newInputStream(log)) {
                    detail = new String(input.readNBytes(4096), java.nio.charset.StandardCharsets.UTF_8);
                }
                if (password != null && !password.isEmpty()) detail = detail.replace(password, "***");
                throw new IOException("PostgreSQL command failed: " + detail);
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
            Files.deleteIfExists(log);
        }
    }

    private String resolveExecutable(String configured) throws IOException {
        // Explicit paths always win. Locate Windows installations even if the JVM's PATH is stale.
        if (!System.getProperty("os.name").toLowerCase().contains("win")
                || configured.contains("/") || configured.contains("\\")) return configured;
        String name = configured.endsWith(".exe") ? configured : configured + ".exe";
        String searchPath = System.getenv("PATH");
        if (searchPath != null) {
            for (String directory : searchPath.split(java.io.File.pathSeparator)) {
                try {
                    Path candidate = Path.of(directory.replace("\"", ""), name);
                    if (Files.isRegularFile(candidate)) return candidate.toString();
                } catch (InvalidPathException ignored) { }
            }
        }
        Path installations = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:/Program Files"), "PostgreSQL");
        if (Files.isDirectory(installations)) {
            try (var versions = Files.list(installations)) {
                var candidate = versions.filter(path -> path.getFileName().toString().matches("\\d+"))
                        .sorted(java.util.Comparator.comparingInt((Path path) -> Integer.parseInt(path.getFileName().toString())).reversed())
                        .map(path -> path.resolve("bin").resolve(name)).filter(Files::isRegularFile).findFirst();
                if (candidate.isPresent()) return candidate.get().toString();
            }
        }
        return configured;
    }
}
