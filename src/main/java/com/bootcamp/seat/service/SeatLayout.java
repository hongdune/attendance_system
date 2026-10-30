package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.SeatGrid;
import com.bootcamp.seat.domain.Student;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 한 기수의 좌석 점유 현황. 중도포기(위치 0,0) 학생은 자리를 차지하지 않는다.
 */
class SeatLayout {

    private final Map<Integer, Student> occupants = new HashMap<>();

    SeatLayout(Collection<Student> students) {
        for (Student s : students) {
            if (!s.isGiveUp() && SeatGrid.inRange(s.getRowIndex(), s.getColIndex())) {
                occupants.put(key(s.getRowIndex(), s.getColIndex()), s);
            }
        }
    }

    Optional<Student> occupant(int row, int col) {
        return Optional.ofNullable(occupants.get(key(row, col)));
    }

    boolean isFree(int row, int col) {
        return !occupants.containsKey(key(row, col));
    }

    void occupy(int row, int col, Student student) {
        occupants.put(key(row, col), student);
    }

    /** 1행 1열부터 행 우선으로 첫 빈 칸. [row, col] */
    Optional<int[]> firstFree() {
        for (int r = 1; r <= SeatGrid.ROWS; r++) {
            for (int c = 1; c <= SeatGrid.COLS; c++) {
                if (isFree(r, c)) {
                    return Optional.of(new int[]{r, c});
                }
            }
        }
        return Optional.empty();
    }

    private static int key(int row, int col) {
        return row * 1000 + col;
    }
}
