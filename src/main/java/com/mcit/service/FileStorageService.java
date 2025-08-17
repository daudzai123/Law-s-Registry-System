package com.mcit.service;

import com.mcit.exception.FileStorageException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {

    private static final long fileSize = 10 * 1024 * 1024; // 10MB
    private final Path fileStorageLocation;

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("D:\\Law's Registry System\\attachment")
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the directory to store files", ex);
        }
    }

    public String saveFile(MultipartFile file) {
        validateFile(file);

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFileName);
        String baseName = originalFileName.replace("." + extension, "");

        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = baseName + "_" + timestamp + "." + extension;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > fileSize) {
            throw new FileStorageException("Attachment size exceeds 10MB limit.");
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        boolean validContentType = "application/pdf".equalsIgnoreCase(contentType);
        boolean validExtension = filename != null && filename.toLowerCase().endsWith(".pdf");

        if (!(validContentType && validExtension)) {
            throw new FileStorageException("Only PDF files are allowed.");
        }
    }

    public String saveProfileImage(MultipartFile file, String username) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String fileName = "profile_" + username + "." + extension;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return targetLocation.toString(); // or relative path
        } catch (IOException ex) {
            throw new FileStorageException("Could not store profile image for " + username, ex);
        }
    }


    public void deleteFile(String fileName) {
        try {
            Path pathToDelete = this.fileStorageLocation.resolve(fileName).normalize();
            Files.deleteIfExists(pathToDelete); // Delete the file if it exists
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete file: " + fileName, ex);
        }
    }

    public String getFormattedFileSize(String fileName) {
        if (fileName == null) return null;
        Path filePath = this.fileStorageLocation.resolve(fileName);

        try {
            long sizeInBytes = Files.size(filePath);
            double sizeInMB = sizeInBytes / (1024.0 * 1024.0);
            return String.format("%.2f MB", sizeInMB);
        } catch (IOException e) {
            return null; // file not found or unreadable
        }
    }
}
