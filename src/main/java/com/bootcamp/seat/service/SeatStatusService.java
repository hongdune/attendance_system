package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.History;
import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;
import com.bootcamp.seat.repository.BatchRepository;
import com.bootcamp.seat.repository.HistoryRepository;
import com.bootcamp.seat.repository.StudentRepository;
import com.bootcamp.seat.service.SeatChangedEvent.Type;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 상태·회의실 변경. 상태가 바뀌는 모든 경로(개별·점심시간·전체 퇴실)는 여기를 거쳐
 * History 1건을 남기고 브로드캐스트 이벤트를 발행한다.
 */
@Service
@Transactional
public class SeatStatusService {

    /** 점심시간 일괄 적용에서 제외: 자리에 없는 사람과 이미 점심인 사람 */
    static final Set<SeatStatus> LUNCH_EXCLUDED =
            EnumSet.of(SeatStatus.GIVEUP, SeatStatus.ABSENT, SeatStatus.OUT, SeatStatus.LUNCH);

    /** 전체 퇴실에서 제외: 결석 기록 보존, 이미 퇴실한 사람 */
    static final Set<SeatStatus> BULK_EXIT_EXCLUDED =
            EnumSet.of(SeatStatus.GIVEUP, SeatStatus.ABSENT, SeatStatus.OUT);

    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final HistoryRepository historyRepository;
    private final BatchService batchService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SeatStatusService(StudentRepository studentRepository, BatchRepository batchRepository,
                             HistoryRepository historyRepository, BatchService batchService,
                             ApplicationEventPublisher events, Clock clock) {
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.historyRepository = historyRepository;
        this.batchService = batchService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 개별 상태 변경. 같은 상태로의 변경은 아무것도 하지 않는다(타이머 유지, 히스토리 미기록).
     * 중도포기에서 복귀하면 1행 1열부터 첫 빈 자리에 배치한다.
     */
    public Student changeStatus(Long seatId, SeatStatus status) {
        if (status == null) {
            throw SeatException.badRequest("변경할 상태를 지정해주세요.");
        }
        Student student = getWithBatch(seatId);
        if (student.getStatus() == status) {
            return student;
        }
        if (student.isGiveUp()) {
            placeReturningStudent(student);
        }
        apply(student, status, now());
        return student;
    }

    /** 점심시간: 기수 전체를 LUNCH 로. 변경된 인원 수 반환. */
    public int lunchTime(String batchName) {
        return applyToBatch(batchName, SeatStatus.LUNCH, LUNCH_EXCLUDED, false);
    }

    /** 전체 퇴실: 기수 전체를 OUT 으로, 회의실에서도 모두 내보낸다. 변경된 인원 수 반환. */
    public int bulkExit(String batchName) {
        return applyToBatch(batchName, SeatStatus.OUT, BULK_EXIT_EXCLUDED, true);
    }

    /** 회의실 입장. 좌석 상태는 그대로 두고 meetingRoom 만 바꾼다(히스토리 미기록). */
    public Student enterMeetingRoom(Long seatId, Integer room) {
        if (room == null || room < 1 || room > Student.MEETING_ROOM_COUNT) {
            throw SeatException.badRequest("회의실은 1~" + Student.MEETING_ROOM_COUNT + "번 중에서 선택해주세요.");
        }
        Student student = getWithBatch(seatId);
        if (student.isGiveUp()) {
            throw SeatException.badRequest("중도포기 수강생은 회의실에 입장할 수 없습니다.");
        }
        student.enterMeetingRoom(room);
        events.publishEvent(SeatChangedEvent.of(Type.UPDATED, student));
        return student;
    }

    public Student exitMeetingRoom(Long seatId) {
        Student student = getWithBatch(seatId);
        if (student.getMeetingRoom() != null) {
            student.exitMeetingRoom();
            events.publishEvent(SeatChangedEvent.of(Type.UPDATED, student));
        }
        return student;
    }

    public int exitAllMeetingRooms(String batchName) {
        Batch batch = batchService.getByName(batchName);
        int count = 0;
        for (Student student : studentRepository.findByBatchOrderByNameAsc(batch)) {
            if (student.getMeetingRoom() != null) {
                student.exitMeetingRoom();
                events.publishEvent(SeatChangedEvent.of(Type.UPDATED, student));
                count++;
            }
        }
        return count;
    }

    private int applyToBatch(String batchName, SeatStatus target, Set<SeatStatus> excluded, boolean leaveMeetingRoom) {
        Batch batch = batchService.getByName(batchName);
        LocalDateTime now = now();
        int changed = 0;
        for (Student student : studentRepository.findByBatchOrderByNameAsc(batch)) {
            boolean statusChanges = !excluded.contains(student.getStatus());
            boolean roomChanges = leaveMeetingRoom && student.getMeetingRoom() != null;
            if (roomChanges) {
                student.exitMeetingRoom();
            }
            if (statusChanges) {
                apply(student, target, now);
                changed++;
            } else if (roomChanges) {
                events.publishEvent(SeatChangedEvent.of(Type.UPDATED, student));
            }
        }
        return changed;
    }

    private void apply(Student student, SeatStatus status, LocalDateTime now) {
        student.changeStatus(status, now);
        historyRepository.save(History.of(student));
        events.publishEvent(SeatChangedEvent.of(Type.UPDATED, student));
    }

    private void placeReturningStudent(Student student) {
        Batch batch = batchRepository.findByIdForUpdate(student.getBatch().getId()).orElseThrow();
        int[] seat = new SeatLayout(studentRepository.findByBatchOrderByNameAsc(batch)).firstFree()
                .orElseThrow(() -> SeatException.conflict("배치도에 빈 자리가 없어 복귀시킬 수 없습니다."));
        student.moveTo(seat[0], seat[1]);
    }

    private Student getWithBatch(Long seatId) {
        if (seatId == null) {
            throw SeatException.badRequest("좌석을 지정해주세요.");
        }
        return studentRepository.findWithBatchById(seatId)
                .orElseThrow(() -> SeatException.notFound("수강생을 찾을 수 없습니다."));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
