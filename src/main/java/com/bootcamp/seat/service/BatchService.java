package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.repository.BatchRepository;
import com.bootcamp.seat.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BatchService {

    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;

    public BatchService(BatchRepository batchRepository, StudentRepository studentRepository) {
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
    }

    public List<Batch> findAll() {
        return batchRepository.findAllByOrderByIdAsc();
    }

    public Batch getByName(String name) {
        return batchRepository.findByName(name)
                .orElseThrow(() -> SeatException.notFound("'" + name + "' 기수를 찾을 수 없습니다."));
    }

    /** 이름으로 찾고, 없으면 첫 번째 기수. 기수가 하나도 없으면 empty. */
    public Optional<Batch> findByNameOrFirst(String name) {
        if (name != null && !name.isBlank()) {
            Optional<Batch> found = batchRepository.findByName(name);
            if (found.isPresent()) {
                return found;
            }
        }
        return findAll().stream().findFirst();
    }

    public long countStudents(Batch batch) {
        return studentRepository.countByBatch(batch);
    }

    @Transactional
    public Batch create(String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.length() < Batch.NAME_MIN || name.length() > Batch.NAME_MAX) {
            throw SeatException.badRequest("기수명은 " + Batch.NAME_MIN + "~" + Batch.NAME_MAX + "자로 입력해주세요.");
        }
        if (batchRepository.existsByName(name)) {
            throw SeatException.conflict("이미 존재하는 기수명입니다: " + name);
        }
        return batchRepository.save(new Batch(name));
    }

    @Transactional
    public void delete(Long id) {
        Batch batch = batchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> SeatException.notFound("기수를 찾을 수 없습니다."));
        if (studentRepository.countByBatch(batch) > 0) {
            throw SeatException.conflict("수강생이 있는 기수는 삭제할 수 없습니다. 먼저 모든 수강생을 삭제해주세요.");
        }
        batchRepository.delete(batch);
    }
}
