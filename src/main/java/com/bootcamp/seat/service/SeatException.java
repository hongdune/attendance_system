package com.bootcamp.seat.service;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 비즈니스 규칙 위반. 메시지는 그대로 사용자에게 보여지므로 한국어로 작성한다.
 */
public class SeatException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> details;

    public SeatException(HttpStatus status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = List.copyOf(details);
    }

    public static SeatException badRequest(String message) {
        return new SeatException(HttpStatus.BAD_REQUEST, message, List.of());
    }

    public static SeatException badRequest(String message, List<String> details) {
        return new SeatException(HttpStatus.BAD_REQUEST, message, details);
    }

    public static SeatException notFound(String message) {
        return new SeatException(HttpStatus.NOT_FOUND, message, List.of());
    }

    public static SeatException conflict(String message) {
        return new SeatException(HttpStatus.CONFLICT, message, List.of());
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}
