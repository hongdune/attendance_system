package com.bootcamp.seat.web;

import com.bootcamp.seat.service.SeatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * REST 컨트롤러 오류를 {"message": "...", "details": [...]} 형태로 통일한다.
 */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SeatException.class)
    public ResponseEntity<Map<String, Object>> handleSeat(SeatException e) {
        return body(e.getStatus(), e.getMessage(), e.getDetails());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 위반", e);
        return body(HttpStatus.CONFLICT, "다른 사용자가 동시에 변경했습니다. 새로고침 후 다시 시도해주세요.", List.of());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(details.isEmpty()
                ? Map.of("message", message)
                : Map.of("message", message, "details", details));
    }
}
