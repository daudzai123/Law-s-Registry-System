package com.mcit.service;

import com.mcit.entity.Law;
import com.mcit.exception.FileStorageException;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.LawRepository;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

@Service
public class FileStorageService {

    private final LawRepository lawRepository;
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    private final Path lawAttachmentLocation;
    private final Path profileImageLocation;

    public FileStorageService(LawRepository lawRepository) {
        this.lawRepository = lawRepository;

        this.lawAttachmentLocation = Paths.get("E:\\Law's Registry System\\attachment\\laws")
                .toAbsolutePath().normalize();
        this.profileImageLocation = Paths.get("E:\\Law's Registry System\\attachment\\profileImages")
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.lawAttachmentLocation);
            Files.createDirectories(this.profileImageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create directories to store files", ex);
        }
    }

    // ---------------- Save Law Attachment (PDF only) ----------------
    public String saveLawAttachment(MultipartFile file) {
        validatePdfFile(file);

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFileName);
        String baseName = originalFileName.replace("." + extension, "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = baseName + "_" + timestamp + "." + extension;

        try {
            Path targetLocation = this.lawAttachmentLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return "laws/" + fileName; // store relative path in DB
        } catch (IOException ex) {
            throw new FileStorageException("Could not store law attachment " + fileName, ex);
        }
    }

    public void validatePdfFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileStorageException("Attachment size exceeds 100MB limit.");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        boolean validContentType = "application/pdf".equalsIgnoreCase(contentType);
        boolean validExtension = filename != null && filename.toLowerCase().endsWith(".pdf");

        if (!(validContentType && validExtension)) {
            throw new FileStorageException("Only PDF files are allowed for law attachments.");
        }
    }

    // ---------------- Save Profile Image (jpg, jpeg, png) ----------------
    public String saveProfileImage(MultipartFile file) {
        validateImageFile(file);

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String fileName = "profile_" + System.currentTimeMillis() + "." + extension;

        try {
            Path targetLocation = this.profileImageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return "profileImages/" + fileName; // store relative path in DB
        } catch (IOException ex) {
            throw new FileStorageException("Could not store profile image " + fileName, ex);
        }
    }


    public void validateImageFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileStorageException("Profile image size exceeds 100MB limit.");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename()).toLowerCase();
        if (!Arrays.asList("jpg", "jpeg", "png").contains(extension)) {
            throw new FileStorageException("Only JPG, JPEG, PNG files are allowed for profile images.");
        }
    }

    // ---------------- Delete Files ----------------
    public void deleteLawAttachment(String fileName) {
        deleteFile(lawAttachmentLocation, fileName);
    }

    public void deleteProfileImage(String fileName) {
        deleteFile(profileImageLocation, fileName);
    }

    private void deleteFile(Path folder, String fileName) {
        try {
            Path pathToDelete = folder.resolve(fileName).normalize();
            Files.deleteIfExists(pathToDelete);
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete file: " + fileName, ex);
        }
    }

    // ---------------- Load Law Attachment ----------------
    public Resource loadLawAttachmentById(Long lawId) {
        Law law = lawRepository.findById(lawId)
                .orElseThrow(() -> new ResourceNotFoundException("Law not found with id: " + lawId));

        String filename = law.getAttachment();
        if (filename == null || filename.isBlank()) {
            throw new ResourceNotFoundException("No attachment found for law id: " + lawId);
        }

        try {
            Path filePath = this.lawAttachmentLocation.resolve(filename.replace("laws/", "")).normalize();
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                throw new FileStorageException("File does not exist or is not readable: " + filename);
            }
            return new PathResource(filePath);
        } catch (Exception e) {
            throw new FileStorageException("Error loading file: " + filename, e);
        }
    }

    // Optional: get formatted file size
    public String getFormattedFileSize(String fileName, boolean isProfileImage) {
        Path folder = isProfileImage ? profileImageLocation : lawAttachmentLocation;
        if (fileName == null) return null;

        try {
            Path filePath = folder.resolve(fileName).normalize();
            long sizeInBytes = Files.size(filePath);
            double sizeInMB = sizeInBytes / (1024.0 * 1024.0);
            return String.format("%.2f MB", sizeInMB);
        } catch (IOException e) {
            return null;
        }
    }
}

