package com.mcit.repo;

import com.mcit.entity.ActivityLog;
import com.mcit.specification.ActivityLogCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog,Long> {

    @Query("SELECT a FROM ActivityLog a " +
            "WHERE (:entityName is null or a.entityName = :entityName) " +
            "AND (:action is null or a.action = :action) " +
            "AND (:userName is null or a.userName = :userName) " +
            "AND (:search is null or LOWER(a.userName) LIKE %:search% OR LOWER(a.action) LIKE %:search% OR LOWER(a.entityName) LIKE %:search%)")
    Page<ActivityLog> filterActivityLogs(
            @Param("entityName") String entityName,
            @Param("action") String action,
            @Param("userName") String userName,
            @Param("search") String search,
            Pageable pageable
    );

    default Specification<ActivityLog> buildSpecification(ActivityLogCriteria criteria){
        return (Root<ActivityLog> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getEntityName() != null && !criteria.getEntityName().isEmpty()) {
                predicates.add(cb.like(root.get("entityName"), "%" + criteria.getEntityName() + "%"));
            }
            if (criteria.getAction() != null && !criteria.getAction().isEmpty()) {
                predicates.add(cb.like(root.get("action"), "%" + criteria.getAction() + "%"));
            }
            if (criteria.getLogsStartDate() != null && criteria.getLogsEndDate() != null) {
                predicates.add(cb.between(root.get("timestamp"),
                        criteria.getLogsStartDate().atStartOfDay(),
                        criteria.getLogsEndDate().atTime(23, 59, 59)));
            }
            if (criteria.getSearchTerm() != null && !criteria.getSearchTerm().isEmpty()) {
                String likeTerm = "%" + criteria.getSearchTerm() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("entityName"), likeTerm),
                        cb.like(root.get("action"), likeTerm),
                        cb.like(root.get("userName"), likeTerm)
                ));
            }

            query.where(cb.and(predicates.toArray(new Predicate[0])));
            return query.getRestriction();
        };
    }

    Page<ActivityLog> findAll(Specification<ActivityLog> spec, Pageable pageable);
}
