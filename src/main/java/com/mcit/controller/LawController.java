package com.mcit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcit.dto.*;
import com.mcit.entity.Law;
import com.mcit.entity.User;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import com.mcit.exception.DuplicateLawException;
import com.mcit.exception.FileStorageException;
import com.mcit.repo.LawRepository;
import com.mcit.repo.UserRepository;
import com.mcit.service.ActivityLogService;
import com.mcit.service.FileStorageService;
import com.mcit.service.LawService;
import io.swagger.v3.core.util.ObjectMapperFactory;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/laws")
@RequiredArgsConstructor
public class LawController {

    private final LawService lawService;
    private final LawRepository lawRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final ActivityLogService activityLogService;

    // Add new law
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addLaw(
            @RequestPart("law") String lawJson,
            @RequestPart(value = "attachment", required = false) MultipartFile attachmentFile
    ) {
        try {
            // 1️⃣ Parse JSON
            LawDTO lawDTO = objectMapper.readValue(lawJson, LawDTO.class);

            String actor = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // 2️⃣ Get authenticated user
            String username = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Invalid user"));

            lawDTO.setUserId(user.getId());

            Optional<Law> existingLawWithAttachment = lawRepository
                    .findBySequenceNumber(lawDTO.getSequenceNumber())
                    .stream()
                    .filter(l -> l.getAttachment() != null && !l.getAttachment().isEmpty())
                    .findFirst();

            if (existingLawWithAttachment.isPresent()) {
                // Reuse existing attachment
                lawDTO.setAttachment(existingLawWithAttachment.get().getAttachment());
            } else {
                // New attachment required
                if (attachmentFile == null || attachmentFile.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Attachment file is required"));
                }

                try {
                    fileStorageService.validatePdfFile(attachmentFile);
                } catch (FileStorageException ex) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", ex.getMessage()));
                }

                // Save new attachment
                String savedFile = fileStorageService.saveLawAttachment(attachmentFile);
                lawDTO.setAttachment(savedFile);
            }

            // 4️⃣ Save law
            LawDTO savedLaw = lawService.addLawFromDTO(lawDTO);

            // ✅ ACTIVITY LOG
            activityLogService.logActivity(
                    "Law",
                    savedLaw.getId(),
                    "CREATE",
                    "Law created with sequence number: " + savedLaw.getSequenceNumber(),
                    actor
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(savedLaw);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateLaw(
            @PathVariable Long id,
            @RequestPart("law") String lawJson,
            @RequestPart(value = "attachment", required = false) MultipartFile attachmentFile
    ) {
        try {
            // 1️⃣ Parse JSON into LawDTO
            LawDTO lawDTO = ObjectMapperFactory.buildStrictGenericObjectMapper()
                    .readValue(lawJson, LawDTO.class);

            String actor = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // 2️⃣ Get authenticated user
            String username = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Invalid user"));

            lawDTO.setUserId(user.getId());

            // 3️⃣ Load existing law
            Law existingLaw = lawRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Law not found"));

            // 4️⃣ Handle attachment logic
            if (attachmentFile != null && !attachmentFile.isEmpty()) {
                // Validate new attachment
                fileStorageService.validatePdfFile(attachmentFile);

                // Save new attachment
                String savedFile = fileStorageService.saveLawAttachment(attachmentFile);
                lawDTO.setAttachment(savedFile);

                // ✅ Update all laws with same sequenceNumber in DB
                List<Law> allLawsWithSameSeq = lawRepository.findBySequenceNumber(existingLaw.getSequenceNumber());
                for (Law law : allLawsWithSameSeq) {
                    law.setAttachment(savedFile); // update only path in DB
                }
                lawRepository.saveAll(allLawsWithSameSeq);
            } else {
                // No new attachment uploaded, keep existing
                lawDTO.setAttachment(existingLaw.getAttachment());
            }

            // 5️⃣ Update the specific law DTO fields
            LawDTO updated = lawService.updateLawFromDTO(id, lawDTO);


            // ✅ ACTIVITY LOG
            activityLogService.logActivity(
                    "Law",
                    updated.getId(),
                    "UPDATE",
                    "Law updated with sequence number: " + updated.getSequenceNumber(),
                    actor
            );

            return ResponseEntity.ok(updated);

        } catch (DuplicateLawException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Read Law with attachment size
    @GetMapping("/{id}")
    public ResponseEntity<?> getLawById(@PathVariable Long id) {
        Law law = lawService.findByIdEntity(id); // new helper to return Law entity
        LawResponseDTO lawDTO = lawService.mapToResponseDTOWithSize(law);
        return ResponseEntity.ok(lawDTO);
    }

    // Read and Filter
    @GetMapping
    public ResponseEntity<PaginatedResponseDTO<LawResponseDTO>> searchLaws(
            LawSearchCriteriaDTO criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort
    ) {
        Page<Law> result = lawService.searchLaws(criteria, page, size, sort);

        List<LawResponseDTO> laws = result.getContent()
                .stream()
                .map(lawService::mapToResponseDTOWithSize)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                new PaginatedResponseDTO<>(
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

    // check law attachment
    @GetMapping("/by-sequence-number/{sequenceNumber}")
    public ResponseEntity<AttachmentExistDTO> checkLawAttachmentBySequenceNumber(
            @PathVariable Long sequenceNumber) {

        Optional<Law> lawOpt = lawService.findBySequenceNumber(sequenceNumber)
                .stream()
                .findFirst();

        if (lawOpt.isPresent()) {
            Law law = lawOpt.get();

            boolean exists = law.getAttachment() != null
                    && !law.getAttachment().isBlank();

            return ResponseEntity.ok(new AttachmentExistDTO(exists));
        }

        return ResponseEntity.ok(new AttachmentExistDTO(false));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteLaw(@PathVariable Long id) {
        String actor = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // 1️⃣ Find the law before deleting
        Law law = lawService.findByIdEntity(id);

        // 2️⃣ Delete the law and attachment
        lawService.deleteById(id);

        // 3️⃣ Log activity
        activityLogService.logActivity(
                "Law",
                law.getId(),
                "DELETE",
                "Law deleted with sequence number: " + law.getSequenceNumber(),
                actor
        );

        return ResponseEntity.ok("Law deleted successfully with ID: " + id);
    }

    // Search By Title
    @GetMapping("/search/byTitle")
    public ResponseEntity<?> searchByTitle(@RequestParam String title) {
        List<LawResponseDTO> results = lawService.searchByTitle(title);
        return ResponseEntity.ok(results);
    }

    // Search By Exact Title
        @GetMapping("/search/exact-title")
    public ResponseEntity<?> searchByExactTitle(@RequestParam String title) {
        return ResponseEntity.ok(lawService.findByExactTitle(title));
    }

    // Report and Statistic
    @GetMapping("/reports/law-status-counts")
    public ResponseEntity<Map<Status, Long>> getLawStatusCounts() {
        return ResponseEntity.ok(lawService.getLawCountsByStatus());
    }

    @GetMapping("/reports/law-type-counts")
    public ResponseEntity<Map<LawType, Long>> getLawTypeCounts() {
        return ResponseEntity.ok(lawService.getLawCountsByType());
    }

    @GetMapping("/reports/law-type-status")
    public ResponseEntity<Map<LawType, Map<Status, Long>>> getTypeStatusReport() {
        return ResponseEntity.ok(lawService.getTypeStatusReport());
    }

    // Endpoint: /api/laws/summary?year=2025&month=12
    @GetMapping("/reports/summary")
    public LawSummaryReportDTO getLawSummary(
            @RequestParam int year,
            @RequestParam(required = false) Integer month) {
        return lawService.getLawSummaryByYearAndMonth(year, month);
    }

    // File Helper
    @GetMapping("/download_attachment/{id}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        Resource resource = fileStorageService.loadLawAttachmentById(id);

        // Use application/octet-stream for download
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/view_attachment/{id}")
    public ResponseEntity<Resource> viewAttachment(@PathVariable Long id) {
        Resource resource = fileStorageService.loadLawAttachmentById(id);

        // Detect PDF automatically
        MediaType mediaType = MediaType.APPLICATION_PDF;
        String filename = resource.getFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

}
