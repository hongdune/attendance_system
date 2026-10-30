package com.bootcamp.seat.web;

import com.bootcamp.seat.service.BulkRegistrationParser;
import com.bootcamp.seat.service.SeatException;
import com.bootcamp.seat.service.StudentService;
import com.bootcamp.seat.web.dto.Requests;
import com.bootcamp.seat.web.dto.SeatDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class StudentController {

    private final StudentService studentService;
    private final ZoneId zone;

    public StudentController(StudentService studentService, Clock clock) {
        this.studentService = studentService;
        this.zone = clock.getZone();
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatDto create(@RequestBody Requests.CreateStudent request) {
        return SeatDto.from(studentService.create(request.batchName(), request.name(),
                request.rowIndex(), request.colIndex()), zone);
    }

    @PutMapping("/students/{id}")
    public SeatDto update(@PathVariable Long id, @RequestBody Requests.UpdateStudent request) {
        return SeatDto.from(studentService.update(id, request.name(), request.rowIndex(), request.colIndex()), zone);
    }

    @DeleteMapping("/students/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        studentService.delete(id);
    }

    @PostMapping("/students/swap")
    public List<SeatDto> swap(@RequestBody Requests.Swap request) {
        return studentService.swap(request.seatId1(), request.seatId2()).stream()
                .map(s -> SeatDto.from(s, zone))
                .toList();
    }

    @PostMapping("/students/bulk")
    public Map<String, Object> bulk(@RequestBody Requests.BulkRegister request) {
        StudentService.BulkResult result = studentService.bulkRegister(request.batchName(), parse(request));
        return Map.of(
                "createdCount", result.created().size(),
                "created", result.created().stream().map(s -> SeatDto.from(s, zone)).toList(),
                "skipped", result.skipped());
    }

    private static BulkRegistrationParser.Result parse(Requests.BulkRegister request) {
        if (request.text() != null && !request.text().isBlank()) {
            return BulkRegistrationParser.parse(request.text());
        }
        if (request.students() == null || request.students().isEmpty()) {
            throw SeatException.badRequest("등록할 수강생을 입력해주세요.");
        }
        List<BulkRegistrationParser.Entry> entries = new ArrayList<>();
        for (int i = 0; i < request.students().size(); i++) {
            Requests.BulkStudent s = request.students().get(i);
            entries.add(new BulkRegistrationParser.Entry(i + 1, s.name(), s.rowIndex(), s.colIndex()));
        }
        return BulkRegistrationParser.fromEntries(entries);
    }
}
