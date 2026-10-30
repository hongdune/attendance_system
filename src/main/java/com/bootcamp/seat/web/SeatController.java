package com.bootcamp.seat.web;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.service.BatchService;
import com.bootcamp.seat.service.SeatStatusService;
import com.bootcamp.seat.service.StudentService;
import com.bootcamp.seat.web.dto.Requests;
import com.bootcamp.seat.web.dto.SeatDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
public class SeatController {

    private final BatchService batchService;
    private final StudentService studentService;
    private final SeatStatusService seatStatusService;
    private final ZoneId zone;

    public SeatController(BatchService batchService, StudentService studentService,
                          SeatStatusService seatStatusService, Clock clock) {
        this.batchService = batchService;
        this.studentService = studentService;
        this.seatStatusService = seatStatusService;
        this.zone = clock.getZone();
    }

    /** 기수 좌석 전체 (중도포기 포함, 이름순) */
    @GetMapping("/api/seats/batch/{batchName}")
    public List<SeatDto> seats(@PathVariable String batchName) {
        Batch batch = batchService.getByName(batchName);
        return studentService.findByBatch(batch).stream().map(s -> SeatDto.from(s, zone)).toList();
    }

    @PostMapping("/seats/status")
    public SeatDto changeStatus(@RequestBody Requests.StatusChange request) {
        return SeatDto.from(seatStatusService.changeStatus(request.seatId(), request.status()), zone);
    }

    @PostMapping("/seats/bulk-exit")
    public Map<String, Integer> bulkExit(@RequestBody Requests.BatchTarget request) {
        return Map.of("changed", seatStatusService.bulkExit(request.batchName()));
    }

    @PostMapping("/seats/lunch-time")
    public Map<String, Integer> lunchTime(@RequestBody Requests.BatchTarget request) {
        return Map.of("changed", seatStatusService.lunchTime(request.batchName()));
    }
}
