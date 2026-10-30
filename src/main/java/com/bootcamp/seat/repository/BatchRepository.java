package com.bootcamp.seat.repository;

import com.bootcamp.seat.domain.Batch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    Optional<Batch> findByName(String name);

    boolean existsByName(String name);

    List<Batch> findAllByOrderByIdAsc();

    /** 같은 기수의 좌석 위치 변경을 직렬화하기 위한 행 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.id = :id")
    Optional<Batch> findByIdForUpdate(Long id);
}
