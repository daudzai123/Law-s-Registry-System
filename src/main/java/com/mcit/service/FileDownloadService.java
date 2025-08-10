package com.mcit.service;

import com.mcit.entity.Law;
import com.mcit.exception.FileStorageException;
import com.mcit.repo.LawRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileDownloadService {

    private final Path fileStorageLocation;
    private final LawRepository lawRepository;

    @Autowired
    public FileDownloadService(LawRepository lawRepository) {
        this.fileStorageLocation = Paths.get("D:\\Law's Registry System\\attachment").toAbsolutePath().normalize();
        this.lawRepository = lawRepository;
    }

    public Resource loadFileById(Long id) {
        try {
            Law fileRecord = lawRepository.findById(id)
                    .orElseThrow(() -> new FileStorageException("File with ID " + id + " not found in database."));

            String attachmentPath = fileRecord.getAttachment();
            if (attachmentPath == null || attachmentPath.isBlank()) {
                throw new FileStorageException("No attachment found for record ID: " + id);
            }

            // Remove the base directory if stored path contains it
            String fileName = attachmentPath.replace(fileStorageLocation.toString() + "\\", "");

            Path filePath = fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new FileStorageException("File does not exist or is not readable: " + fileName);
            }

            return resource;
        } catch (Exception ex) {
            throw new FileStorageException("Error loading file: " + ex.getMessage(), ex);
        }
    }
}
