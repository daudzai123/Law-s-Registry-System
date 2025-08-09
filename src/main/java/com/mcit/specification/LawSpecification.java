package com.mcit.specification;

import com.mcit.dto.LawSearchCriteriaDTO;
import com.mcit.entity.Law;
import com.mcit.enums.Status;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

public class LawSpecification {

    public static Specification<Law> filterByCriteria(LawSearchCriteriaDTO criteria) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        return (Root<Law> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 🟢 Filter by Law Type
            if (criteria.getType() != null) {
                predicates.add(cb.equal(root.get("type"), criteria.getType()));
            }

            // 🟢 Filter by Sequence Number
            if (criteria.getSequenceNumber() != null) {
                predicates.add(cb.equal(root.get("sequenceNumber"), criteria.getSequenceNumber()));
            }

            // 🟢 Filter by Title in English
            if (criteria.getTitleEng() != null) {
                predicates.add(cb.like(cb.lower(root.get("titleEng")), "%" + criteria.getTitleEng().toLowerCase() + "%"));
            }

            // 🟢 Filter by Title in Pashto
            if (criteria.getTitlePs() != null) {
                predicates.add(cb.like(cb.lower(root.get("titlePs")), "%" + criteria.getTitlePs().toLowerCase() + "%"));
            }

            // 🟢 Filter by Title in Dari
            if (criteria.getTitleDr() != null) {
                predicates.add(cb.like(cb.lower(root.get("titleDr")), "%" + criteria.getTitleDr().toLowerCase() + "%"));
            }

            // 🟢 Filter by Publish Date
            if (criteria.getPublishDate() != null) {
                predicates.add(cb.equal(root.get("publishDate"), criteria.getPublishDate()));
            }

            // 🟢 Filter by Status
            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            // 🟢 Filter by Created By User
            if (criteria.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), criteria.getUserId()));
            }

            // 🟢 Sort by Most Recent First
            query.orderBy(cb.desc(root.get("publishDate")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
