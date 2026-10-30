package com.bootcamp.seat.repository;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = "batch")
    List<Student> findByBatchOrderByNameAsc(Batch batch);

    @EntityGraph(attributePaths = "batch")
    Optional<Student> findWithBatchById(Long id);

    long countByBatch(Batch batch);
}
