package com.mcit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcit.dto.LawDTO;
import com.mcit.dto.LawPaginatedResponseDTO;
import com.mcit.dto.LawResponseDTO;
import com.mcit.dto.LawSearchCriteriaDTO;
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
import org.springframework.data.domain.Page;
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
            @RequestPart(value = "attachment", required = false) MultipartFile attachmentFile
    ) throws IOException {

        // Parse JSON
        LawDTO lawDTO = objectMapper.readValue(lawJson, LawDTO.class);

        // Validate logged-in user
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        MyUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user."));
        lawDTO.setUserId(user.getId());

        // Validate attachment presence
        if (attachmentFile == null || attachmentFile.isEmpty()) {
            throw new IllegalArgumentException("Attachment file is required.");
        }

        // Validate attachment size
        if (attachmentFile.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Attachment size exceeds 3MB limit.");
        }

        // Validate file type
        if (!isAllowedFile(attachmentFile)) {
            throw new IllegalArgumentException("Only PDF and DOCX files are allowed.");
        }

        // Save file
        String savedFilename = fileStorageService.saveFile(attachmentFile);
        lawDTO.setAttachment(savedFilename);

        // Save law (business-specific exception handled separately)
        LawDTO saved = lawService.addLawFromDTO(lawDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }


//    /**
//     * Return all laws as DTOs (avoid returning entities directly)
//     */
//    @GetMapping
//    public ResponseEntity<List<LawDTO>> getAllLaws() {
//        List<LawDTO> dtos = lawService.findAllAsDTO();
//        return ResponseEntity.ok(dtos);
//    }

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

    @GetMapping
    public ResponseEntity<LawPaginatedResponseDTO<LawResponseDTO>> searchLaws(
            LawSearchCriteriaDTO criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort) {

        Page<Law> result = lawService.searchLaws(criteria, page, size, sort);

        List<LawResponseDTO> dtoList = result.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        LawPaginatedResponseDTO<LawResponseDTO> response = new LawPaginatedResponseDTO<>(
                dtoList,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious()
        );

        return ResponseEntity.ok(response);
    }

    private LawResponseDTO mapToDTO(Law law) {
        LawResponseDTO dto = new LawResponseDTO();
        dto.setId(law.getId());
        dto.setType(law.getType());
        dto.setSequenceNumber(law.getSequenceNumber());
        dto.setTitleEng(law.getTitleEng());
        dto.setTitlePs(law.getTitlePs());
        dto.setTitleDr(law.getTitleDr());
        dto.setPublishDate(law.getPublishDate());
        dto.setStatus(law.getStatus());
        dto.setDescription(law.getDescription());
        dto.setAttachment(law.getAttachment());

        if (law.getUser() != null) {
            dto.setUserId(law.getUser().getId());
        }

        return dto;
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
