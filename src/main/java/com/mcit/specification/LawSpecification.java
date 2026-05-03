package com.mcit.specification;

import com.mcit.dto.LawSearchCriteriaDTO;
import com.mcit.entity.Law;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

public class LawSpecification {

    public static Specification<Law> filterByCriteria(LawSearchCriteriaDTO c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Handle type filtering
            List<LawType> effectiveTypes = c.getEffectiveTypes();
            
            if (effectiveTypes != null && !effectiveTypes.isEmpty()) {
                // If types are specified, filter by those types
                CriteriaBuilder.In<LawType> inClause = cb.in(root.get("type"));
                for (LawType lawType : effectiveTypes) {
                    inClause.value(lawType);
                }
                predicates.add(inClause);
            }
            // If effectiveTypes is null -> return ALL types (no type filter)

            // Other filters
            if (c.getSequenceNumber() != null)
                predicates.add(cb.equal(root.get("sequenceNumber"), c.getSequenceNumber()));

            if (c.getTitleEng() != null && !c.getTitleEng().isBlank())
                predicates.add(cb.like(cb.lower(root.get("titleEng")), "%" + c.getTitleEng().toLowerCase() + "%"));

            if (c.getTitlePs() != null && !c.getTitlePs().isBlank())
                predicates.add(cb.like(cb.lower(root.get("titlePs")), "%" + c.getTitlePs().toLowerCase() + "%"));

            if (c.getTitleDr() != null && !c.getTitleDr().isBlank())
                predicates.add(cb.like(cb.lower(root.get("titleDr")), "%" + c.getTitleDr().toLowerCase() + "%"));

            if (c.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), c.getStatus()));

            if (c.getPublishDate() != null && !c.getPublishDate().isBlank())
                predicates.add(cb.equal(root.get("publishDate"), c.getPublishDate()));

            if (c.getUserId() != null)
                predicates.add(cb.equal(root.get("user").get("id"), c.getUserId()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}