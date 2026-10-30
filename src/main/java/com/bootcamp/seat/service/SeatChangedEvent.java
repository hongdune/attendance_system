package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;

import java.time.LocalDateTime;

/**
 * 좌석 변경 이벤트. 트랜잭션 커밋 후 WebSocket 으로 브로드캐스트된다.
 * 엔티티 대신 값 스냅샷을 담아 커밋 이후에도 안전하게 읽을 수 있게 한다.
 */
public record SeatChangedEvent(
        Type type,
        String batchName,
        Long seatId,
        String seatName,
        int rowIndex,
        int colIndex,
        SeatStatus status,
        LocalDateTime statusChangedAt,
        Integer meetingRoom
) {

    public enum Type {
        CREATED, UPDATED, DELETED
    }

    public static SeatChangedEvent of(Type type, Student s) {
        return new SeatChangedEvent(type, s.getBatch().getName(), s.getId(), s.getName(),
                s.getRowIndex(), s.getColIndex(), s.getStatus(), s.getStatusChangedAt(), s.getMeetingRoom());
    }
}
