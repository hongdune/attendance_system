package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.SeatGrid;
import com.bootcamp.seat.domain.Student;
import com.bootcamp.seat.repository.BatchRepository;
import com.bootcamp.seat.repository.StudentRepository;
import com.bootcamp.seat.service.SeatChangedEvent.Type;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 수강생 등록/수정/삭제와 자리 배치. 좌석 위치를 바꾸는 모든 작업은 기수 행을 잠근 뒤 중복을 검사한다.
 */
@Service
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StudentService(StudentRepository studentRepository, BatchRepository batchRepository,
                          ApplicationEventPublisher events, Clock clock) {
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.events = events;
        this.clock = clock;
    }

    public List<Student> findByBatch(Batch batch) {
        return studentRepository.findByBatchOrderByNameAsc(batch);
    }

    @Transactional
    public Student create(String batchName, String rawName, Integer row, Integer col) {
        Batch batch = lockBatch(batchName);
        String name = validateName(rawName);
        requirePosition(row, col);
        SeatLayout layout = new SeatLayout(findByBatch(batch));
        requireFree(layout, row, col, null);

        Student student = studentRepository.save(new Student(name, batch, row, col, now()));
        events.publishEvent(SeatChangedEvent.of(Type.CREATED, student));
        return student;
    }

    /** 이름·위치 수정 (드래그 앤 드롭 이동 포함). 중도포기 학생의 위치는 (0,0)으로 유지한다. */
    @Transactional
    public Student update(Long id, String rawName, Integer row, Integer col) {
        Student student = getWithBatch(id);
        String name = validateName(rawName);
        batchRepository.findByIdForUpdate(student.getBatch().getId());

        if (!student.isGiveUp() && (row != null || col != null)) {
            requirePosition(row, col);
            if (!student.isAt(row, col)) {
                requireFree(new SeatLayout(findByBatch(student.getBatch())), row, col, student);
                student.moveTo(row, col);
            }
        }
        student.rename(name);
        events.publishEvent(SeatChangedEvent.of(Type.UPDATED, student));
        return student;
    }

    @Transactional
    public List<Student> swap(Long seatId1, Long seatId2) {
        if (seatId1 == null || seatId2 == null) {
            throw SeatException.badRequest("교환할 두 좌석을 지정해주세요.");
        }
        if (seatId1.equals(seatId2)) {
            throw SeatException.badRequest("같은 좌석끼리는 교환할 수 없습니다.");
        }
        Student a = getWithBatch(seatId1);
        Student b = getWithBatch(seatId2);
        if (!a.getBatch().getId().equals(b.getBatch().getId())) {
            throw SeatException.badRequest("같은 기수의 좌석끼리만 교환할 수 있습니다.");
        }
        if (a.isGiveUp() || b.isGiveUp()) {
            throw SeatException.badRequest("중도포기 수강생은 자리를 교환할 수 없습니다.");
        }
        batchRepository.findByIdForUpdate(a.getBatch().getId());

        int row = a.getRowIndex();
        int col = a.getColIndex();
        a.moveTo(b.getRowIndex(), b.getColIndex());
        b.moveTo(row, col);
        events.publishEvent(SeatChangedEvent.of(Type.UPDATED, a));
        events.publishEvent(SeatChangedEvent.of(Type.UPDATED, b));
        return List.of(a, b);
    }

    @Transactional
    public void delete(Long id) {
        Student student = getWithBatch(id);
        SeatChangedEvent event = SeatChangedEvent.of(Type.DELETED, student);
        studentRepository.delete(student);
        events.publishEvent(event);
    }

    public record Skipped(int line, String name, String reason) {
    }

    public record BulkResult(List<Student> created, List<Skipped> skipped) {
    }

    /**
     * 일괄 등록. 형식 오류가 하나라도 있으면 아무것도 저장하지 않는다.
     * 위치가 이미 사용 중이면(입력 내 중복 포함) 그 줄만 건너뛴다.
     */
    @Transactional
    public BulkResult bulkRegister(String batchName, BulkRegistrationParser.Result parsed) {
        if (parsed.hasErrors()) {
            throw SeatException.badRequest("일괄 등록 내용을 확인해주세요.", parsed.errors());
        }
        Batch batch = lockBatch(batchName);
        SeatLayout layout = new SeatLayout(findByBatch(batch));
        LocalDateTime now = now();
        List<Student> created = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();

        for (BulkRegistrationParser.Entry entry : parsed.entries()) {
            int row;
            int col;
            if (entry.hasPosition()) {
                row = entry.row();
                col = entry.col();
                Optional<Student> occupant = layout.occupant(row, col);
                if (occupant.isPresent()) {
                    skipped.add(new Skipped(entry.line(), entry.name(),
                            row + "행 " + col + "열은 이미 " + occupant.get().getName() + "이(가) 사용 중입니다."));
                    continue;
                }
            } else {
                Optional<int[]> free = layout.firstFree();
                if (free.isEmpty()) {
                    skipped.add(new Skipped(entry.line(), entry.name(), "빈 자리가 없습니다."));
                    continue;
                }
                row = free.get()[0];
                col = free.get()[1];
            }
            Student student = studentRepository.save(new Student(entry.name(), batch, row, col, now));
            layout.occupy(row, col, student);
            created.add(student);
        }
        created.forEach(s -> events.publishEvent(SeatChangedEvent.of(Type.CREATED, s)));
        return new BulkResult(created, skipped);
    }

    Student getWithBatch(Long id) {
        if (id == null) {
            throw SeatException.badRequest("좌석을 지정해주세요.");
        }
        return studentRepository.findWithBatchById(id)
                .orElseThrow(() -> SeatException.notFound("수강생을 찾을 수 없습니다."));
    }

    private Batch lockBatch(String batchName) {
        if (batchName == null || batchName.isBlank()) {
            throw SeatException.badRequest("기수를 선택해주세요.");
        }
        Batch batch = batchRepository.findByName(batchName)
                .orElseThrow(() -> SeatException.notFound("'" + batchName + "' 기수를 찾을 수 없습니다."));
        return batchRepository.findByIdForUpdate(batch.getId()).orElseThrow();
    }

    private static String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) {
            throw SeatException.badRequest("이름을 입력해주세요.");
        }
        if (name.length() > Student.NAME_MAX) {
            throw SeatException.badRequest("이름은 " + Student.NAME_MAX + "자 이하로 입력해주세요.");
        }
        return name;
    }

    private static void requirePosition(Integer row, Integer col) {
        if (row == null || col == null || !SeatGrid.inRange(row, col)) {
            throw SeatException.badRequest("행은 1~" + SeatGrid.ROWS + ", 열은 1~" + SeatGrid.COLS + " 사이로 입력해주세요.");
        }
    }

    private static void requireFree(SeatLayout layout, int row, int col, Student self) {
        layout.occupant(row, col)
                .filter(occupant -> occupant != self)
                .ifPresent(occupant -> {
                    throw SeatException.conflict(row + "행 " + col + "열에는 " + occupant.getName() + "이(가) 배치되어 있습니다.");
                });
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
