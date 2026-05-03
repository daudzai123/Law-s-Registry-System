package com.mcit.service;

import com.mcit.dto.LawDTO;
import com.mcit.dto.LawResponseDTO;
import com.mcit.dto.LawSearchCriteriaDTO;
import com.mcit.dto.LawSummaryReportDTO;
import com.mcit.dto.AttachmentDTO;
import com.mcit.entity.Law;
import com.mcit.entity.LawAttachment;
import com.mcit.entity.User;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.LawRepository;
import com.mcit.repo.LawAttachmentRepository;
import com.mcit.repo.UserRepository;
import com.mcit.specification.LawSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LawService {

    private final LawRepository lawRepository;
    private final LawAttachmentRepository lawAttachmentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final DateConversionService dateConversionService;

    public LawDTO addLawFromDTO(LawDTO dto) {

        Law law = new Law();

        law.setType(dto.getType());

        // Logic for MAJMOA_OF_LAW and AHKAM_AND_FRAMIN (No sequence number, use collection)
        if (dto.getType() == LawType.MAJMOA_OF_LAW || dto.getType() == LawType.AHKAM_AND_FRAMIN) {
            law.setSequenceNumber(null);
            law.setCollection(dto.getCollection());
            law.setCollection2(dto.getCollection2());
        }
        // Logic for EXISTING types (Keep sequence number)
        else {
            law.setSequenceNumber(dto.getSequenceNumber());
        }

        law.setTitleEng(dto.getTitleEng());
        law.setTitlePs(dto.getTitlePs());
        law.setTitleDr(dto.getTitleDr());
        law.setStatus(dto.getStatus());
        law.setDescriptionEng(dto.getDescriptionEng());
        law.setDescriptionPs(dto.getDescriptionPs());
        law.setDescriptionDr(dto.getDescriptionDr());

        String inputDate = dto.getPublishDate();

        if (isAlreadyIslamicQamari(inputDate)) {
            law.setPublishDate(inputDate);
        } else {
            String converted = dateConversionService.toHijriQamariAuto(inputDate);
            law.setPublishDate(converted);
        }

        law.setUser(
                userRepository.findById(dto.getUserId())
                        .orElseThrow(() -> new IllegalArgumentException("User not found")));

        // Save law first
        Law saved = lawRepository.save(law);
        
        // Handle attachments if present in DTO
        if (dto.getAttachments() != null && !dto.getAttachments().isEmpty()) {
            for (AttachmentDTO attachDTO : dto.getAttachments()) {
                LawAttachment attachment = new LawAttachment();
                attachment.setLaw(saved);
                attachment.setLanguage(attachDTO.getLanguage());
                attachment.setFilePath(attachDTO.getFilePath());
                attachment.setFileName(attachDTO.getFileName());
                attachment.setFileSize(attachDTO.getFileSize());
                attachment.setMimeType(attachDTO.getMimeType());
                attachment.setIsPrimary(attachDTO.getIsPrimary() != null ? attachDTO.getIsPrimary() : false);
                attachment.setVersion(attachDTO.getVersion() != null ? attachDTO.getVersion() : 1);
                attachment.setUploadedBy(saved.getUser());
                
                lawAttachmentRepository.save(attachment);
            }
        }
        
        return toDTO(saved);
    }

    // Helper method to check if law type requires multiple attachments
    private boolean isMultiAttachmentLawType(LawType lawType) {
        return lawType == LawType.JARIDA || 
               lawType == LawType.OSOLNAMA || 
               lawType == LawType.BUSINESS_ADS || 
               lawType == LawType.NIZAMNAMA;
    }

    // Read
    public LawDTO findByIdAsDTO(Long id) {
        return lawRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Law not found with id: " + id));
    }

// Search and Filter
public Page<Law> searchLaws(LawSearchCriteriaDTO criteria, int page, int size, String[] sort) {
    Specification<Law> spec = LawSpecification.filterByCriteria(criteria);
    
    // Remove the exclusion logic - now handled by the specification
    // The specification will return:
    // - If types specified: only those types
    // - If no types specified: ALL 6 types
    
    Sort sortOrder = Sort.by(Sort.Direction.fromString(sort[1]), sort[0]);
    Pageable pageable = PageRequest.of(page, size, sortOrder);
    
    return lawRepository.findAll(spec, pageable);
}

    // Search By Title
public List<LawResponseDTO> searchByTitle(String title, LawType type) {

    List<Law> laws = lawRepository.searchByTitle(title, type);

    if (laws.isEmpty()) {
        return List.of(); // better for public search
    }

    return laws.stream()
            .map(this::mapToResponseDTOWithSize)
            .toList();
}
    // Find By Exact Title
    public List<LawResponseDTO> findByExactTitle(String title, LawType type) {
        List<Law> laws = lawRepository.findByExactTitleFlexible(title, type);

        if (laws.isEmpty()) {
            throw new ResourceNotFoundException("No law found with the given title.");
        }

        return laws.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public LawDTO updateLawFromDTO(Long id, LawDTO updates) {

        // 1️⃣ Find existing law
        Law existing = lawRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Law not found with id: " + id));

        // 2️⃣ Update simple mutable fields only

        // Handle sequence number vs collection based on type
        if (updates.getType() != null) {
            existing.setType(updates.getType());
        }

        if (existing.getType() == LawType.MAJMOA_OF_LAW || existing.getType() == LawType.AHKAM_AND_FRAMIN) {
            existing.setSequenceNumber(null);
            if (updates.getCollection() != null) {
                existing.setCollection(updates.getCollection());
            }
        } else {
            if (updates.getSequenceNumber() != null) {
                existing.setSequenceNumber(updates.getSequenceNumber());
            }
        }

        if (updates.getTitleEng() != null)
            existing.setTitleEng(updates.getTitleEng());

        if (updates.getTitlePs() != null)
            existing.setTitlePs(updates.getTitlePs());

        if (updates.getTitleDr() != null)
            existing.setTitleDr(updates.getTitleDr());

        if (updates.getStatus() != null)
            existing.setStatus(updates.getStatus());

        if (updates.getDescriptionEng() != null)
            existing.setDescriptionEng(updates.getDescriptionEng());
        if (updates.getCollection2() != null)
            existing.setCollection2(updates.getCollection2());
        if(updates.getCollection() != null)
            existing.setCollection(updates.getCollection());

        if (updates.getDescriptionPs() != null)
            existing.setDescriptionPs(updates.getDescriptionPs());

        if (updates.getDescriptionDr() != null)
            existing.setDescriptionDr(updates.getDescriptionDr());

        if (updates.getPublishDate() != null
                && !updates.getPublishDate().isBlank()) {

            String inputDate = updates.getPublishDate();

            if (isAlreadyIslamicQamari(inputDate)) {
                existing.setPublishDate(inputDate);
            } else {
                String hijriQamariDate = dateConversionService.toHijriQamariAuto(inputDate);
                existing.setPublishDate(hijriQamariDate);
            }
        }

        if (updates.getUserId() != null) {
            User user = userRepository.findById(updates.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + updates.getUserId()));
            existing.setUser(user);
        }

        // 3️⃣ Save law
        Law saved = lawRepository.saveAndFlush(existing);
        
        // 4️⃣ Handle attachments if provided
     if (updates.getAttachments() != null) {

    if (saved.getSequenceNumber() != null) {

        List<Law> sameSequenceLaws =
                lawRepository.findBySequenceNumber(saved.getSequenceNumber());

        for (Law law : sameSequenceLaws) {

            // delete old attachments
            lawAttachmentRepository.deleteByLawId(law.getId());

            // add new attachments
            for (AttachmentDTO attachDTO : updates.getAttachments()) {

                LawAttachment attachment = new LawAttachment();

                attachment.setLaw(law);
                attachment.setLanguage(attachDTO.getLanguage());
                attachment.setFilePath(attachDTO.getFilePath());
                attachment.setFileName(attachDTO.getFileName());
                attachment.setFileSize(attachDTO.getFileSize());
                attachment.setMimeType(attachDTO.getMimeType());
                attachment.setIsPrimary(
                    attachDTO.getIsPrimary() != null
                        ? attachDTO.getIsPrimary()
                        : false
                );

                attachment.setVersion(
                    attachDTO.getVersion() != null
                        ? attachDTO.getVersion()
                        : 1
                );

                attachment.setUploadedBy(saved.getUser());

                lawAttachmentRepository.save(attachment);
            }
        }

    } else {

        // if no sequence number update only one law
        lawAttachmentRepository.deleteByLawId(saved.getId());

        for (AttachmentDTO attachDTO : updates.getAttachments()) {

            LawAttachment attachment = new LawAttachment();

            attachment.setLaw(saved);
            attachment.setLanguage(attachDTO.getLanguage());
            attachment.setFilePath(attachDTO.getFilePath());
            attachment.setFileName(attachDTO.getFileName());
            attachment.setFileSize(attachDTO.getFileSize());
            attachment.setMimeType(attachDTO.getMimeType());

            lawAttachmentRepository.save(attachment);
        }
    }
}
        
        return toDTO(saved);
    }
    
    // Update only attachments
public LawDTO updateLawAttachments(Long lawId, List<AttachmentDTO> attachments) {

    Law law = lawRepository.findById(lawId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Law not found with id: " + lawId));

    List<Law> lawsToUpdate;

    // If has sequence number update all same sequence
    if (law.getSequenceNumber() != null) {
        lawsToUpdate =
                lawRepository.findBySequenceNumber(law.getSequenceNumber());
    } else {
        lawsToUpdate = List.of(law);
    }

    for (Law item : lawsToUpdate) {

        lawAttachmentRepository.deleteByLawId(item.getId());

        for (AttachmentDTO attachDTO : attachments) {

            LawAttachment attachment = new LawAttachment();

            attachment.setLaw(item);
            attachment.setLanguage(attachDTO.getLanguage());
            attachment.setFilePath(attachDTO.getFilePath());
            attachment.setFileName(attachDTO.getFileName());
            attachment.setFileSize(attachDTO.getFileSize());
            attachment.setMimeType(attachDTO.getMimeType());
            attachment.setIsPrimary(
                    attachDTO.getIsPrimary() != null
                            ? attachDTO.getIsPrimary()
                            : false);

            attachment.setVersion(
                    attachDTO.getVersion() != null
                            ? attachDTO.getVersion()
                            : 1);

            attachment.setUploadedBy(item.getUser());

            lawAttachmentRepository.save(attachment);
        }
    }

    return toDTO(law);
}

    // find by sequence number
    public List<Law> findBySequenceNumber(Long sequenceNumber) {
        return lawRepository.findBySequenceNumber(sequenceNumber);
    }

    // Delete
    public void deleteById(Long id) {
        // 1️⃣ Find the law first
        Law law = lawRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Law not found with id: " + id));

        // 2️⃣ Delete all attachments from storage
        List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(id);
       for (LawAttachment attachment : attachments) {
    String filePath = attachment.getFilePath();

    if (filePath != null && !filePath.isBlank()) {

        // 🔍 Check if file is used by other laws
        long usageCount = lawAttachmentRepository.countByFilePath(filePath);

        // ✅ Only delete if this is the LAST usage
        if (usageCount <= 1) {
            fileStorageService.deleteLawAttachment(filePath);
        }
    }
}

        // 3️⃣ Delete the law record (attachments will be deleted automatically due to cascade)
        lawRepository.deleteById(id);
    }

    // Report and Statistic
    public Map<Status, Long> getLawCountsByStatus() {
        return lawRepository.countLawsByStatus()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Status) row[0],
                        row -> (Long) row[1]));
    }

    // summary report for year and month
    public Map<LawType, Map<Status, Long>> getTypeStatusReport() {
        Map<LawType, Map<Status, Long>> result = new EnumMap<>(LawType.class);

        for (LawType type : LawType.values()) {
            Map<Status, Long> statusMap = new EnumMap<>(Status.class);
            for (Status status : Status.values()) {
                statusMap.put(status, 0L);
            }
            result.put(type, statusMap);
        }

        lawRepository.countByTypeAndStatus()
                .forEach(row -> result.get((LawType) row[0]).put((Status) row[1], (Long) row[2]));

        return result;
    }

    public LawSummaryReportDTO getLawSummaryByYearAndMonth(int year, Integer month) {
        String yearStr = String.valueOf(year);
        String yearMonth = month != null ? String.format("%s-%02d", yearStr, month) : null;

        LawSummaryReportDTO dto = new LawSummaryReportDTO();
        dto.setYear(year);
        dto.setMonth(month);

        // ---------- By Type ----------
        Map<LawType, Long> byType = Arrays.stream(LawType.values())
                .collect(Collectors.toMap(t -> t, t -> 0L));

        List<Object[]> typeResults = (month != null) ? lawRepository.countLawsByTypeForYearAndMonth(yearMonth)
                : lawRepository.countLawsByTypeForYear(yearStr);

        typeResults.forEach(r -> {
            LawType type = (LawType) r[0];
            Long count = ((Number) r[1]).longValue();
            byType.put(type, count);
        });

        dto.setByType(byType);

        // ---------- By Status ----------
        Map<Status, Long> byStatus = Arrays.stream(Status.values())
                .collect(Collectors.toMap(s -> s, s -> 0L));

        List<Law> laws = (month != null) ? lawRepository.findAllByPublishDateStartingWith(yearMonth)
                : lawRepository.findAllByPublishDateStartingWith(yearStr);

        laws.forEach(law -> {
            Status s = law.getStatus();
            byStatus.put(s, byStatus.getOrDefault(s, 0L) + 1);
        });

        dto.setByStatus(byStatus);

        // ---------- By Type + Status ----------
        Map<LawType, Map<Status, Long>> byTypeStatus = new EnumMap<>(LawType.class);
        for (LawType type : LawType.values()) {
            Map<Status, Long> statusMap = new EnumMap<>(Status.class);
            for (Status status : Status.values()) {
                statusMap.put(status, 0L);
            }
            byTypeStatus.put(type, statusMap);
        }

        laws.forEach(law -> {
            byTypeStatus.get(law.getType()).put(
                    law.getStatus(),
                    byTypeStatus.get(law.getType()).get(law.getStatus()) + 1);
        });

        dto.setByTypeAndStatus(byTypeStatus);

        return dto;
    }

    public Map<LawType, Long> getLawCountsByType() {
        return convertToMap(lawRepository.countLawsByType());
    }

    private Map<LawType, Long> convertToMap(List<Object[]> results) {
        Map<LawType, Long> counts = Arrays.stream(LawType.values())
                .collect(Collectors.toMap(t -> t, t -> 0L));

        results.forEach(row -> counts.put((LawType) row[0], (Long) row[1]));
        return counts;
    }

    // Mappers - Only using attachments array, no backward compatibility
    private LawDTO toDTO(Law law) {
        LawDTO dto = new LawDTO();
        dto.setId(law.getId());
        dto.setSequenceNumber(law.getSequenceNumber());
        dto.setCollection(law.getCollection());
        dto.setCollection2(law.getCollection2());
        dto.setTitleEng(law.getTitleEng());
        dto.setTitlePs(law.getTitlePs());
        dto.setTitleDr(law.getTitleDr());
        dto.setType(law.getType());
        dto.setStatus(law.getStatus());
        dto.setDescriptionEng(law.getDescriptionEng());
        dto.setDescriptionPs(law.getDescriptionPs());
        dto.setDescriptionDr(law.getDescriptionDr());
        dto.setUserId(law.getUser().getId());
        dto.setPublishDate(law.getPublishDate());
        dto.setCreateDate(law.getCreateDate());
        dto.setUpdateDate(law.getUpdateDate());
        dto.setPublic(law.isPublic());
        
        // Map attachments from attachment table only
        List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(law.getId());
        List<AttachmentDTO> attachmentDTOs = attachments.stream()
                .map(this::toAttachmentDTO)
                .collect(Collectors.toList());
        dto.setAttachments(attachmentDTOs);
        
        return dto;
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

    private LawResponseDTO mapToResponseDTO(Law law) {
        LawResponseDTO dto = new LawResponseDTO();
        dto.setId(law.getId());
        dto.setType(law.getType());
        dto.setSequenceNumber(law.getSequenceNumber());
        dto.setCollection(law.getCollection());
        dto.setCollection2(law.getCollection2());
        dto.setTitleEng(law.getTitleEng());
        dto.setTitlePs(law.getTitlePs());
        dto.setTitleDr(law.getTitleDr());
        dto.setPublishDate(law.getPublishDate());
        dto.setStatus(law.getStatus());
        dto.setDescriptionEng(law.getDescriptionEng());
        dto.setDescriptionPs(law.getDescriptionPs());
        dto.setDescriptionDr(law.getDescriptionDr());
        dto.setCreateDate(law.getCreateDate());
        dto.setUpdateDate(law.getUpdateDate());
        dto.setPublic(law.isPublic());
        
        if (law.getUser() != null)
            dto.setUserId(law.getUser().getId());
        
        // Map attachments from attachment table only
        List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(law.getId());
        List<AttachmentDTO> attachmentDTOs = attachments.stream()
                .map(this::toAttachmentDTO)
                .collect(Collectors.toList());
        dto.setAttachments(attachmentDTOs);
        
        return dto;
    }

    public LawResponseDTO mapToResponseDTOWithSize(Law law) {
        LawResponseDTO dto = mapToResponseDTO(law);
        
        List<LawAttachment> attachments = lawAttachmentRepository.findByLawId(law.getId());
        
        if (!attachments.isEmpty()) {
            // Calculate total size of all attachments
            long totalSize = 0;
            for (LawAttachment attachment : attachments) {
                Long size = getFileSizeInBytes(attachment.getFilePath());
                if (size != null) totalSize += size;
            }
            
            if (totalSize > 0) {
                double sizeInMB = totalSize / (1024.0 * 1024.0);
                dto.setAttachmentSize(String.format("%.2f MB", sizeInMB));
            } else {
                dto.setAttachmentSize(null);
            }
        } else {
            dto.setAttachmentSize(null);
        }
        
        return dto;
    }

    // Helper method to get file size in bytes
    private Long getFileSizeInBytes(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        try {
            Path filePathObj = fileStorageService.getLawAttachmentPath().resolve(filePath.replace("laws/", ""));
            return Files.size(filePathObj);
        } catch (IOException e) {
            return null;
        }
    }
    
    // In LawService
    public Law findByIdEntity(Long id) {
        return lawRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Law not found with id: " + id));
    }

    private boolean isAlreadyIslamicQamari(String date) {
        if (date == null)
            return false;

        // Detect formats like: 1447-01-06, 1446-09-20
        return date.matches("144\\d-\\d{2}-\\d{2}");
    }

  

// Toggle visibility
// In LawService.java
@Transactional
public LawDTO toggleVisibility(Long id, boolean isPublic) {
    Law law = lawRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Law not found with id: " + id));
    


    // Update the field
    law.setPublic(isPublic);
    
    // Save and flush immediately
    Law updatedLaw = lawRepository.saveAndFlush(law);
    
    // Verify the value was saved (for debugging)
    System.out.println("Saved law ID: " + updatedLaw.getId() + ", isPublic: " + updatedLaw.isPublic());
    
    // Return DTO with the updated value
    return toDTO(updatedLaw);
}

// Get public laws with pagination
public Page<LawResponseDTO> getPublicLaws(Pageable pageable, LawType type) {
    Page<Law> laws;
    
    if (type != null) {
        laws = lawRepository.findAllPublicByType(type, pageable);
    } else {
        laws = lawRepository.findAllPublic(pageable);
    }
    
    return laws.map(this::mapToResponseDTOWithSize);
}

// Search public laws by title
public List<LawResponseDTO> searchPublicByTitle(String title, LawType type) {
    List<Law> laws = lawRepository.searchPublicByTitle(title, type);
    return laws.stream()
        .map(this::mapToResponseDTOWithSize)
        .collect(Collectors.toList());
}

// Search public laws by sequence number
public List<LawResponseDTO> searchPublicBySequenceNumber(Long sequenceNumber, LawType type) {

    List<Law> laws = lawRepository.searchPublicBySequenceNumber(sequenceNumber, type);

    return laws.stream()
        .map(this::mapToResponseDTOWithSize)
        .collect(Collectors.toList());
}
}