package com.bootcamp.seat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 테스트용 시계. 시각을 고정하고 원하는 만큼 앞으로 돌릴 수 있다. */
public class MutableClock extends Clock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private Instant instant;

    public MutableClock(LocalDateTime start) {
        this.instant = start.atZone(ZONE).toInstant();
    }

    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
