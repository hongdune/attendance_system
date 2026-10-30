package com.bootcamp.seat.web.dto;

import com.bootcamp.seat.domain.SeatStatus;

import java.util.List;

/**
 * API 요청 바디. 필드 검증과 한국어 오류 메시지는 서비스 계층이 담당한다.
 */
public final class Requests {

    private Requests() {
    }

    public record StatusChange(Long seatId, SeatStatus status) {
    }

    public record BatchTarget(String batchName) {
    }

    public record CreateStudent(String name, String batchName, Integer rowIndex, Integer colIndex) {
    }

    public record UpdateStudent(String name, Integer rowIndex, Integer colIndex) {
    }

    public record Swap(Long seatId1, Long seatId2) {
    }

    /**
     * 일괄 등록. {@code text}(textarea 원문, 서버에서 파싱)가 있으면 우선 사용하고,
     * 없으면 {@code students} 목록을 사용한다. students 의 rowIndex/colIndex 가 비어 있으면 자동 배치.
     */
    public record BulkRegister(String batchName, String text, List<BulkStudent> students) {
    }

    public record BulkStudent(String name, Integer rowIndex, Integer colIndex) {
    }

    public record CreateBatch(String name) {
    }

    public record EnterMeetingRoom(Long seatId, Integer meetingRoom) {
    }
}
