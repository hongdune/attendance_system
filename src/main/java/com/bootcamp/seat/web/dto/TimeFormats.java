package com.bootcamp.seat.web.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 서버 시각(LocalDateTime, seat.time-zone 기준)을 오프셋이 포함된 값으로 바꿔 내보낸다.
 * 브라우저가 어느 시간대에 있든 타이머 경과 시간이 정확해진다.
 */
public final class TimeFormats {

    private TimeFormats() {
    }

    public static OffsetDateTime withOffset(LocalDateTime time, ZoneId zone) {
        return time == null ? null : time.atZone(zone).toOffsetDateTime();
    }
}
