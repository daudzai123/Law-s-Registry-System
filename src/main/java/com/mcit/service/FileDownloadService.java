package com.mcit.service;

import com.mcit.entity.Law;
import com.mcit.exception.FileStorageException;
import com.mcit.repo.LawRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileDownloadService {

    private final Path fileStorageLocation;
    private final LawRepository lawRepository;

    @Autowired
    public FileDownloadService(LawRepository lawRepository) {
        this.fileStorageLocation = Paths.get("E:\\Law's Registry System\\attachment").toAbsolutePath().normalize();
        this.lawRepository = lawRepository;
    }

    public Resource loadLawAttachmentById(Long lawId) {
        try {
            Law law = lawRepository.findById(lawId)
                    .orElseThrow(() -> new FileStorageException("Law with ID " + lawId + " not found."));

            String attachmentPath = law.getAttachment();

            if (attachmentPath == null || attachmentPath.trim().isEmpty()) {
                throw new FileStorageException("No attachment found for Law ID: " + lawId);
            }

            // Extract only the filename and resolve it in the base directory
            Path fileNameOnly = Paths.get(attachmentPath).getFileName();
            Path filePath = fileStorageLocation.resolve(fileNameOnly).normalize();

            if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
                throw new FileStorageException("Attachment is not accessible: " + fileNameOnly);
            }

            return new UrlResource(filePath.toUri());

        } catch (Exception ex) {
            throw new FileStorageException("Failed to load law attachment: " + ex.getMessage(), ex);
        }
    }
}
