package com.bootcamp.seat.web.dto;

import com.bootcamp.seat.domain.History;
import com.bootcamp.seat.domain.SeatStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record HistoryDto(
        Long id,
        String studentName,
        String batchName,
        SeatStatus toStatus,
        OffsetDateTime changedAt
) {

    public static HistoryDto from(History h, ZoneId zone) {
        return new HistoryDto(h.getId(), h.getStudentName(), h.getBatchName(), h.getToStatus(),
                TimeFormats.withOffset(h.getChangedAt(), zone));
    }
}
