package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.SeatGrid;
import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class StudentServiceTest extends ServiceTestSupport {

    Batch batch;

    @BeforeEach
    void setUp() {
        batch = batch("BESP5");
    }

    // ===== 좌석 중복 =====

    @Test
    void 같은_기수의_사용중인_자리에는_추가할_수_없다() {
        student(batch, "홍길동", 1, 2);

        assertThatThrownBy(() -> student(batch, "김철수", 1, 2))
                .isInstanceOfSatisfying(SeatException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("홍길동이(가) 배치되어 있습니다");
                });
    }

    @Test
    void 다른_기수라면_같은_자리를_쓸_수_있다() {
        student(batch, "홍길동", 1, 2);
        Batch other = batch("AINLP5");

        Student s = student(other, "김철수", 1, 2);

        assertThat(s.getId()).isNotNull();
    }

    @Test
    void 그리드_범위를_벗어난_위치는_거부한다() {
        assertThatThrownBy(() -> student(batch, "홍길동", SeatGrid.ROWS + 1, 1)).isInstanceOf(SeatException.class);
        assertThatThrownBy(() -> student(batch, "홍길동", 1, SeatGrid.COLS + 1)).isInstanceOf(SeatException.class);
        assertThatThrownBy(() -> student(batch, "홍길동", 0, 1)).isInstanceOf(SeatException.class);
    }

    @Test
    void 자리_이동은_빈자리만_가능하고_제자리는_허용한다() {
        Student a = student(batch, "가", 1, 2);
        student(batch, "나", 1, 3);

        assertThatThrownBy(() -> studentService.update(a.getId(), "가", 1, 3))
                .isInstanceOfSatisfying(SeatException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        studentService.update(a.getId(), "가나다", 1, 2);
        assertThat(a.getName()).isEqualTo("가나다");

        studentService.update(a.getId(), "가나다", 5, 6);
        assertThat(a.getRowIndex()).isEqualTo(5);
        assertThat(a.getColIndex()).isEqualTo(6);
    }

    @Test
    void 중도포기_학생은_자리를_차지하지_않고_위치를_수정해도_0_0으로_유지된다() {
        Student a = student(batch, "가", 2, 2, SeatStatus.GIVEUP);
        student(batch, "나", 3, 3, SeatStatus.GIVEUP);

        Student c = student(batch, "다", 2, 2);
        studentService.update(a.getId(), "가", 7, 7);

        assertThat(c.getRowIndex()).isEqualTo(2);
        assertThat(a.getRowIndex()).isZero();
        assertThat(a.getColIndex()).isZero();
    }

    // ===== 스왑 =====

    @Test
    void 두_좌석의_위치를_교환하고_두_건의_이벤트를_발행한다() {
        Student a = student(batch, "가", 1, 2);
        Student b = student(batch, "나", 4, 5);
        events.clear();

        studentService.swap(a.getId(), b.getId());

        assertThat(a.getRowIndex()).isEqualTo(4);
        assertThat(a.getColIndex()).isEqualTo(5);
        assertThat(b.getRowIndex()).isEqualTo(1);
        assertThat(b.getColIndex()).isEqualTo(2);
        assertThat(seatEvents()).extracting(SeatChangedEvent::seatId, SeatChangedEvent::rowIndex)
                .containsExactly(tuple(a.getId(), 4), tuple(b.getId(), 1));
    }

    @Test
    void 다른_기수_중도포기_같은_좌석끼리는_교환할_수_없다() {
        Student a = student(batch, "가", 1, 2);
        Student giveUp = student(batch, "나", 1, 3, SeatStatus.GIVEUP);
        Student other = student(batch("AINLP5"), "다", 1, 2);

        assertThatThrownBy(() -> studentService.swap(a.getId(), other.getId())).isInstanceOf(SeatException.class);
        assertThatThrownBy(() -> studentService.swap(a.getId(), giveUp.getId())).isInstanceOf(SeatException.class);
        assertThatThrownBy(() -> studentService.swap(a.getId(), a.getId())).isInstanceOf(SeatException.class);
        assertThat(a.getRowIndex()).isEqualTo(1);
        assertThat(a.getColIndex()).isEqualTo(2);
    }

    // ===== 일괄 등록 =====

    @Test
    void 자동_배치는_1행_1열부터_빈자리를_채운다() {
        student(batch, "기존", 1, 2);

        StudentService.BulkResult result = studentService.bulkRegister("BESP5",
                BulkRegistrationParser.parse("가, 나\n다"));

        assertThat(result.created()).extracting(Student::getName, Student::getRowIndex, Student::getColIndex)
                .containsExactly(tuple("가", 1, 1), tuple("나", 1, 3), tuple("다", 1, 4));
        assertThat(result.skipped()).isEmpty();
        assertThat(seatEvents()).filteredOn(e -> e.type() == SeatChangedEvent.Type.CREATED).hasSize(4);
    }

    @Test
    void 직접_지정은_사용중인_위치와_입력내_중복을_건너뛴다() {
        student(batch, "기존", 1, 1);

        StudentService.BulkResult result = studentService.bulkRegister("BESP5",
                BulkRegistrationParser.parse("가, 1, 1\n나, 2, 2\n다, 2, 2"));

        assertThat(result.created()).extracting(Student::getName).containsExactly("나");
        assertThat(result.skipped()).extracting(StudentService.Skipped::line, StudentService.Skipped::name)
                .containsExactly(tuple(1, "가"), tuple(3, "다"));
        assertThat(result.skipped().get(0).reason()).contains("기존");
    }

    @Test
    void 형식_오류가_있으면_아무것도_저장하지_않는다() {
        BulkRegistrationParser.Result parsed = BulkRegistrationParser.parse("가, 1, 1\n나, 99, 1");

        assertThatThrownBy(() -> studentService.bulkRegister("BESP5", parsed))
                .isInstanceOfSatisfying(SeatException.class,
                        e -> assertThat(e.getDetails()).singleElement().asString().startsWith("2번째 줄"));
        assertThat(studentRepository.countByBatch(batch)).isZero();
    }
}
