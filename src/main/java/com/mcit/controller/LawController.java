package com.mcit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcit.dto.LawDTO;
import com.mcit.dto.LawPaginatedResponseDTO;
import com.mcit.dto.LawResponseDTO;
import com.mcit.dto.LawSearchCriteriaDTO;
import com.mcit.entity.Law;
import com.mcit.entity.MyUser;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
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
import java.util.Map;
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

    // this function shows allowed files
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

    // get all laws or filter laws
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

    // mapping lawResponseDTO to law entity
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
        dto.setAttachmentSize(fileStorageService.getFormattedFileSize(law.getAttachment()));

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

    // partial update law by id
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

    // download the law attachment
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

    @GetMapping("/status-counts")
    public ResponseEntity<Map<Status, Long>> getLawStatusCounts() {
        Map<Status, Long> counts = lawService.getLawCountsByStatus();
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/type-counts")
    public ResponseEntity<Map<LawType, Long>> getLawTypeCounts() {
        Map<LawType, Long> counts = lawService.getLawCountsByType();
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/annually&monthly-report")
    public ResponseEntity<Map<LawType, Long>> getMonthlyReport(
            @RequestParam String year,               // e.g., "1446"
            @RequestParam(required = false) String month) {  // e.g., "01", "02", etc.

        try {
            int yearInt = Integer.parseInt(year);

            if (month != null) {
                int monthInt = Integer.parseInt(month);
                return ResponseEntity.ok(lawService.getReportByMonthAndYear(yearInt, monthInt));
            } else {
                return ResponseEntity.ok(lawService.getReportByYear(yearInt));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search/byTitle")
    public ResponseEntity<LawResponseDTO> getLawByTitle(@RequestParam String title) {
        LawResponseDTO dto = lawService.findByTitle(title);
        return ResponseEntity.ok(dto);
    }



}