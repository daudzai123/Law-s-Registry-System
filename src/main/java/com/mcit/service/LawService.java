package com.mcit.service;

import com.mcit.entity.Law;
import com.mcit.exception.DuplicateLawException;
import com.mcit.repo.LawRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LawService {

    private final LawRepository lawRepository;

    public LawService(LawRepository lawRepository) {
        this.lawRepository = lawRepository;
    }

    public List<Law> findAll() {
        return lawRepository.findAll();
    }

    public Optional<Law> findById(Long id) {
        return lawRepository.findById(id);
    }

    public void deleteById(Long id) {
        lawRepository.deleteById(id);
    }

    @Transactional
    public Law saveLaw(Law law) {
        // Duplicate checks
        if (lawRepository.existsBySequenceNumber(law.getSequenceNumber())) {
            throw new DuplicateLawException("Sequence number '" + law.getSequenceNumber() + "' already exists.");
        }
        if (lawRepository.existsByTitleEngIgnoreCase(law.getTitleEng())) {
            throw new DuplicateLawException("English title '" + law.getTitleEng() + "' already exists.");
        }
        if (lawRepository.existsByTitlePsIgnoreCase(law.getTitlePs())) {
            throw new DuplicateLawException("Pashto title '" + law.getTitlePs() + "' already exists.");
        }
        if (lawRepository.existsByTitleDrIgnoreCase(law.getTitleDr())) {
            throw new DuplicateLawException("Dari title '" + law.getTitleDr() + "' already exists.");
        }

        return lawRepository.save(law);
    }
}
