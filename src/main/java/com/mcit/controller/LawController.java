package com.mcit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcit.dto.LawDTO;
import com.mcit.entity.Law;
import com.mcit.entity.MyUser;
import com.mcit.exception.DuplicateLawException;
import com.mcit.repo.MyUserRepository;
import com.mcit.service.FileDownloadService;
import com.mcit.service.FileStorageService;
import com.mcit.service.LawService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/laws")
@RequiredArgsConstructor
public class LawController {

    private final LawService lawService;
    private final MyUserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final FileDownloadService fileDownloadService;
    private final ObjectMapper objectMapper; // Provided by Spring Boot auto-config

    private static final long MAX_FILE_SIZE = 3 * 1024 * 1024; // 3 MB

    /**
     * Create a law from multipart/form-data:
     * - law: JSON (LawDTO)
     * - attachment: file (pdf/docx)
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> addLaw(
            @RequestPart("law") String lawJson,
            @RequestPart(value = "attachment", required = false) MultipartFile attachmentFile,
            HttpServletRequest request
    ) throws IOException {

        // Parse incoming JSON to DTO
        LawDTO lawDTO;
        try {
            lawDTO = objectMapper.readValue(lawJson, LawDTO.class);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body("Invalid law JSON payload.");
        }

        // Validate logged-in user
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<MyUser> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user.");
        }
        lawDTO.setUserId(userOpt.get().getId());

        // Validate attachment presence (required)
        if (attachmentFile == null || attachmentFile.isEmpty()) {
            return ResponseEntity.badRequest().body("Attachment file is required.");
        }

        // Validate attachment size
        if (attachmentFile.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body("Attachment size exceeds 3MB limit.");
        }

        // Validate attachment file type by content type AND extension (pdf or docx)
        if (!isAllowedFile(attachmentFile)) {
            return ResponseEntity.badRequest()
                    .body("Only PDF and DOCX files are allowed.");
        }

        // Save attachment file and set filename in DTO
        String savedFilename = fileStorageService.saveFile(attachmentFile);
        lawDTO.setAttachment(savedFilename);

        try {
            LawDTO saved = lawService.createLawFromDTO(lawDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (DuplicateLawException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save law.");
        }
    }

    /**
     * Return all laws as DTOs (avoid returning entities directly)
     */
    @GetMapping
    public ResponseEntity<List<LawDTO>> getAllLaws() {
        List<LawDTO> dtos = lawService.findAllAsDTO();
        return ResponseEntity.ok(dtos);
    }

    private boolean isAllowedFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        boolean validContentType = "application/pdf".equalsIgnoreCase(contentType) ||
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType);

        boolean validExtension = filename != null && filename.toLowerCase().matches(".*\\.(pdf|docx)$");

        return validContentType && validExtension;
    }


    // ✅ 3. Find a law by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getLawById(@PathVariable Long id) {
        LawDTO law = lawService.findByIdAsDTO(id);
        if (law == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Law not found with ID: " + id);
        }
        return ResponseEntity.ok(law);
    }

    // ✅ 4. Delete a law by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteLaw(@PathVariable Long id) {
        lawService.deleteById(id);
        return ResponseEntity.ok("Law deleted successfully with ID: " + id);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> partialUpdateLaw(@PathVariable Long id, @RequestBody LawDTO updates) {
        try {
            LawDTO updated = lawService.partialUpdateLaw(id, updates);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Law not found with ID: " + id);
            }
            return ResponseEntity.ok(updated);
        } catch (DuplicateLawException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/download_attachment/{id}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        Resource resource = fileDownloadService.loadFileById(id);

        String contentType = "application/octet-stream";
        try {
            contentType = resource.getURL().openConnection().getContentType();
        } catch (Exception ignored) {}

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }


}
