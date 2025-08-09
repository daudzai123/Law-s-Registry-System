package com.mcit.repo;

import com.mcit.entity.Law;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LawRepository extends JpaRepository<Law, Long>, JpaSpecificationExecutor<Law> {

    boolean existsBySequenceNumber(Long sequenceNumber);

    boolean existsByTitleEngIgnoreCase(String titleEng);

    boolean existsByTitlePsIgnoreCase(String titlePs);

    boolean existsByTitleDrIgnoreCase(String titleDr);

}
