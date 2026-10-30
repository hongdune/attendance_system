package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;
import com.bootcamp.seat.repository.BatchRepository;
import com.bootcamp.seat.repository.HistoryRepository;
import com.bootcamp.seat.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 서비스 테스트 공통 설정: JPA 슬라이스(내장 H2) + 서비스 빈 + 고정 시계 + 이벤트 기록.
 */
@DataJpaTest
@RecordApplicationEvents
@Import({BatchService.class, StudentService.class, SeatStatusService.class, ServiceTestSupport.ClockConfig.class})
abstract class ServiceTestSupport {

    static final LocalDateTime START = LocalDateTime.of(2026, 9, 2, 9, 0, 0);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        MutableClock clock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    MutableClock clock;
    @Autowired
    BatchRepository batchRepository;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    HistoryRepository historyRepository;
    @Autowired
    BatchService batchService;
    @Autowired
    StudentService studentService;
    @Autowired
    SeatStatusService seatStatusService;
    @Autowired
    ApplicationEvents events;

    Batch batch(String name) {
        return batchService.create(name);
    }

    Student student(Batch batch, String name, int row, int col) {
        return studentService.create(batch.getName(), name, row, col);
    }

    Student student(Batch batch, String name, int row, int col, SeatStatus status) {
        Student s = student(batch, name, row, col);
        if (status != s.getStatus()) {
            seatStatusService.changeStatus(s.getId(), status);
        }
        return s;
    }

    List<SeatChangedEvent> seatEvents() {
        return events.stream(SeatChangedEvent.class).toList();
    }
}
