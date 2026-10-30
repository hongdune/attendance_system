package com.bootcamp.seat.web.dto;

import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.service.SeatChangedEvent;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * /topic/batch/{batchName} 으로 보내는 메시지. type: CREATED | UPDATED | DELETED
 */
public record SeatMessage(
        String type,
        Long seatId,
        String seatName,
        int rowIndex,
        int colIndex,
        SeatStatus status,
        OffsetDateTime statusChangedAt,
        Integer meetingRoom
) {

    public static SeatMessage from(SeatChangedEvent e, ZoneId zone) {
        return new SeatMessage(e.type().name(), e.seatId(), e.seatName(), e.rowIndex(), e.colIndex(),
                e.status(), TimeFormats.withOffset(e.statusChangedAt(), zone), e.meetingRoom());
    }
}
