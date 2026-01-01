package com.mcit.specification;

import com.mcit.dto.LawSearchCriteriaDTO;
import com.mcit.entity.Law;
import com.mcit.enums.Status;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

public class LawSpecification {

    public static Specification<Law> filterByCriteria(LawSearchCriteriaDTO c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (c.getType() != null)
                predicates.add(cb.equal(root.get("type"), c.getType()));

            if (c.getSequenceNumber() != null)
                predicates.add(cb.equal(root.get("sequenceNumber"), c.getSequenceNumber()));

            if (c.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), c.getStatus()));

            if (c.getPublishDate() != null && !c.getPublishDate().isBlank())
                predicates.add(cb.equal(root.get("publishDate"), c.getPublishDate()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
