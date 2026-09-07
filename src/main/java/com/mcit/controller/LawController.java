package com.mcit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.IslamicCalendar;
import com.mcit.dto.*;
import com.mcit.entity.Law;
import com.mcit.entity.LawAttachment;
import com.mcit.entity.User;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import com.mcit.exception.DuplicateLawException;
import com.mcit.exception.FileStorageException;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.LawAttachmentRepository;
import com.mcit.repo.LawRepository;
import com.mcit.repo.UserRepository;
import com.mcit.service.ActivityLogService;
import com.mcit.service.FileStorageService;
import com.mcit.service.LawService;
import io.swagger.v3.core.util.ObjectMapperFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
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
    private final LawAttachmentRepository lawAttachmentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final ActivityLogService activityLogService;
    public record HijriDefaultResponse(int year, int month) {}

    // Add new law
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addLaw(
            @RequestPart("law") String lawJson,
            @RequestPart(value = "attachment", required = false) MultipartFile attachmentFile,
            @RequestPart(value = "attachmentEng", required = false) MultipartFile attachmentEngFile,
            @RequestPart(value = "attachmentPs", required = false) MultipartFile attachmentPsFile,
            @RequestPart(value = "attachmentAr", required = false) MultipartFile attachmentArFile) {
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

            // Check if the law type requires multiple attachments
            boolean isMultiAttachmentType = isMultiAttachmentLawType(lawDTO.getType());
            boolean isSingleAttachmentType = isSingleAttachmentLawType(lawDTO.getType());

            // Prepare attachments list
            List<AttachmentDTO> attachments = new ArrayList<>();

            if (isMultiAttachmentType) {
                // Handle 3 attachments (English, Pashto, Arabic)
                // Check if existing law with same sequence number has attachments
                List<Law> existingLaws = lawRepository.findBySequenceNumber(lawDTO.getSequenceNumber());
                Optional<Law> existingLawWithAttachments = existingLaws.stream().findFirst();

                if (existingLawWithAttachments.isPresent()) {
                    // Check if attachments exist in attachment table
                    List<LawAttachment> existingAttachments = lawAttachmentRepository
                            .findByLawId(existingLawWithAttachments.get().getId());

                    if (!existingAttachments.isEmpty()) {
                        // Reuse existing attachments from attachment table
                        for (LawAttachment existingAtt : existingAttachments) {
                            AttachmentDTO attachDTO = new AttachmentDTO();
                            attachDTO.setLanguage(existingAtt.getLanguage());
                            attachDTO.setFilePath(existingAtt.getFilePath());
                            attachDTO.setFileName(existingAtt.getFileName());
                            attachDTO.setFileSize(existingAtt.getFileSize());
                            attachDTO.setMimeType(existingAtt.getMimeType());
                            attachments.add(attachDTO);
                        }
                    } else {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "No attachments found for existing law"));
                    }
                } else {
                    // Require all 3 attachments for new law
                    if (attachmentPsFile == null || attachmentPsFile.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Pashto attachment is required"));
                    }

                    try {
                        // ✅ Pashto (REQUIRED)
                        fileStorageService.validatePdfFile(attachmentPsFile);
                        String savedPsFile = fileStorageService.saveLawAttachment(attachmentPsFile);

                        AttachmentDTO psAttach = new AttachmentDTO();
                        psAttach.setLanguage("ps");
                        psAttach.setFilePath(savedPsFile);
                        psAttach.setFileName(attachmentPsFile.getOriginalFilename());
                        psAttach.setFileSize(attachmentPsFile.getSize());
                        psAttach.setMimeType(attachmentPsFile.getContentType());
                        attachments.add(psAttach);

                        // ✅ English (OPTIONAL)
                        if (attachmentEngFile != null && !attachmentEngFile.isEmpty()) {
                            fileStorageService.validatePdfFile(attachmentEngFile);
                            String savedEngFile = fileStorageService.saveLawAttachment(attachmentEngFile);

                            AttachmentDTO engAttach = new AttachmentDTO();
                            engAttach.setLanguage("eng");
                            engAttach.setFilePath(savedEngFile);
                            engAttach.setFileName(attachmentEngFile.getOriginalFilename());
                            engAttach.setFileSize(attachmentEngFile.getSize());
                            engAttach.setMimeType(attachmentEngFile.getContentType());
                            attachments.add(engAttach);
                        }

                        // ✅ Arabic (OPTIONAL)
                        if (attachmentArFile != null && !attachmentArFile.isEmpty()) {
                            fileStorageService.validatePdfFile(attachmentArFile);
                            String savedArFile = fileStorageService.saveLawAttachment(attachmentArFile);

                            AttachmentDTO arAttach = new AttachmentDTO();
                            arAttach.setLanguage("ar");
                            arAttach.setFilePath(savedArFile);
                            arAttach.setFileName(attachmentArFile.getOriginalFilename());
                            arAttach.setFileSize(attachmentArFile.getSize());
                            arAttach.setMimeType(attachmentArFile.getContentType());
                            attachments.add(arAttach);
                        }

                    } catch (FileStorageException ex) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", ex.getMessage()));
                    }
                }
            } else if (isSingleAttachmentType) {
                // Handle single attachment
               Optional<Law> existingLawWithAttachment = Optional.empty();

// ✅ ONLY apply sequence logic for types that actually use it
if (lawDTO.getType() != LawType.AHKAM_AND_FRAMIN &&
    lawDTO.getType() != LawType.MAJMOA_OF_LAW) {

    List<Law> existingLaws = lawRepository.findBySequenceNumber(lawDTO.getSequenceNumber());
    existingLawWithAttachment = existingLaws.stream().findFirst();
}

                if (existingLawWithAttachment.isPresent()) {
                    // Check attachment table first
                    List<LawAttachment> existingAttachments = lawAttachmentRepository
                            .findByLawId(existingLawWithAttachment.get().getId());

                    if (!existingAttachments.isEmpty()) {
                        for (LawAttachment existingAtt : existingAttachments) {
                            AttachmentDTO attachDTO = new AttachmentDTO();
                            attachDTO.setLanguage(existingAtt.getLanguage());
                            attachDTO.setFilePath(existingAtt.getFilePath());
                            attachDTO.setFileName(existingAtt.getFileName());
                            attachDTO.setFileSize(existingAtt.getFileSize());
                            attachDTO.setMimeType(existingAtt.getMimeType());
                            attachments.add(attachDTO);
                        }
                    } else {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "No attachment found for existing law"));
                    }
                } else {
                    // New attachment required
                    if (attachmentFile == null || attachmentFile.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Attachment file is required"));
                    }

                    try {
                        fileStorageService.validatePdfFile(attachmentFile);
                        String savedFile = fileStorageService.saveLawAttachment(attachmentFile);

                        AttachmentDTO attachDTO = new AttachmentDTO();
                        attachDTO.setLanguage("default");
                        attachDTO.setFilePath(savedFile);
                        attachDTO.setFileName(attachmentFile.getOriginalFilename());
                        attachDTO.setFileSize(attachmentFile.getSize());
                        attachDTO.setMimeType(attachmentFile.getContentType());
                        attachments.add(attachDTO);

                    } catch (FileStorageException ex) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", ex.getMessage()));
                    }
                }
            }

            // Set attachments in DTO
            lawDTO.setAttachments(attachments);

            // 4️⃣ Save law
            LawDTO savedLaw = lawService.addLawFromDTO(lawDTO);

            String message;
            if (savedLaw.getType() == LawType.AHKAM_AND_FRAMIN ||
                    savedLaw.getType() == LawType.MAJMOA_OF_LAW) {
                message = savedLaw.getType().getDisplayName()
                        + " created with title: " + savedLaw.getTitleEng();
            } else {
                message = savedLaw.getType().getDisplayName()
                        + " created with sequence number: " + savedLaw.getSequenceNumber()
                        + " and title: " + savedLaw.getTitleEng();
            }

            // ✅ ACTIVITY LOG
            activityLogService.logActivity(
                    "Law",
                    savedLaw.getId(),
                    "CREATE",
                    message,
                    actor);

            return ResponseEntity.status(HttpStatus.CREATED).body(savedLaw);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateLaw(
            @PathVariable Long id,
            @RequestPart("law") String lawJson,
            @RequestPart(value = "attachment", required = false) MultipartFile attachmentFile,
            @RequestPart(value = "attachmentEng", required = false) MultipartFile attachmentEngFile,
            @RequestPart(value = "attachmentPs", required = false) MultipartFile attachmentPsFile,
            @RequestPart(value = "attachmentAr", required = false) MultipartFile attachmentArFile) {
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

            // 4️⃣ Handle attachment logic based on law type
            boolean isMultiAttachmentType = isMultiAttachmentLawType(existingLaw.getType());
            boolean isSingleAttachmentType = isSingleAttachmentLawType(existingLaw.getType());

            List<AttachmentDTO> attachments = new ArrayList<>();

            if (isMultiAttachmentType) {
                // Handle 3 attachments update
                boolean hasAnyAttachment = (attachmentEngFile != null && !attachmentEngFile.isEmpty()) ||
                        (attachmentPsFile != null && !attachmentPsFile.isEmpty()) ||
                        (attachmentArFile != null && !attachmentArFile.isEmpty());

                if (hasAnyAttachment) {

    List<LawAttachment> existingAttachments = lawAttachmentRepository.findByLawId(id);

    boolean hasExistingPs = existingAttachments.stream()
            .anyMatch(att -> "ps".equalsIgnoreCase(att.getLanguage()));

    boolean hasNewPs = (attachmentPsFile != null && !attachmentPsFile.isEmpty());

    if (!hasExistingPs && !hasNewPs) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Pashto attachment is required"));
    }

    try {
        // ✅ Pashto
        if (hasNewPs) {
            fileStorageService.validatePdfFile(attachmentPsFile);
            String savedPsFile = fileStorageService.saveLawAttachment(attachmentPsFile);

            AttachmentDTO psAttach = new AttachmentDTO();
            psAttach.setLanguage("ps");
            psAttach.setFilePath(savedPsFile);
            psAttach.setFileName(attachmentPsFile.getOriginalFilename());
            psAttach.setFileSize(attachmentPsFile.getSize());
            psAttach.setMimeType(attachmentPsFile.getContentType());
            attachments.add(psAttach);
        } else {
            // ✅ keep existing ps
            existingAttachments.stream()
                .filter(att -> "ps".equalsIgnoreCase(att.getLanguage()))
                .forEach(att -> attachments.add(mapToDTO(att)));
        }

        // ✅ English
        if (attachmentEngFile != null && !attachmentEngFile.isEmpty()) {
            fileStorageService.validatePdfFile(attachmentEngFile);
            String savedEngFile = fileStorageService.saveLawAttachment(attachmentEngFile);

            AttachmentDTO engAttach = new AttachmentDTO();
            engAttach.setLanguage("eng");
            engAttach.setFilePath(savedEngFile);
            engAttach.setFileName(attachmentEngFile.getOriginalFilename());
            engAttach.setFileSize(attachmentEngFile.getSize());
            engAttach.setMimeType(attachmentEngFile.getContentType());
            attachments.add(engAttach);
        } else {
            existingAttachments.stream()
                .filter(att -> "eng".equalsIgnoreCase(att.getLanguage()))
                .forEach(att -> attachments.add(mapToDTO(att)));
        }

        // ✅ Arabic
        if (attachmentArFile != null && !attachmentArFile.isEmpty()) {
            fileStorageService.validatePdfFile(attachmentArFile);
            String savedArFile = fileStorageService.saveLawAttachment(attachmentArFile);

            AttachmentDTO arAttach = new AttachmentDTO();
            arAttach.setLanguage("ar");
            arAttach.setFilePath(savedArFile);
            arAttach.setFileName(attachmentArFile.getOriginalFilename());
            arAttach.setFileSize(attachmentArFile.getSize());
            arAttach.setMimeType(attachmentArFile.getContentType());
            attachments.add(arAttach);
        } else {
            existingAttachments.stream()
                .filter(att -> "ar".equalsIgnoreCase(att.getLanguage()))
                .forEach(att -> attachments.add(mapToDTO(att)));
        }

    } catch (FileStorageException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", ex.getMessage()));
    }
} else {
                    // Keep existing attachments from attachment table
                    List<LawAttachment> existingAttachments = lawAttachmentRepository.findByLawId(id);
                    for (LawAttachment existingAtt : existingAttachments) {
                        AttachmentDTO attachDTO = new AttachmentDTO();
                        attachDTO.setLanguage(existingAtt.getLanguage());
                        attachDTO.setFilePath(existingAtt.getFilePath());
                        attachDTO.setFileName(existingAtt.getFileName());
                        attachDTO.setFileSize(existingAtt.getFileSize());
                        attachDTO.setMimeType(existingAtt.getMimeType());
                        attachments.add(attachDTO);
                    }
                }
            } else if (isSingleAttachmentType) {
                // Handle single attachment update
                if (attachmentFile != null && !attachmentFile.isEmpty()) {
                    // Validate new attachment
                    fileStorageService.validatePdfFile(attachmentFile);

                    // Save new attachment
                    String savedFile = fileStorageService.saveLawAttachment(attachmentFile);

                    AttachmentDTO attachDTO = new AttachmentDTO();
                    attachDTO.setLanguage("default");
                    attachDTO.setFilePath(savedFile);
                    attachDTO.setFileName(attachmentFile.getOriginalFilename());
                    attachDTO.setFileSize(attachmentFile.getSize());
                    attachDTO.setMimeType(attachmentFile.getContentType());
                    attachments.add(attachDTO);
                } else {
                    // Keep existing attachments from attachment table
                    List<LawAttachment> existingAttachments = lawAttachmentRepository.findByLawId(id);
                    for (LawAttachment existingAtt : existingAttachments) {
                        AttachmentDTO attachDTO = new AttachmentDTO();
                        attachDTO.setLanguage(existingAtt.getLanguage());
                        attachDTO.setFilePath(existingAtt.getFilePath());
                        attachDTO.setFileName(existingAtt.getFileName());
                        attachDTO.setFileSize(existingAtt.getFileSize());
                        attachDTO.setMimeType(existingAtt.getMimeType());
                        attachments.add(attachDTO);
                    }
                }
            }

            // Set attachments in DTO
            lawDTO.setAttachments(attachments);

            // 5️⃣ Update the specific law DTO fields
            LawDTO updated = lawService.updateLawFromDTO(id, lawDTO);

            // ✅ ACTIVITY LOG
            activityLogService.logActivity(
                    "Law",
                    updated.getId(),
                    "UPDATE",
                    "Law updated with sequence number: " + updated.getSequenceNumber(),
                    actor);

            return ResponseEntity.ok(updated);

        } catch (DuplicateLawException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Helper method to check if law type requires multiple attachments
    private boolean isMultiAttachmentLawType(LawType lawType) {
        return lawType == LawType.JARIDA ||
                lawType == LawType.OSOLNAMA ||
                lawType == LawType.BUSINESS_ADS ||
                lawType == LawType.NIZAMNAMA;
    }

    // Helper method to check if law type requires single attachment
    private boolean isSingleAttachmentLawType(LawType lawType) {
        return lawType == LawType.MAJMOA_OF_LAW ||
                lawType == LawType.AHKAM_AND_FRAMIN;
    }

    // Read Law with attachment size
    @GetMapping("/{id}")
    public ResponseEntity<?> getLawById(@PathVariable Long id) {
        Law law = lawService.findByIdEntity(id);
        LawResponseDTO lawDTO = lawService.mapToResponseDTOWithSize(law);
        return ResponseEntity.ok(lawDTO);
    }

    // Read and Filter
    @GetMapping
    public ResponseEntity<PaginatedResponseDTO<LawResponseDTO>> searchLaws(
            LawSearchCriteriaDTO criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createDate,desc") String[] sort,
            @RequestParam(required = false) List<LawType> type // Add this to accept multiple type params
    ) {
        // If multiple types are provided in query string, set them in criteria
        if (type != null && !type.isEmpty()) {
            criteria.setTypes(type);
        }

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
                        result.hasPrevious()));
    }

    // Check law attachment
    @GetMapping("/by-sequence-number/{sequenceNumber}")
    public ResponseEntity<AttachmentExistDTO> checkLawAttachmentBySequenceNumber(
            @PathVariable Long sequenceNumber,
            @RequestParam(required = false) String language) {

        Optional<Law> lawOpt = lawService.findBySequenceNumber(sequenceNumber)
                .stream()
                .findFirst();

        if (lawOpt.isPresent()) {
            Law law = lawOpt.get();
            boolean exists = false;

            // Check attachment table
            List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(law.getId());

            if (!attachments.isEmpty()) {
                if (language != null) {
                    exists = attachments.stream()
                            .anyMatch(a -> a.getLanguage().equalsIgnoreCase(language));
                } else if (isMultiAttachmentLawType(law.getType())) {
                    // Check if all three languages exist
                    boolean hasPs = attachments.stream()
                            .anyMatch(a -> a.getLanguage().equalsIgnoreCase("ps"));

                    exists = hasPs; // only Pashto required
                } else {
                    exists = true;
                }
            }

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

        // 2️⃣ Delete the law and attachment(s)
        lawService.deleteById(id);

        // 3️⃣ Log activity
        activityLogService.logActivity(
                "Law",
                law.getId(),
                "DELETE",
                "Law deleted with sequence number: " + law.getSequenceNumber(),
                actor);

        return ResponseEntity.ok("Law deleted successfully with ID: " + id);
    }

    // Search By Title

    @GetMapping("/search/byTitle")
    public ResponseEntity<?> searchByTitle(
            @RequestParam String title,
            @RequestParam(required = false) LawType type) {

        return ResponseEntity.ok(
                lawService.searchByTitle(title, type));
    }

    // Search By Exact Title
    @GetMapping("/search/exact-title")
    public ResponseEntity<List<LawResponseDTO>> findByExactTitle(
            @RequestParam String title,
            @RequestParam(required = false) LawType type) {
        return ResponseEntity.ok(
                lawService.findByExactTitle(title, type));
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
    @GetMapping("/reports/default-filters")
public HijriDefaultResponse getDefaults() {

    IslamicCalendar hijri = new IslamicCalendar();

    return new HijriDefaultResponse(
        hijri.get(Calendar.YEAR),
        hijri.get(Calendar.MONTH) + 1
    );
}

    // File Helper
    @GetMapping("/download_attachment/{id}")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Long attachmentId) {

        Resource resource;

        if (attachmentId != null) {
            // Download by attachment ID
            resource = fileStorageService.loadLawAttachmentByAttachmentId(attachmentId);
        } else {
            // Download by law ID and language
            resource = fileStorageService.loadLawAttachmentById(id, language);
        }

        // Use application/octet-stream for download
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/view_attachment/{id}")
    public ResponseEntity<Resource> viewAttachment(
            @PathVariable Long id,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Long attachmentId) {

        Resource resource;

        if (attachmentId != null) {
            // View by attachment ID
            resource = fileStorageService.loadLawAttachmentByAttachmentId(attachmentId);
        } else {
            // View by law ID and language
            resource = fileStorageService.loadLawAttachmentById(id, language);
        }

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

    // New endpoint to get all attachments for a law
    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<AttachmentDTO>> getLawAttachments(@PathVariable Long id) {
        List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(id);
        List<AttachmentDTO> attachmentDTOs = attachments.stream()
                .map(this::toAttachmentDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(attachmentDTOs);
    }


    //new endpoint for hide and show law
     @GetMapping("/public")
public ResponseEntity<PaginatedResponseDTO<LawResponseDTO>> getPublicLaws(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createDate,desc") String[] sort,
        @RequestParam(required = false) LawType type) {
    
    // Match your existing pattern
    Sort sortOrder = Sort.by(new Sort.Order(Sort.Direction.fromString(sort[1]), sort[0]).nullsLast())
            .and(Sort.by(Sort.Direction.DESC, "id"));
    Pageable pageable = PageRequest.of(page, size, sortOrder);
    
    Page<LawResponseDTO> result = lawService.getPublicLaws(pageable, type);
    
    return ResponseEntity.ok(
        new PaginatedResponseDTO<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext(),
            result.hasPrevious()));
}

// NEW: Public search endpoint (without authentication)
@GetMapping("/public/search/byTitle")
public ResponseEntity<List<LawResponseDTO>> searchPublicByTitle(
        @RequestParam String title,
        @RequestParam(required = false) LawType type) {
    
    List<LawResponseDTO> results = lawService.searchPublicByTitle(title, type);
    return ResponseEntity.ok(results);
}

//public search by sequence number
@GetMapping("/public/search/by-sequence-number/{sequenceNumber}")
public ResponseEntity<List<LawResponseDTO>> searchPublicBySequenceNumber(
        @PathVariable Long sequenceNumber,
        @RequestParam(required = false) LawType type) {
    
    List<LawResponseDTO> results = lawService.searchPublicBySequenceNumber(sequenceNumber, type);
    return ResponseEntity.ok(results);
}


// Toggle visibility endpoint (with authentication)
// In LawController.java - Update toggleVisibility
@PatchMapping("/{id}/visibility")
public ResponseEntity<?> toggleVisibility(
        @PathVariable Long id,
        @RequestParam boolean isPublic) {
    try {
        String actor = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        
        LawDTO updatedLaw = lawService.toggleVisibility(id, isPublic);
        
        // Log the activity
        activityLogService.logActivity(
            "Law",
            id,
            "VISIBILITY_TOGGLE",
            "Law visibility changed to: " + (isPublic ? "PUBLIC" : "HIDDEN"),
            actor);
        
        // Return the updated law with the new isPublic value
        return ResponseEntity.ok(updatedLaw);
        
    } catch (ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update visibility: " + e.getMessage()));
    }
}

    // New endpoint to delete a specific attachment
    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long attachmentId) {
        String actor = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        LawAttachment attachment = lawAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        Long lawId = attachment.getLaw().getId();

        // Delete file from storage
        fileStorageService.deleteLawAttachment(attachment);

        // Delete from database
        lawAttachmentRepository.delete(attachment);

        activityLogService.logActivity(
                "Law",
                lawId,
                "DELETE_ATTACHMENT",
                "Attachment deleted for law ID: " + lawId,
                actor);

        return ResponseEntity.ok(Map.of("message", "Attachment deleted successfully"));
    }



    private AttachmentDTO toAttachmentDTO(LawAttachment attachment) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setId(attachment.getId());
        dto.setLanguage(attachment.getLanguage());
        dto.setFilePath(attachment.getFilePath());
        dto.setFileName(attachment.getFileName());
        dto.setFileSize(attachment.getFileSize());
        dto.setMimeType(attachment.getMimeType());
        dto.setIsPrimary(attachment.getIsPrimary());
        dto.setVersion(attachment.getVersion());
        dto.setUploadDate(attachment.getUploadDate());
        return dto;
    }
    private AttachmentDTO mapToDTO(LawAttachment att) {
    AttachmentDTO dto = new AttachmentDTO();
    dto.setLanguage(att.getLanguage());
    dto.setFilePath(att.getFilePath());
    dto.setFileName(att.getFileName());
    dto.setFileSize(att.getFileSize());
    dto.setMimeType(att.getMimeType());
    return dto;
}
}
