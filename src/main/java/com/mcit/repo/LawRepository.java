package com.mcit.repo;

import com.mcit.entity.Law;
import com.mcit.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LawRepository extends JpaRepository<Law, Long>, JpaSpecificationExecutor<Law> {

    boolean existsBySequenceNumber(Long sequenceNumber);

    boolean existsByTitleEngIgnoreCase(String titleEng);

    boolean existsByTitlePsIgnoreCase(String titlePs);

    boolean existsByTitleDrIgnoreCase(String titleDr);

    long countByStatus(Status status);

    // Count laws grouped by status (no change)
    @Query("SELECT l.status, COUNT(l) FROM Law l GROUP BY l.status")
    List<Object[]> countLawsByStatus();

    // Count laws grouped by type (no change)
    @Query("SELECT l.type, COUNT(l) FROM Law l GROUP BY l.type")
    List<Object[]> countLawsByType();

    // Count laws grouped by type for a given year
    @Query("SELECT l.type, COUNT(l) " +
            "FROM Law l " +
            "WHERE YEAR(l.publishDate) = :year " +
            "GROUP BY l.type")
    List<Object[]> countLawsByTypeForYear(@Param("year") int year);

    // Count laws grouped by type for a given year and month
    @Query("SELECT l.type, COUNT(l) " +
            "FROM Law l " +
            "WHERE YEAR(l.publishDate) = :year " +
            "  AND MONTH(l.publishDate) = :month " +
            "GROUP BY l.type")
    List<Object[]> countLawsByTypeForYearAndMonth(@Param("year") int year,
                                                  @Param("month") int month);




}
