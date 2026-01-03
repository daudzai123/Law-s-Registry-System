package com.mcit.service;

import com.mcit.dto.LawDTO;
import com.mcit.dto.LawResponseDTO;
import com.mcit.dto.LawSearchCriteriaDTO;
import com.mcit.dto.LawSummaryReportDTO;
import com.mcit.entity.Law;
import com.mcit.entity.User;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.LawRepository;
import com.mcit.repo.UserRepository;
import com.mcit.specification.LawSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LawService {

    private final LawRepository lawRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final DateConversionService dateConversionService;

    public LawDTO addLawFromDTO(LawDTO dto) {

        Law law = new Law();

        law.setType(dto.getType());
        law.setSequenceNumber(dto.getSequenceNumber());
        law.setTitleEng(dto.getTitleEng());
        law.setTitlePs(dto.getTitlePs());
        law.setTitleDr(dto.getTitleDr());
        law.setStatus(dto.getStatus());
        law.setDescription(dto.getDescription());
        law.setAttachment(dto.getAttachment());

        // 🔹 Auto-detect calendar & convert to Hijri-Qamari
        String hijriQamariDate =
                dateConversionService.toHijriQamariAuto(dto.getPublishDate());

        law.setPublishDate(hijriQamariDate);

        law.setUser(
                userRepository.findById(dto.getUserId())
                        .orElseThrow(() -> new IllegalArgumentException("User not found"))
        );

        Law saved = lawRepository.save(law);
        return toDTO(saved);
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
        Sort sortOrder = Sort.by(Sort.Direction.fromString(sort[1]), sort[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);
        return lawRepository.findAll(spec, pageable);
    }

    // Search By Title
    public List<LawResponseDTO> searchByTitle(String title) {
        List<LawResponseDTO> results = lawRepository.searchByTitle(title)
                .stream()
                .map(this::mapToResponseDTOWithSize)
                .toList();

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("No law found with title containing: " + title);
        }

        return results;
    }

    // Find By Exact Title
    public List<LawResponseDTO> findByExactTitle(String title) {
        List<Law> laws = lawRepository.findByExactTitle(title);

        if (laws.isEmpty()) {
            throw new ResourceNotFoundException("No law found with the exact given title.");
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
        if (updates.getSequenceNumber() != null)
            existing.setSequenceNumber(updates.getSequenceNumber());

        if (updates.getTitleEng() != null)
            existing.setTitleEng(updates.getTitleEng());

        if (updates.getTitlePs() != null)
            existing.setTitlePs(updates.getTitlePs());

        if (updates.getTitleDr() != null)
            existing.setTitleDr(updates.getTitleDr());

        if (updates.getType() != null)
            existing.setType(updates.getType());

        if (updates.getStatus() != null)
            existing.setStatus(updates.getStatus());

        if (updates.getDescription() != null)
            existing.setDescription(updates.getDescription());

        if (updates.getAttachment() != null) {
            // Just update the path in DB
            existing.setAttachment(updates.getAttachment());
        }

        // 🔹 Convert publishDate to Hijri-Qamari if provided
        if (updates.getPublishDate() != null && !updates.getPublishDate().isBlank()) {
            String hijriQamariDate =
                    dateConversionService.toHijriQamariAuto(updates.getPublishDate());
            existing.setPublishDate(hijriQamariDate);
        }

        if (updates.getUserId() != null) {
            User user = userRepository.findById(updates.getUserId())
                    .orElseThrow(() ->
                            new RuntimeException("User not found with id: " + updates.getUserId()));
            existing.setUser(user);
        }

        // 3️⃣ Save & return updated DTO
        Law saved = lawRepository.saveAndFlush(existing);
        return toDTO(saved);
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

        // 2️⃣ Delete the attachment if exists
        if (law.getAttachment() != null && !law.getAttachment().isBlank()) {
            fileStorageService.deleteLawAttachment(law.getAttachment().replace("laws/", ""));
        }

        // 3️⃣ Delete the law record
        lawRepository.deleteById(id);
    }

    // Report and Statistic
    public Map<Status, Long> getLawCountsByStatus() {
        return lawRepository.countLawsByStatus()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Status) row[0],
                        row -> (Long) row[1]
                ));
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

        List<Object[]> typeResults = (month != null) ?
                lawRepository.countLawsByTypeForYearAndMonth(yearMonth) :
                lawRepository.countLawsByTypeForYear(yearStr);

        typeResults.forEach(r -> {
            LawType type = (LawType) r[0];   // cast directly to LawType
            Long count = ((Number) r[1]).longValue();
            byType.put(type, count);
        });

        dto.setByType(byType);


        // ---------- By Status ----------
        Map<Status, Long> byStatus = Arrays.stream(Status.values())
                .collect(Collectors.toMap(s -> s, s -> 0L));

        // Fetch all laws for the period and count status in Java
        List<Law> laws = (month != null) ?
                lawRepository.findAllByPublishDateStartingWith(yearMonth) :
                lawRepository.findAllByPublishDateStartingWith(yearStr);

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
                    byTypeStatus.get(law.getType()).get(law.getStatus()) + 1
            );
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

    // Mappers
    private LawDTO toDTO(Law law) {
        LawDTO dto = new LawDTO();
        dto.setId(law.getId());
        dto.setSequenceNumber(law.getSequenceNumber());
        dto.setTitleEng(law.getTitleEng());
        dto.setTitlePs(law.getTitlePs());
        dto.setTitleDr(law.getTitleDr());
        dto.setType(law.getType());
        dto.setStatus(law.getStatus());
        dto.setDescription(law.getDescription());
        dto.setAttachment(law.getAttachment());
        dto.setUserId(law.getUser().getId());
        dto.setPublishDate(law.getPublishDate());
        dto.setCreateDate(law.getCreateDate());
        dto.setUpdateDate(law.getUpdateDate());
        return dto;
    }

    private LawResponseDTO mapToResponseDTO(Law law) {
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
        dto.setCreateDate(law.getCreateDate());
        dto.setUpdateDate(law.getUpdateDate());
        if (law.getUser() != null) dto.setUserId(law.getUser().getId());
        return dto;
    }

    public LawResponseDTO mapToResponseDTOWithSize(Law law) {
        LawResponseDTO dto = mapToResponseDTO(law);
        if (law.getAttachment() != null && !law.getAttachment().isBlank()) {
            dto.setAttachmentSize(
                    fileStorageService.getFormattedFileSize(law.getAttachment().replace("laws/", ""), false)
            );
        } else {
            dto.setAttachmentSize(null);
        }
        return dto;
    }

    // In LawService
    public Law findByIdEntity(Long id) {
        return lawRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Law not found with id: " + id));
    }

}

