package com.mcit.service;

import com.mcit.dto.LawDTO;
import com.mcit.dto.LawResponseDTO;
import com.mcit.dto.LawSearchCriteriaDTO;
import com.mcit.entity.Law;
import com.mcit.entity.MyUser;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import com.mcit.exception.DuplicateLawException;
import com.mcit.repo.LawRepository;
import com.mcit.repo.MyUserRepository;
import com.mcit.specification.LawSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LawService {

    private final LawRepository lawRepository;
    private final MyUserRepository userRepository;
    private final FileStorageService fileStorageService;

    /* =========================================================
       1️⃣ CREATE
       ========================================================= */

    public LawDTO addLawFromDTO(LawDTO dto) {

        // Duplicate checks
        if (dto.getSequenceNumber() != null &&
                lawRepository.existsBySequenceNumber(dto.getSequenceNumber())) {
            throw new DuplicateLawException(
                    "Sequence number '" + dto.getSequenceNumber() + "' already exists."
            );
        }

        Law law = dtoToEntity(dto);

        // Validate user
        if (dto.getUserId() == null) {
            throw new IllegalArgumentException("User id is required.");
        }

        MyUser user = userRepository.findById(dto.getUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found with id: " + dto.getUserId()
                        )
                );

        law.setUser(user);

        Law saved = lawRepository.save(law);
        return toDTO(saved);
    }

    /* =========================================================
       2️⃣ READ
       ========================================================= */

    public LawDTO findByIdAsDTO(Long id) {
        return lawRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public List<LawDTO> findAllAsDTO() {
        return lawRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /* =========================================================
       3️⃣ SEARCH & FILTER
       ========================================================= */

    public Page<Law> searchLaws(
            LawSearchCriteriaDTO criteria,
            int page,
            int size,
            String[] sort
    ) {

        Specification<Law> spec =
                LawSpecification.filterByCriteria(criteria);

        Sort sortOrder = Sort.by(
                Sort.Direction.fromString(sort[1]),
                sort[0]
        );

        Pageable pageable =
                PageRequest.of(page, size, sortOrder);

        return lawRepository.findAll(spec, pageable);
    }

    public List<LawResponseDTO> searchByTitle(String title) {
        return lawRepository.searchByTitle(title)
                .stream()
                .map(this::mapToResponseDTOWithSize)
                .toList();
    }

    public LawResponseDTO findByExactTitle(String title) {
        Law law = lawRepository.findByExactTitle(title)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No law found with the exact given title."
                        )
                );

        return mapToResponseDTO(law);
    }

    /* =========================================================
       4️⃣ UPDATE
       ========================================================= */

    public LawDTO partialUpdateLaw(Long id, LawDTO updates) {

        Law existing = lawRepository.findById(id).orElse(null);
        if (existing == null) return null;

        // Duplicate checks
        if (updates.getSequenceNumber() != null &&
                !updates.getSequenceNumber().equals(existing.getSequenceNumber())) {

            if (lawRepository.existsBySequenceNumber(updates.getSequenceNumber())) {
                throw new DuplicateLawException(
                        "Sequence number '" + updates.getSequenceNumber() + "' already exists."
                );
            }
            existing.setSequenceNumber(updates.getSequenceNumber());
        }

        if (updates.getTitleEng() != null &&
                !updates.getTitleEng().equalsIgnoreCase(existing.getTitleEng())) {

            if (lawRepository.existsByTitleEngIgnoreCase(updates.getTitleEng())) {
                throw new DuplicateLawException(
                        "English title '" + updates.getTitleEng() + "' already exists."
                );
            }
            existing.setTitleEng(updates.getTitleEng());
        }

        if (updates.getTitlePs() != null &&
                !updates.getTitlePs().equalsIgnoreCase(existing.getTitlePs())) {

            if (lawRepository.existsByTitlePsIgnoreCase(updates.getTitlePs())) {
                throw new DuplicateLawException(
                        "Pashto title '" + updates.getTitlePs() + "' already exists."
                );
            }
            existing.setTitlePs(updates.getTitlePs());
        }

        if (updates.getTitleDr() != null &&
                !updates.getTitleDr().equalsIgnoreCase(existing.getTitleDr())) {

            if (lawRepository.existsByTitleDrIgnoreCase(updates.getTitleDr())) {
                throw new DuplicateLawException(
                        "Dari title '" + updates.getTitleDr() + "' already exists."
                );
            }
            existing.setTitleDr(updates.getTitleDr());
        }

        // Update simple fields
        if (updates.getType() != null) existing.setType(updates.getType());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getAttachment() != null) existing.setAttachment(updates.getAttachment());

        // Update user
        if (updates.getUserId() != null &&
                (existing.getUser() == null ||
                        !updates.getUserId().equals(existing.getUser().getId()))) {

            MyUser user = userRepository.findById(updates.getUserId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "User not found with id: " + updates.getUserId()
                            )
                    );
            existing.setUser(user);
        }

        return toDTO(lawRepository.save(existing));
    }

    /* =========================================================
       5️⃣ DELETE
       ========================================================= */

    public void deleteById(Long id) {
        lawRepository.deleteById(id);
    }

    /* =========================================================
       6️⃣ REPORTS & STATISTICS
       ========================================================= */

    public Map<Status, Long> getLawCountsByStatus() {
        return lawRepository.countLawsByStatus()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Status) row[0],
                        row -> (Long) row[1]
                ));
    }

    public Map<LawType, Long> getLawCountsByType() {
        return lawRepository.countLawsByType()
                .stream()
                .collect(Collectors.toMap(
                        row -> (LawType) row[0],
                        row -> (Long) row[1]
                ));
    }

    public Map<LawType, Long> getReportByYear(int year) {
        return convertToMap(
                lawRepository.countLawsByTypeForYear(year)
        );
    }

    public Map<LawType, Long> getReportByMonthAndYear(int year, int month) {
        return convertToMap(
                lawRepository.countLawsByTypeForYearAndMonth(year, month)
        );
    }

    /* =========================================================
       7️⃣ FILE HELPERS
       ========================================================= */

    public String getAttachmentFilename(Long id) {
        return lawRepository.findById(id)
                .map(Law::getAttachment)
                .orElse(null);
    }

    /* =========================================================
       8️⃣ MAPPERS & PRIVATE HELPERS
       ========================================================= */

    private Map<LawType, Long> convertToMap(List<Object[]> results) {
        Map<LawType, Long> counts =
                Arrays.stream(LawType.values())
                        .collect(Collectors.toMap(t -> t, t -> 0L));

        results.forEach(row ->
                counts.put((LawType) row[0], (Long) row[1])
        );

        return counts;
    }

    private LawDTO toDTO(Law law) {
        if (law == null) return null;

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
        dto.setPublishDate(law.getPublishDate());
        dto.setUserId(
                law.getUser() != null ? law.getUser().getId() : null
        );
        return dto;
    }

    private Law dtoToEntity(LawDTO dto) {
        Law law = new Law();
        law.setId(dto.getId());
        law.setPublishDate(dto.getPublishDate());
        law.setSequenceNumber(dto.getSequenceNumber());
        law.setTitleEng(dto.getTitleEng());
        law.setTitlePs(dto.getTitlePs());
        law.setTitleDr(dto.getTitleDr());
        law.setType(dto.getType());
        law.setStatus(dto.getStatus());
        law.setDescription(dto.getDescription());
        law.setAttachment(dto.getAttachment());
        return law;
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

        if (law.getUser() != null) {
            dto.setUserId(law.getUser().getId());
        }
        return dto;
    }

    private LawResponseDTO mapToResponseDTOWithSize(Law law) {
        LawResponseDTO dto = mapToResponseDTO(law);
        dto.setAttachmentSize(
                fileStorageService.getFormattedFileSize(law.getAttachment())
        );
        return dto;
    }
}
