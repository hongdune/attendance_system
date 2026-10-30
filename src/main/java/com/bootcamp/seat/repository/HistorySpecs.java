package com.bootcamp.seat.repository;

import com.bootcamp.seat.domain.History;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class HistorySpecs {

    private HistorySpecs() {
    }

    /** 비어 있는 조건은 무시한다. 기간은 [from, to] 양끝 포함, 수강생명은 부분 일치. */
    public static Specification<History> filter(String batchName, String studentName,
                                                LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (batchName != null && !batchName.isBlank()) {
                predicates.add(cb.equal(root.get("batchName"), batchName));
            }
            if (studentName != null && !studentName.isBlank()) {
                String pattern = "%" + escapeLike(studentName.trim()) + "%";
                predicates.add(cb.like(root.get("studentName"), pattern, '\\'));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
