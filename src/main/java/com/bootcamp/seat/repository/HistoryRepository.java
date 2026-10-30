package com.bootcamp.seat.repository;

import com.bootcamp.seat.domain.History;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HistoryRepository extends JpaRepository<History, Long>, JpaSpecificationExecutor<History> {
}
