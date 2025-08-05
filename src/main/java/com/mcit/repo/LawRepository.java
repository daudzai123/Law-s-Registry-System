package com.mcit.repo;

import com.mcit.entity.Law;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LawRepository extends JpaRepository<Law, Long> {

    boolean existsBySequenceNumber(Long sequenceNumber);

    boolean existsByTitleEngIgnoreCase(String titleEng);

    boolean existsByTitlePsIgnoreCase(String titlePs);

    boolean existsByTitleDrIgnoreCase(String titleDr);
}
