package com.mcit.repo;

import com.mcit.entity.Law;
import com.mcit.enums.LawType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface LawRepository extends JpaRepository<Law, Long>, JpaSpecificationExecutor<Law> {

    List<Law> findBySequenceNumber(Long sequenceNumber);

    /* ===============================
       Search Methods
       =============================== */
    @Query("SELECT l FROM Law l WHERE LOWER(l.titleEng) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "OR LOWER(l.titlePs) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "OR LOWER(l.titleDr) LIKE LOWER(CONCAT('%', :title, '%'))")
    List<Law> searchByTitle(@Param("title") String title);

    @Query("SELECT l FROM Law l WHERE l.titleEng = :title OR l.titlePs = :title OR l.titleDr = :title")
    List<Law> findByExactTitle(@Param("title") String title);


    /* ===============================
       Reporting / Statistics
       =============================== */
    @Query("SELECT l.status, COUNT(l) FROM Law l GROUP BY l.status")
    List<Object[]> countLawsByStatus();

    @Query("SELECT l.type, l.status, COUNT(l) FROM Law l GROUP BY l.type, l.status")
    List<Object[]> countByTypeAndStatus();

    @Query("SELECT l.type, COUNT(l) FROM Law l GROUP BY l.type")
    List<Object[]> countLawsByType();

    // Count laws by type for a year string (like '1448' or '2025')
    @Query("SELECT l.type, COUNT(l) FROM Law l " +
            "WHERE l.publishDate LIKE CONCAT(:year, '%') " +
            "GROUP BY l.type")
    List<Object[]> countLawsByTypeForYear(@Param("year") String year);

    // Count laws by type for a year and month string (like '1448-09')
    @Query("SELECT l.type, COUNT(l) FROM Law l " +
            "WHERE l.publishDate LIKE CONCAT(:yearMonth, '%') " +
            "GROUP BY l.type")
    List<Object[]> countLawsByTypeForYearAndMonth(@Param("yearMonth") String yearMonth);

    // Count laws by type for a quarter using string-based filtering (must be handled in Java)
    default List<Object[]> countByYearAndQuarter(String year, int quarter) {
        // Pull all laws of that year
        List<Law> laws = findAllByPublishDateStartingWith(year);
        // Filter by quarter in Java
        int startMonth = (quarter - 1) * 3 + 1;
        int endMonth = startMonth + 2;

        // Map type -> count
        Map<String, Long> map = laws.stream()
                .filter(law -> {
                    try {
                        String[] parts = law.getPublishDate().split("-");
                        int month = Integer.parseInt(parts[1]);
                        return month >= startMonth && month <= endMonth;
                    } catch (Exception e) {
                        return false; // skip malformed dates
                    }
                })
                .collect(Collectors.groupingBy(law -> law.getType().name(), Collectors.counting()));

        // Convert to Object[] list
        List<Object[]> result = map.entrySet().stream()
                .map(e -> new Object[]{LawType.valueOf(e.getKey()), e.getValue()})
                .toList();

        return result;
    }

    // Helper method
    List<Law> findAllByPublishDateStartingWith(String prefix);

    // Derived search by year+month string
    default List<Law> findByTypeAndYearAndMonth(String type, String yearMonth) {
        return findAllByTypeAndPublishDateStartingWith(type, yearMonth);
    }

    List<Law> findAllByTypeAndPublishDateStartingWith(String type, String yearMonth);

    default long countByTypeAndYearAndMonth(String type, String yearMonth) {
        return findAllByTypeAndPublishDateStartingWith(type, yearMonth).size();
    }

    @Query(value = """
    SELECT l.type, COUNT(*)
    FROM laws l
    WHERE EXTRACT(YEAR FROM TO_DATE(l.publish_date, 'YYYY-MM-DD')) = :year
    GROUP BY l.type
""", nativeQuery = true)
    List<Object[]> countByTypeAndYear(@Param("year") int year);


    @Query(value = """
    SELECT l.type, l.status, COUNT(*)
    FROM laws l
    WHERE EXTRACT(YEAR FROM TO_DATE(l.publish_date, 'YYYY-MM-DD')) = :year
      AND EXTRACT(MONTH FROM TO_DATE(l.publish_date, 'YYYY-MM-DD')) = :month
    GROUP BY l.type, l.status
""", nativeQuery = true)
    List<Object[]> countByTypeStatusYearMonth(
            @Param("year") int year,
            @Param("month") int month
    );

    // Count by Type for given year/month
    @Query("""
           SELECT l.type, COUNT(l)
           FROM Law l
           WHERE (:month IS NULL OR FUNCTION('MONTH', l.publishDate) = :month)
             AND FUNCTION('YEAR', l.publishDate) = :year
           GROUP BY l.type
           """)
    List<Object[]> countByTypeAndDate(@Param("year") int year, @Param("month") Integer month);

    // Count by Status for given year/month
    @Query("""
           SELECT l.status, COUNT(l)
           FROM Law l
           WHERE (:month IS NULL OR FUNCTION('MONTH', l.publishDate) = :month)
             AND FUNCTION('YEAR', l.publishDate) = :year
           GROUP BY l.status
           """)
    List<Object[]> countByStatusAndDate(@Param("year") int year, @Param("month") Integer month);

    // Count by Type + Status for given year/month
    @Query("""
           SELECT l.type, l.status, COUNT(l)
           FROM Law l
           WHERE (:month IS NULL OR FUNCTION('MONTH', l.publishDate) = :month)
             AND FUNCTION('YEAR', l.publishDate) = :year
           GROUP BY l.type, l.status
           """)
    List<Object[]> countByTypeAndStatusAndDate(@Param("year") int year, @Param("month") Integer month);





}
