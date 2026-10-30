package com.bootcamp.seat.web;

import com.bootcamp.seat.domain.History;
import com.bootcamp.seat.service.HistoryService;
import com.bootcamp.seat.web.dto.HistoryDto;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 출입 히스토리. startDate/endDate 는 datetime-local 형식(yyyy-MM-ddTHH:mm[:ss]), 서버 시간대 기준.
 * page 는 0부터 시작한다.
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final HistoryService historyService;
    private final Clock clock;
    private final ZoneId zone;

    public HistoryController(HistoryService historyService, Clock clock) {
        this.historyService = historyService;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    @GetMapping
    public Map<String, Object> search(
            @RequestParam(required = false) String batchName,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + HistoryService.DEFAULT_PAGE_SIZE) int size) {
        Page<History> result = historyService.search(
                new HistoryService.Filter(batchName, studentName, startDate, endDate), page, size);
        return Map.of(
                "histories", result.getContent().stream().map(h -> HistoryDto.from(h, zone)).toList(),
                "totalPages", result.getTotalPages(),
                "currentPage", result.getNumber(),
                "totalElements", result.getTotalElements(),
                "hasPrevious", result.hasPrevious(),
                "hasNext", result.hasNext());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String batchName,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        byte[] file = historyService.exportExcel(
                new HistoryService.Filter(batchName, studentName, startDate, endDate));
        String filename = "출입히스토리_" + LocalDateTime.now(clock).format(FILE_TIME) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(file);
    }
}
