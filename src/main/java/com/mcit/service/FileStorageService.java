package com.mcit.service;

import com.mcit.entity.LawAttachment;
import com.mcit.exception.FileStorageException;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.LawAttachmentRepository;
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
import java.util.List;

@Service
public class FileStorageService {

    private final LawAttachmentRepository lawAttachmentRepository;
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    private final Path lawAttachmentLocation;
    private final Path profileImageLocation;

    public FileStorageService(LawAttachmentRepository lawAttachmentRepository) {
        this.lawAttachmentRepository = lawAttachmentRepository;

        this.lawAttachmentLocation = Paths.get("D:\\Law's Registry System\\attachment\\laws")
                .toAbsolutePath().normalize();
        this.profileImageLocation = Paths.get("D:\\Law's Registry System\\attachment\\profileImages")
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.lawAttachmentLocation);
            Files.createDirectories(this.profileImageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create directories to store files", ex);
        }
    }

    // Get the law attachment path
    public Path getLawAttachmentPath() {
        return this.lawAttachmentLocation;
    }

    // Get the profile image path
    public Path getProfileImagePath() {
        return this.profileImageLocation;
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
            return "laws/" + fileName;
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
            return "profileImages/" + fileName;
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
    public void deleteLawAttachment(String attachmentPath) {
        if (attachmentPath == null || attachmentPath.isBlank()) {
            return;
        }
        
        try {
            String fileName = attachmentPath.replace("laws/", "");
            Path pathToDelete = lawAttachmentLocation.resolve(fileName).normalize();
            Files.deleteIfExists(pathToDelete);
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete file: " + attachmentPath, ex);
        }
    }
    
    // Delete attachment by LawAttachment entity
    public void deleteLawAttachment(LawAttachment attachment) {
        if (attachment != null && attachment.getFilePath() != null && !attachment.getFilePath().isBlank()) {
            deleteLawAttachment(attachment.getFilePath());
        }
    }
    
    // Delete all attachments for a law
    public void deleteAllLawAttachments(Long lawId) {
        List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(lawId);
        for (LawAttachment attachment : attachments) {
            deleteLawAttachment(attachment);
        }
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

    // ---------------- Load Law Attachment from attachment table ----------------
    public Resource loadLawAttachmentById(Long lawId, String language) {
        List<LawAttachment> attachments;
        
        if (language != null && !language.isEmpty()) {
            // Get specific language attachment
            attachments = lawAttachmentRepository.findByLawIdAndLanguage(lawId, language);
        } else {
            // Get primary attachment or first available
            var primaryAttachment = lawAttachmentRepository.findByLawIdAndIsPrimaryTrue(lawId);
            if (primaryAttachment.isPresent()) {
                attachments = List.of(primaryAttachment.get());
            } else {
                attachments = lawAttachmentRepository.findByLawId(lawId);
            }
        }
        
        if (attachments.isEmpty()) {
            throw new ResourceNotFoundException("No attachment found for law id: " + lawId + " with language: " + language);
        }
        
        LawAttachment attachment = attachments.get(0);
        String attachmentPath = attachment.getFilePath();
        
        if (attachmentPath == null || attachmentPath.isBlank()) {
            throw new ResourceNotFoundException("No file path found for attachment");
        }

        try {
            Path filePath = this.lawAttachmentLocation.resolve(attachmentPath.replace("laws/", "")).normalize();
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                throw new FileStorageException("File does not exist or is not readable: " + attachmentPath);
            }
            return new PathResource(filePath);
        } catch (Exception e) {
            throw new FileStorageException("Error loading file: " + attachmentPath, e);
        }
    }
    
    // Load attachment by attachment ID
    public Resource loadLawAttachmentByAttachmentId(Long attachmentId) {
        LawAttachment attachment = lawAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id: " + attachmentId));
        
        String attachmentPath = attachment.getFilePath();
        
        if (attachmentPath == null || attachmentPath.isBlank()) {
            throw new ResourceNotFoundException("No file path found for attachment id: " + attachmentId);
        }

        try {
            Path filePath = this.lawAttachmentLocation.resolve(attachmentPath.replace("laws/", "")).normalize();
            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                throw new FileStorageException("File does not exist or is not readable: " + attachmentPath);
            }
            return new PathResource(filePath);
        } catch (Exception e) {
            throw new FileStorageException("Error loading file: " + attachmentPath, e);
        }
    }

    // Overloaded method for backward compatibility
    public Resource loadLawAttachmentById(Long lawId) {
        return loadLawAttachmentById(lawId, null);
    }

    // Get formatted file size
    public String getFormattedFileSize(String filePath, boolean isProfileImage) {
        Path folder = isProfileImage ? profileImageLocation : lawAttachmentLocation;
        if (filePath == null) return null;

        try {
            String cleanFileName = filePath;
            if (filePath.startsWith("laws/")) {
                cleanFileName = filePath.replace("laws/", "");
            } else if (filePath.startsWith("profileImages/")) {
                cleanFileName = filePath.replace("profileImages/", "");
            }
            
            Path filePathObj = folder.resolve(cleanFileName).normalize();
            long sizeInBytes = Files.size(filePathObj);
            double sizeInMB = sizeInBytes / (1024.0 * 1024.0);
            return String.format("%.2f MB", sizeInMB);
        } catch (IOException e) {
            return null;
        }
    }
    
    // Get formatted file size for LawAttachment
    public String getFormattedFileSize(LawAttachment attachment) {
        if (attachment == null || attachment.getFilePath() == null) {
            return null;
        }
        return getFormattedFileSize(attachment.getFilePath(), false);
    }
    
    // Get total formatted file size for all attachments of a law
    public String getTotalFormattedFileSizeForLaw(Long lawId) {
        List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(lawId);
        if (attachments.isEmpty()) {
            return null;
        }
        
        long totalSize = 0;
        for (LawAttachment attachment : attachments) {
            Long size = getFileSizeInBytes(attachment.getFilePath(), false);
            if (size != null) {
                totalSize += size;
            }
        }
        
        if (totalSize > 0) {
            double sizeInMB = totalSize / (1024.0 * 1024.0);
            return String.format("%.2f MB", sizeInMB);
        }
        
        return null;
    }
    
    // Get file size in bytes
    public Long getFileSizeInBytes(String filePath, boolean isProfileImage) {
        Path folder = isProfileImage ? profileImageLocation : lawAttachmentLocation;
        if (filePath == null) return null;

        try {
            String cleanFileName = filePath;
            if (filePath.startsWith("laws/")) {
                cleanFileName = filePath.replace("laws/", "");
            } else if (filePath.startsWith("profileImages/")) {
                cleanFileName = filePath.replace("profileImages/", "");
            }
            
            Path filePathObj = folder.resolve(cleanFileName).normalize();
            return Files.size(filePathObj);
        } catch (IOException e) {
            return null;
        }
    }
    
    // Get file size in bytes for LawAttachment
    public Long getFileSizeInBytes(LawAttachment attachment) {
        if (attachment == null || attachment.getFilePath() == null) {
            return null;
        }
        return getFileSizeInBytes(attachment.getFilePath(), false);
    }
    
    // Check if file exists
    public boolean fileExists(String filePath, boolean isProfileImage) {
        Path folder = isProfileImage ? profileImageLocation : lawAttachmentLocation;
        if (filePath == null) return false;
        
        try {
            String cleanFileName = filePath;
            if (filePath.startsWith("laws/")) {
                cleanFileName = filePath.replace("laws/", "");
            } else if (filePath.startsWith("profileImages/")) {
                cleanFileName = filePath.replace("profileImages/", "");
            }
            
            Path filePathObj = folder.resolve(cleanFileName).normalize();
            return Files.exists(filePathObj) && Files.isReadable(filePathObj);
        } catch (Exception e) {
            return false;
        }
    }
}