package com.bootcamp.seat.web;

import com.bootcamp.seat.service.SeatStatusService;
import com.bootcamp.seat.web.dto.Requests;
import com.bootcamp.seat.web.dto.SeatDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/meeting-room")
public class MeetingRoomController {

    private final SeatStatusService seatStatusService;
    private final ZoneId zone;

    public MeetingRoomController(SeatStatusService seatStatusService, Clock clock) {
        this.seatStatusService = seatStatusService;
        this.zone = clock.getZone();
    }

    @PostMapping("/enter")
    public SeatDto enter(@RequestBody Requests.EnterMeetingRoom request) {
        return SeatDto.from(seatStatusService.enterMeetingRoom(request.seatId(), request.meetingRoom()), zone);
    }

    @PostMapping("/exit")
    public SeatDto exit(@RequestParam Long seatId) {
        return SeatDto.from(seatStatusService.exitMeetingRoom(seatId), zone);
    }

    @PostMapping("/exit-all")
    public Map<String, Integer> exitAll(@RequestParam String batchName) {
        return Map.of("changed", seatStatusService.exitAllMeetingRooms(batchName));
    }
}
