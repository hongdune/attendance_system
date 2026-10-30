package com.bootcamp.seat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 출입 기록. 학생/기수가 삭제돼도 남도록 이름을 스냅샷으로 저장한다.
 */
@Entity
@Table(name = "history", indexes = {
        @Index(name = "idx_history_changed_at", columnList = "changedAt"),
        @Index(name = "idx_history_batch_name", columnList = "batchName")
})
public class History {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = Student.NAME_MAX)
    private String studentName;

    @Column(nullable = false, length = Batch.NAME_MAX)
    private String batchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus toStatus;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    protected History() {
    }

    public History(String studentName, String batchName, SeatStatus toStatus, LocalDateTime changedAt) {
        this.studentName = studentName;
        this.batchName = batchName;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    /** 학생의 현재 상태를 스냅샷으로 기록 */
    public static History of(Student student) {
        return new History(student.getName(), student.getBatch().getName(),
                student.getStatus(), student.getStatusChangedAt());
    }

    public Long getId() {
        return id;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getBatchName() {
        return batchName;
    }

    public SeatStatus getToStatus() {
        return toStatus;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
