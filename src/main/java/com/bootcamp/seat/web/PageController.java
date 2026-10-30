package com.bootcamp.seat.web;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.SeatGrid;
import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;
import com.bootcamp.seat.service.BatchService;
import com.bootcamp.seat.service.BulkRegistrationParser;
import com.bootcamp.seat.service.StudentService;
import com.bootcamp.seat.web.dto.SeatDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf 화면. 상태 메타데이터·그리드 크기 등 프론트가 쓰는 상수는 모두 seatConfig 로 내려준다.
 */
@Controller
public class PageController {

    private final BatchService batchService;
    private final StudentService studentService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PageController(BatchService batchService, StudentService studentService,
                          ObjectMapper objectMapper, Clock clock) {
        this.batchService = batchService;
        this.studentService = studentService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @GetMapping("/")
    public String seatMap(@RequestParam(required = false) String batchName, Model model) {
        return render("index", batchName, model);
    }

    @GetMapping("/students")
    public String students(@RequestParam(required = false) String batchName, Model model) {
        return render("students", batchName, model);
    }

    private String render(String view, String requestedBatch, Model model) {
        List<Batch> batches = batchService.findAll();
        Optional<Batch> current = batchService.findByNameOrFirst(requestedBatch);
        if (requestedBatch != null && !requestedBatch.isBlank()
                && current.map(b -> !b.getName().equals(requestedBatch)).orElse(true)) {
            return "redirect:" + ("index".equals(view) ? "/" : "/students");
        }

        List<SeatDto> seats = current
                .map(b -> studentService.findByBatch(b).stream().map(s -> SeatDto.from(s, clock.getZone())).toList())
                .orElse(List.of());

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("gridRows", SeatGrid.ROWS);
        config.put("gridCols", SeatGrid.COLS);
        config.put("meetingRoomCount", Student.MEETING_ROOM_COUNT);
        config.put("studentNameMax", Student.NAME_MAX);
        config.put("batchNameMin", Batch.NAME_MIN);
        config.put("batchNameMax", Batch.NAME_MAX);
        config.put("bulkMax", BulkRegistrationParser.MAX_STUDENTS);
        config.put("timeZone", clock.getZone().getId());
        config.put("serverNow", OffsetDateTime.now(clock));
        config.put("statuses", Arrays.stream(SeatStatus.values()).map(PageController::statusMeta).toList());
        config.put("batches", batches.stream().map(b -> Map.of("id", b.getId(), "name", b.getName())).toList());
        config.put("currentBatch", current.map(b -> Map.of("id", b.getId(), "name", b.getName())).orElse(null));
        config.put("seats", seats);

        model.addAttribute("batches", batches);
        model.addAttribute("currentBatch", current.orElse(null));
        model.addAttribute("studentCount", seats.size());
        model.addAttribute("seatConfigJson", toJson(config));
        return view;
    }

    private static Map<String, Object> statusMeta(SeatStatus s) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", s.name());
        meta.put("label", s.getLabel());
        meta.put("color", s.getColor());
        meta.put("icon", s.getIcon());
        meta.put("timed", s.isTimed());
        meta.put("warningMinutes", s.getWarningMinutes());
        meta.put("dangerMinutes", s.getDangerMinutes());
        return meta;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("화면 설정 직렬화 실패", e);
        }
    }
}
