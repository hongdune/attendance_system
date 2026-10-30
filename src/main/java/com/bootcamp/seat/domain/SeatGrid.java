package com.bootcamp.seat.domain;

/**
 * 좌석 배치도 그리드 크기. 프론트엔드는 템플릿을 통해 같은 값을 받는다.
 */
public final class SeatGrid {

    public static final int ROWS = 11;
    public static final int COLS = 12;

    /** 중도포기 등 배치도에 없는 학생의 위치 값 */
    public static final int NO_POSITION = 0;

    private SeatGrid() {
    }

    public static boolean inRange(int row, int col) {
        return row >= 1 && row <= ROWS && col >= 1 && col <= COLS;
    }
}
