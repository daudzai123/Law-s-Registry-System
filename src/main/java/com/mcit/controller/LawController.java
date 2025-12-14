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
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final ObjectMapper objectMapper;

    /* =========================================================
       1️⃣ CREATE
       ========================================================= */

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addLaw(
            @RequestPart("law") String lawJson,
            @RequestPart("attachment") MultipartFile attachmentFile
    ) {
        try {
            LawDTO lawDTO = objectMapper.readValue(lawJson, LawDTO.class);

            String username = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            MyUser user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Invalid user"));

            lawDTO.setUserId(user.getId());

            if (attachmentFile == null || attachmentFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Attachment file is required"));
            }

            if (!isAllowedFile(attachmentFile)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only PDF files are allowed"));
            }

            String savedFile = fileStorageService.saveFile(attachmentFile);
            lawDTO.setAttachment(savedFile);

            LawDTO savedLaw = lawService.addLawFromDTO(lawDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedLaw);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* =========================================================
       2️⃣ READ
       ========================================================= */

    @GetMapping("/{id}")
    public ResponseEntity<?> getLawById(@PathVariable Long id) {
        LawDTO law = lawService.findByIdAsDTO(id);
        if (law == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Law not found with ID: " + id);
        }
        return ResponseEntity.ok(law);
    }

    @GetMapping
    public ResponseEntity<LawPaginatedResponseDTO<LawResponseDTO>> searchLaws(
            LawSearchCriteriaDTO criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort
    ) {

        Page<Law> result = lawService.searchLaws(criteria, page, size, sort);

        List<LawResponseDTO> laws = result.getContent()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                new LawPaginatedResponseDTO<>(
                        laws,
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.hasNext(),
                        result.hasPrevious()
                )
        );
    }

    /* =========================================================
       3️⃣ UPDATE
       ========================================================= */

    @PatchMapping("/{id}")
    public ResponseEntity<?> partialUpdateLaw(
            @PathVariable Long id,
            @RequestBody LawDTO updates
    ) {
        try {
            LawDTO updated = lawService.partialUpdateLaw(id, updates);
            return ResponseEntity.ok(updated);
        } catch (DuplicateLawException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /* =========================================================
       4️⃣ DELETE
       ========================================================= */

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteLaw(@PathVariable Long id) {
        lawService.deleteById(id);
        return ResponseEntity.ok("Law deleted successfully with ID: " + id);
    }

    /* =========================================================
       5️⃣ SEARCH
       ========================================================= */

    @GetMapping("/search/byTitle")
    public ResponseEntity<List<LawResponseDTO>> searchByTitle(
            @RequestParam String title
    ) {
        return ResponseEntity.ok(lawService.searchByTitle(title));
    }

    @GetMapping("/search/exact-title")
    public ResponseEntity<?> searchByExactTitle(
            @RequestParam String title
    ) {
        try {
            return ResponseEntity.ok(lawService.findByExactTitle(title));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* =========================================================
       6️⃣ REPORTS & STATISTICS
       ========================================================= */

    @GetMapping("/status-counts")
    public ResponseEntity<Map<Status, Long>> getLawStatusCounts() {
        return ResponseEntity.ok(lawService.getLawCountsByStatus());
    }

    @GetMapping("/type-counts")
    public ResponseEntity<Map<LawType, Long>> getLawTypeCounts() {
        return ResponseEntity.ok(lawService.getLawCountsByType());
    }

    @GetMapping("/annually&monthly-report")
    public ResponseEntity<Map<LawType, Long>> getReport(
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String month
    ) {
        try {
            int y = (year == null)
                    ? LocalDate.now().getYear()
                    : Integer.parseInt(year);

            if (month != null) {
                return ResponseEntity.ok(
                        lawService.getReportByMonthAndYear(y, Integer.parseInt(month))
                );
            }
            return ResponseEntity.ok(lawService.getReportByYear(y));

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /* =========================================================
       7️⃣ FILE HANDLING
       ========================================================= */

    @GetMapping("/download_attachment/{id}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {

        Resource resource = fileDownloadService.loadFileById(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/view_attachment/{id}")
    public ResponseEntity<Resource> viewAttachment(@PathVariable Long id) {

        Resource resource = fileDownloadService.loadFileById(id);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /* =========================================================
       8️⃣ PRIVATE HELPERS
       ========================================================= */

    private boolean isAllowedFile(MultipartFile file) {
        return file.getContentType() != null
                && file.getContentType().equalsIgnoreCase("application/pdf")
                && file.getOriginalFilename() != null
                && file.getOriginalFilename().toLowerCase().endsWith(".pdf");
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
        dto.setAttachmentSize(
                fileStorageService.getFormattedFileSize(law.getAttachment())
        );

        if (law.getUser() != null) {
            dto.setUserId(law.getUser().getId());
        }
        return dto;
    }
}
