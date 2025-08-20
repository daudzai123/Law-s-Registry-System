package com.mcit.service;

import com.mcit.config.HijriShamsiConverter;
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

import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LawService {

    private final LawRepository lawRepository;
    private final MyUserRepository userRepository;
    private final FileStorageService fileStorageService;

        public LawDTO addLawFromDTO(LawDTO dto) {
            // Duplicate checks
            if (dto.getSequenceNumber() != null && lawRepository.existsBySequenceNumber(dto.getSequenceNumber())) {
                throw new DuplicateLawException("Sequence number '" + dto.getSequenceNumber() + "' already exists.");
            }
            if (dto.getTitleEng() != null && lawRepository.existsByTitleEngIgnoreCase(dto.getTitleEng())) {
                throw new DuplicateLawException("English title '" + dto.getTitleEng() + "' already exists.");
            }
            if (dto.getTitlePs() != null && lawRepository.existsByTitlePsIgnoreCase(dto.getTitlePs())) {
                throw new DuplicateLawException("Pashto title '" + dto.getTitlePs() + "' already exists.");
            }
            if (dto.getTitleDr() != null && lawRepository.existsByTitleDrIgnoreCase(dto.getTitleDr())) {
                throw new DuplicateLawException("Dari title '" + dto.getTitleDr() + "' already exists.");
            }

            // ✅ Convert Hijri → Gregorian before saving
            if (dto.getPublishDateHijri() != null) {
                String[] parts = dto.getPublishDateHijri().split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);

                LocalDate miladiDate = HijriShamsiConverter.shamsiToGregorian(year, month, day);
                dto.setPublishDate(miladiDate);
            }

            Law law = dtoToEntity(dto);

            // Ensure user exists
            if (dto.getUserId() != null) {
                MyUser user = userRepository.findById(dto.getUserId())
                        .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + dto.getUserId()));
                law.setUser(user);
            } else {
                throw new IllegalArgumentException("User id is required.");
            }

            Law saved = lawRepository.save(law);
            return toDTO(saved);
        }


    public Page<Law> searchLaws(LawSearchCriteriaDTO criteria, int page, int size, String[] sort) {
        Specification<Law> spec = LawSpecification.filterByCriteria(criteria);

        Sort sortOrder = Sort.by(
                Sort.Direction.fromString(sort[1]),
                sort[0]
        );

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        return lawRepository.findAll(spec, pageable);
    }

    // Optional: other service methods (findById, delete, etc.)
    public LawDTO findByIdAsDTO(Long id) {
        return lawRepository.findById(id).map(this::toDTO).orElse(null);
    }

    @Transactional
    public void deleteById(Long id) {
        lawRepository.deleteById(id);
    }

    public LawDTO toDTO(Law law) {
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
        dto.setUserId(law.getUser() != null ? law.getUser().getId() : null);

        // Set Gregorian date
        dto.setPublishDate(law.getPublishDate());

        // ✅ Convert Gregorian to Hijri for response
        if (law.getPublishDate() != null) {
            dto.setPublishDateHijri(HijriShamsiConverter.gregorianToShamsi(law.getPublishDate()));
        }

        return dto;
    }


    public Law dtoToEntity(LawDTO dto) {
        if (dto == null) return null;
        Law law = new Law();
        // Do not set ID if you want it auto-generated on create; but if updating, you may set it
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
        // user is set separately using userRepository to avoid lazy initialization problems
        return law;
    }

    public List<LawDTO> findAllAsDTO() {
        return lawRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LawDTO partialUpdateLaw(Long id, LawDTO updates) {
        Law existingLaw = lawRepository.findById(id).orElse(null);
        if (existingLaw == null) return null;

        // Check duplicates if sequenceNumber/titleEng/titlePs/titleDr updated
        if (updates.getSequenceNumber() != null && !updates.getSequenceNumber().equals(existingLaw.getSequenceNumber())) {
            if (lawRepository.existsBySequenceNumber(updates.getSequenceNumber())) {
                throw new DuplicateLawException("Sequence number '" + updates.getSequenceNumber() + "' already exists.");
            }
            existingLaw.setSequenceNumber(updates.getSequenceNumber());
        }
        if (updates.getTitleEng() != null && !updates.getTitleEng().equalsIgnoreCase(existingLaw.getTitleEng())) {
            if (lawRepository.existsByTitleEngIgnoreCase(updates.getTitleEng())) {
                throw new DuplicateLawException("English title '" + updates.getTitleEng() + "' already exists.");
            }
            existingLaw.setTitleEng(updates.getTitleEng());
        }
        if (updates.getTitlePs() != null && !updates.getTitlePs().equalsIgnoreCase(existingLaw.getTitlePs())) {
            if (lawRepository.existsByTitlePsIgnoreCase(updates.getTitlePs())) {
                throw new DuplicateLawException("Pashto title '" + updates.getTitlePs() + "' already exists.");
            }
            existingLaw.setTitlePs(updates.getTitlePs());
        }
        if (updates.getTitleDr() != null && !updates.getTitleDr().equalsIgnoreCase(existingLaw.getTitleDr())) {
            if (lawRepository.existsByTitleDrIgnoreCase(updates.getTitleDr())) {
                throw new DuplicateLawException("Dari title '" + updates.getTitleDr() + "' already exists.");
            }
            existingLaw.setTitleDr(updates.getTitleDr());
        }

        // Update other fields only if non-null
        if (updates.getType() != null) existingLaw.setType(updates.getType());
        if (updates.getStatus() != null) existingLaw.setStatus(updates.getStatus());
        if (updates.getDescription() != null) existingLaw.setDescription(updates.getDescription());
        if (updates.getAttachment() != null) existingLaw.setAttachment(updates.getAttachment());

        // For user update (optional):
        if (updates.getUserId() != null && (existingLaw.getUser() == null || !updates.getUserId().equals(existingLaw.getUser().getId()))) {
            MyUser user = userRepository.findById(updates.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + updates.getUserId()));
            existingLaw.setUser(user);
        }

        Law saved = lawRepository.save(existingLaw);
        return toDTO(saved);
    }

    public String getAttachmentFilename(Long id) {
        return lawRepository.findById(id)
                .map(Law::getAttachment)
                .orElse(null);
    }

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

    // Change year parameter to int
    public Map<LawType, Long> getReportByYear(int year) {
        return convertToMap(lawRepository.countLawsByTypeForYear(year));
    }

    // Change year and month parameters to int
    public Map<LawType, Long> getReportByMonthAndYear(int year, int month) {
        return convertToMap(lawRepository.countLawsByTypeForYearAndMonth(year, month));
    }

    private Map<LawType, Long> convertToMap(List<Object[]> results) {
        Map<LawType, Long> counts = Arrays.stream(LawType.values())
                .collect(Collectors.toMap(type -> type, type -> 0L));

        results.forEach(row -> counts.put((LawType) row[0], (Long) row[1]));

        return counts;
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
        dto.setPublishDateHijri(HijriShamsiConverter.gregorianToShamsi(law.getPublishDate()));

        dto.setStatus(law.getStatus());
        dto.setDescription(law.getDescription());
        dto.setAttachment(law.getAttachment());
        dto.setAttachmentSize(fileStorageService.getFormattedFileSize(law.getAttachment()));

        if (law.getUser() != null) {
            dto.setUserId(law.getUser().getId());
        }

        return dto;
    }

    public List<LawResponseDTO> searchByTitle(String title) {
        List<Law> laws = lawRepository.searchByTitle(title);
        return laws.stream()
                .map(this::mapToDTO)
                .toList();
    }



}
