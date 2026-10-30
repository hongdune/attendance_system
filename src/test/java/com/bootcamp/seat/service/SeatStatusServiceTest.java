package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.History;
import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatStatusServiceTest extends ServiceTestSupport {

    Batch batch;

    @BeforeEach
    void setUp() {
        batch = batch("AINLP5");
    }

    @Test
    void 상태를_바꾸면_변경시각이_갱신되고_히스토리와_이벤트가_남는다() {
        Student s = student(batch, "홍길동", 1, 2);
        events.clear();
        clock.advance(Duration.ofMinutes(5));

        Student changed = seatStatusService.changeStatus(s.getId(), SeatStatus.WC);

        assertThat(changed.getStatus()).isEqualTo(SeatStatus.WC);
        assertThat(changed.getStatusChangedAt()).isEqualTo(clock.now());
        List<History> histories = historyRepository.findAll();
        assertThat(histories).hasSize(1);
        History h = histories.get(0);
        assertThat(h.getStudentName()).isEqualTo("홍길동");
        assertThat(h.getBatchName()).isEqualTo("AINLP5");
        assertThat(h.getToStatus()).isEqualTo(SeatStatus.WC);
        assertThat(h.getChangedAt()).isEqualTo(clock.now());
        assertThat(seatEvents()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(SeatChangedEvent.Type.UPDATED);
            assertThat(e.batchName()).isEqualTo("AINLP5");
            assertThat(e.status()).isEqualTo(SeatStatus.WC);
        });
    }

    @Test
    void 같은_상태로의_변경은_타이머를_유지하고_기록하지_않는다() {
        Student s = student(batch, "홍길동", 1, 2, SeatStatus.WC);
        var startedAt = s.getStatusChangedAt();
        long before = historyRepository.count();
        events.clear();
        clock.advance(Duration.ofMinutes(3));

        seatStatusService.changeStatus(s.getId(), SeatStatus.WC);

        assertThat(s.getStatusChangedAt()).isEqualTo(startedAt);
        assertThat(historyRepository.count()).isEqualTo(before);
        assertThat(seatEvents()).isEmpty();
    }

    @Test
    void 중도포기는_위치를_비우고_회의실에서_내보낸다() {
        Student s = student(batch, "홍길동", 3, 5, SeatStatus.IN);
        seatStatusService.enterMeetingRoom(s.getId(), 2);

        seatStatusService.changeStatus(s.getId(), SeatStatus.GIVEUP);

        assertThat(s.getRowIndex()).isZero();
        assertThat(s.getColIndex()).isZero();
        assertThat(s.getMeetingRoom()).isNull();
        assertThat(historyRepository.findAll()).extracting(History::getToStatus)
                .containsExactly(SeatStatus.IN, SeatStatus.GIVEUP);
    }

    @Test
    void 중도포기에서_복귀하면_첫_빈자리에_배치된다() {
        student(batch, "가", 1, 1);
        Student s = student(batch, "나", 1, 2, SeatStatus.GIVEUP);
        // 나가 비운 1행 2열이 첫 빈자리

        seatStatusService.changeStatus(s.getId(), SeatStatus.IN);

        assertThat(s.getRowIndex()).isEqualTo(1);
        assertThat(s.getColIndex()).isEqualTo(2);
        assertThat(s.getStatus()).isEqualTo(SeatStatus.IN);
    }

    @Test
    void 점심시간은_자리에_있는_수강생만_LUNCH로_바꾸고_각각_기록한다() {
        Student in = student(batch, "입실", 1, 1, SeatStatus.IN);
        Student wc = student(batch, "화장실", 1, 2, SeatStatus.WC);
        Student late = student(batch, "지각", 1, 3, SeatStatus.LATE);
        Student absent = student(batch, "결석", 1, 4, SeatStatus.ABSENT);
        Student out = student(batch, "퇴실", 1, 5);
        Student giveUp = student(batch, "포기", 1, 6, SeatStatus.GIVEUP);
        historyRepository.deleteAll();
        events.clear();

        int changed = seatStatusService.lunchTime("AINLP5");

        assertThat(changed).isEqualTo(3);
        assertThat(List.of(in, wc, late)).allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(SeatStatus.LUNCH));
        assertThat(absent.getStatus()).isEqualTo(SeatStatus.ABSENT);
        assertThat(out.getStatus()).isEqualTo(SeatStatus.OUT);
        assertThat(giveUp.getStatus()).isEqualTo(SeatStatus.GIVEUP);
        assertThat(historyRepository.findAll()).hasSize(3)
                .allSatisfy(h -> assertThat(h.getToStatus()).isEqualTo(SeatStatus.LUNCH));
        assertThat(seatEvents()).hasSize(3);
    }

    @Test
    void 전체퇴실은_결석과_중도포기를_제외하고_OUT으로_바꾸며_회의실을_비운다() {
        Student in = student(batch, "입실", 1, 1, SeatStatus.IN);
        Student lunch = student(batch, "점심", 1, 2, SeatStatus.LUNCH);
        Student absent = student(batch, "결석", 1, 3, SeatStatus.ABSENT);
        seatStatusService.enterMeetingRoom(in.getId(), 1);
        seatStatusService.enterMeetingRoom(absent.getId(), 2);
        historyRepository.deleteAll();
        events.clear();

        int changed = seatStatusService.bulkExit("AINLP5");

        assertThat(changed).isEqualTo(2);
        assertThat(in.getStatus()).isEqualTo(SeatStatus.OUT);
        assertThat(lunch.getStatus()).isEqualTo(SeatStatus.OUT);
        assertThat(absent.getStatus()).isEqualTo(SeatStatus.ABSENT);
        assertThat(in.getMeetingRoom()).isNull();
        assertThat(absent.getMeetingRoom()).isNull();
        assertThat(historyRepository.findAll()).hasSize(2);
        // 상태가 바뀐 2명 + 회의실만 비운 결석 1명
        assertThat(seatEvents()).hasSize(3);
    }

    @Test
    void 회의실_입장은_상태와_히스토리를_바꾸지_않는다() {
        Student s = student(batch, "홍길동", 1, 1, SeatStatus.IN);
        long before = historyRepository.count();

        seatStatusService.enterMeetingRoom(s.getId(), 3);

        assertThat(s.getMeetingRoom()).isEqualTo(3);
        assertThat(s.getStatus()).isEqualTo(SeatStatus.IN);
        assertThat(historyRepository.count()).isEqualTo(before);
        assertThatThrownBy(() -> seatStatusService.enterMeetingRoom(s.getId(), 4))
                .isInstanceOf(SeatException.class);
    }

    @Test
    void 수강생이_있는_기수는_삭제할_수_없다() {
        student(batch, "홍길동", 1, 1);

        assertThatThrownBy(() -> batchService.delete(batch.getId()))
                .isInstanceOfSatisfying(SeatException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        Batch empty = batch("빈기수");
        batchService.delete(empty.getId());
        assertThat(batchRepository.findByName("빈기수")).isEmpty();
    }
}
