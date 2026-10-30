package com.bootcamp.seat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 수강생 = 좌석 1개. id 가 곧 seatId.
 * (rowIndex, colIndex) 중복 검사는 서비스에서 한다. 중도포기 학생은 모두 (0,0)이라 DB 유니크 제약을 걸 수 없다.
 */
@Entity
@Table(name = "student", indexes = @Index(name = "idx_student_batch", columnList = "batch_id"))
public class Student {

    public static final int NAME_MAX = 30;
    public static final int MEETING_ROOM_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false)
    private int rowIndex;

    @Column(nullable = false)
    private int colIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(nullable = false)
    private LocalDateTime statusChangedAt;

    private Integer meetingRoom;

    protected Student() {
    }

    public Student(String name, Batch batch, int rowIndex, int colIndex, LocalDateTime now) {
        this.name = name;
        this.batch = batch;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.status = SeatStatus.OUT;
        this.statusChangedAt = now;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(int rowIndex, int colIndex) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
    }

    /** 상태 변경. 중도포기는 위치를 비우고 회의실에서도 내보낸다. */
    public void changeStatus(SeatStatus status, LocalDateTime now) {
        this.status = status;
        this.statusChangedAt = now;
        if (status == SeatStatus.GIVEUP) {
            moveTo(SeatGrid.NO_POSITION, SeatGrid.NO_POSITION);
            this.meetingRoom = null;
        }
    }

    public void enterMeetingRoom(int room) {
        this.meetingRoom = room;
    }

    public void exitMeetingRoom() {
        this.meetingRoom = null;
    }

    public boolean isGiveUp() {
        return status == SeatStatus.GIVEUP;
    }

    public boolean isAt(int row, int col) {
        return rowIndex == row && colIndex == col;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Batch getBatch() {
        return batch;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public LocalDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public Integer getMeetingRoom() {
        return meetingRoom;
    }
}
