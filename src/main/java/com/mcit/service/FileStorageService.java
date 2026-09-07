package com.mcit.service;

import com.mcit.entity.LawAttachment;
import com.mcit.exception.FileStorageException;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.LawAttachmentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
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
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    @Value("${file.storage.base-path}")
    private String basePath;

    @Value("${file.storage.laws-subdir:laws}")
    private String lawsSubdir;

    @Value("${file.storage.profile-subdir:profileImages}")
    private String profileSubdir;

    private Path lawAttachmentLocation;
    private Path profileImageLocation;

    public FileStorageService(LawAttachmentRepository lawAttachmentRepository) {
        this.lawAttachmentRepository = lawAttachmentRepository;
    }

    @PostConstruct
    public void init() {
        this.lawAttachmentLocation = Paths.get(basePath, lawsSubdir).toAbsolutePath().normalize();
        this.profileImageLocation = Paths.get(basePath, profileSubdir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.lawAttachmentLocation);
            Files.createDirectories(this.profileImageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create directories to store files", ex);
        }
    }

    public Path getLawAttachmentPath() {
        return this.lawAttachmentLocation;
    }

    public Path getProfileImagePath() {
        return this.profileImageLocation;
    }

    /** Rebind cached locations after a complete attachment-directory swap. */
    public synchronized void refreshLocations() {
        this.lawAttachmentLocation = Paths.get(basePath, lawsSubdir).toAbsolutePath().normalize();
        this.profileImageLocation = Paths.get(basePath, profileSubdir).toAbsolutePath().normalize();
    }

    public String saveLawAttachment(MultipartFile file) {
        validatePdfFile(file);

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFileName);
        String baseName = originalFileName.replace("." + extension, "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = baseName + "_" + timestamp + "." + extension;

        try {
            Files.createDirectories(this.lawAttachmentLocation);
            Path targetLocation = this.lawAttachmentLocation.resolve(fileName).normalize();
            if (!targetLocation.startsWith(this.lawAttachmentLocation)) {
                throw new FileStorageException("Invalid law attachment filename.");
            }
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

    public String saveProfileImage(MultipartFile file) {
        validateImageFile(file);

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String fileName = "profile_" + System.currentTimeMillis() + "." + extension;

        try {
            Files.createDirectories(this.profileImageLocation);
            Path targetLocation = this.profileImageLocation.resolve(fileName).normalize();
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

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !Arrays.asList("jpg", "jpeg", "png").contains(extension.toLowerCase())) {
            throw new FileStorageException("Only JPG, JPEG, PNG files are allowed for profile images.");
        }
    }

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

    public void deleteLawAttachment(LawAttachment attachment) {
        if (attachment != null && attachment.getFilePath() != null && !attachment.getFilePath().isBlank()) {
            deleteLawAttachment(attachment.getFilePath());
        }
    }

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

    public Resource loadLawAttachmentById(Long lawId, String language) {
        List<LawAttachment> attachments;

        if (language != null && !language.isEmpty()) {
            attachments = lawAttachmentRepository.findByLawIdAndLanguage(lawId, language);
        } else {
            var primaryAttachment = lawAttachmentRepository.findByLawIdAndIsPrimaryTrue(lawId);
            attachments = primaryAttachment.map(List::of)
                    .orElseGet(() -> lawAttachmentRepository.findByLawId(lawId));
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

    public Resource loadLawAttachmentById(Long lawId) {
        return loadLawAttachmentById(lawId, null);
    }

    public String getFormattedFileSize(String filePath, boolean isProfileImage) {
        Long size = getFileSizeInBytes(filePath, isProfileImage);

        if (size == null) {
            return null;
        }

        double sizeInMB = size / (1024.0 * 1024.0);
        return String.format("%.2f MB", sizeInMB);
    }

    public String getFormattedFileSize(LawAttachment attachment) {
        if (attachment == null || attachment.getFilePath() == null) {
            return null;
        }

        return getFormattedFileSize(attachment.getFilePath(), false);
    }

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

    public Long getFileSizeInBytes(String filePath, boolean isProfileImage) {
        Path folder = isProfileImage ? profileImageLocation : lawAttachmentLocation;

        if (filePath == null) {
            return null;
        }

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

    public Long getFileSizeInBytes(LawAttachment attachment) {
        if (attachment == null || attachment.getFilePath() == null) {
            return null;
        }

        return getFileSizeInBytes(attachment.getFilePath(), false);
    }

    public boolean fileExists(String filePath, boolean isProfileImage) {
        Path folder = isProfileImage ? profileImageLocation : lawAttachmentLocation;

        if (filePath == null) {
            return false;
        }

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

    public long getLawAttachmentRecordCount() {
        return lawAttachmentRepository.count();
    }

    /** Validate restored database paths against a staged attachment root before activation. */
    public void validateLawAttachmentFiles(Path stagedAttachmentRoot, long expectedRecordCount) {
        List<LawAttachment> records = lawAttachmentRepository.findAll();
        if (records.size() != expectedRecordCount) {
            throw new FileStorageException("Restored attachment record count does not match the backup manifest.");
        }
        Path root = stagedAttachmentRoot.toAbsolutePath().normalize();
        for (LawAttachment attachment : records) {
            String storedPath = attachment.getFilePath();
            if (storedPath == null || storedPath.isBlank()) {
                throw new FileStorageException("Restored attachment record has an empty file path.");
            }
            String relative = storedPath.replace('\\', '/');
            Path candidate = root.resolve(relative).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
                throw new FileStorageException("Restored attachment is missing: " + storedPath);
            }
        }
    }
}
