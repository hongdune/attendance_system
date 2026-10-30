package com.bootcamp.seat.web.dto;

import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record SeatDto(
        Long id,
        String name,
        int rowIndex,
        int colIndex,
        SeatStatus status,
        OffsetDateTime statusChangedAt,
        Integer meetingRoom
) {

    public static SeatDto from(Student s, ZoneId zone) {
        return new SeatDto(s.getId(), s.getName(), s.getRowIndex(), s.getColIndex(), s.getStatus(),
                TimeFormats.withOffset(s.getStatusChangedAt(), zone), s.getMeetingRoom());
    }
}
