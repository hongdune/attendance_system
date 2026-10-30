package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.SeatGrid;
import com.bootcamp.seat.domain.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일괄 등록 텍스트 파서.
 * <ul>
 *   <li>방법 1 (자동 배치): 쉼표 또는 줄바꿈으로 구분한 이름 목록</li>
 *   <li>방법 2 (직접 지정): 줄마다 {@code 이름, 행, 열}</li>
 * </ul>
 * 첫 번째 내용 줄이 {@code 이름, 숫자, 숫자} 패턴이면 방법 2로 판단한다.
 * 줄 번호는 사용자가 입력한 원문 기준(빈 줄 포함, 1부터)이다.
 */
public final class BulkRegistrationParser {

    public static final int MAX_STUDENTS = 50;

    private static final Pattern DIRECT_LINE = Pattern.compile("^\\s*([^,]+?)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*$");

    public enum Mode {
        AUTO, DIRECT
    }

    /** 파싱된 한 명. 자동 배치면 row/col 이 null. */
    public record Entry(int line, String name, Integer row, Integer col) {

        public boolean hasPosition() {
            return row != null && col != null;
        }
    }

    public record Result(Mode mode, List<Entry> entries, List<String> errors) {

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private BulkRegistrationParser() {
    }

    public static Result parse(String text) {
        String[] lines = text == null ? new String[0] : text.split("\\R", -1);
        Mode mode = detectMode(lines);
        List<Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            if (mode == Mode.DIRECT) {
                parseDirectLine(lineNo, line, entries, errors);
            } else {
                parseNameLine(lineNo, line, entries, errors);
            }
        }
        checkCount(entries.size(), errors);
        return new Result(mode, List.copyOf(entries), List.copyOf(errors));
    }

    /** JSON 으로 직접 받은 목록을 같은 규칙으로 검증한다. 순번을 줄 번호로 사용. */
    public static Result fromEntries(List<Entry> raw) {
        List<Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        boolean anyPosition = false;
        for (Entry e : raw) {
            String name = e.name() == null ? "" : e.name().strip();
            if (!validName(e.line(), name, errors)) {
                continue;
            }
            if (e.row() != null || e.col() != null) {
                anyPosition = true;
                if (e.row() == null || e.col() == null || !SeatGrid.inRange(e.row(), e.col())) {
                    errors.add(rangeError(e.line()));
                    continue;
                }
            }
            entries.add(new Entry(e.line(), name, e.row(), e.col()));
        }
        checkCount(entries.size(), errors);
        return new Result(anyPosition ? Mode.DIRECT : Mode.AUTO, List.copyOf(entries), List.copyOf(errors));
    }

    private static Mode detectMode(String[] lines) {
        for (String line : lines) {
            if (!line.isBlank()) {
                return DIRECT_LINE.matcher(line).matches() ? Mode.DIRECT : Mode.AUTO;
            }
        }
        return Mode.AUTO;
    }

    private static void parseDirectLine(int lineNo, String line, List<Entry> entries, List<String> errors) {
        Matcher m = DIRECT_LINE.matcher(line);
        if (!m.matches()) {
            errors.add(lineNo + "번째 줄: '이름, 행, 열' 형식이 아닙니다. (" + line + ")");
            return;
        }
        String name = m.group(1);
        if (!validName(lineNo, name, errors)) {
            return;
        }
        int row;
        int col;
        try {
            row = Integer.parseInt(m.group(2));
            col = Integer.parseInt(m.group(3));
        } catch (NumberFormatException e) {
            errors.add(rangeError(lineNo));
            return;
        }
        if (!SeatGrid.inRange(row, col)) {
            errors.add(rangeError(lineNo));
            return;
        }
        entries.add(new Entry(lineNo, name, row, col));
    }

    private static void parseNameLine(int lineNo, String line, List<Entry> entries, List<String> errors) {
        for (String token : line.split(",")) {
            String name = token.strip();
            if (name.isEmpty()) {
                continue;
            }
            if (validName(lineNo, name, errors)) {
                entries.add(new Entry(lineNo, name, null, null));
            }
        }
    }

    private static boolean validName(int lineNo, String name, List<String> errors) {
        if (name.isEmpty()) {
            errors.add(lineNo + "번째 줄: 이름이 비어 있습니다.");
            return false;
        }
        if (name.length() > Student.NAME_MAX) {
            errors.add(lineNo + "번째 줄: 이름은 " + Student.NAME_MAX + "자 이하여야 합니다. (" + name + ")");
            return false;
        }
        if (name.chars().allMatch(Character::isDigit)) {
            // 첫 줄은 이름만, 이후 줄에 "이름, 행, 열"을 섞어 쓴 경우를 잡아낸다
            errors.add(lineNo + "번째 줄: 숫자만으로 된 이름은 사용할 수 없습니다. (" + name + ")");
            return false;
        }
        return true;
    }

    private static void checkCount(int count, List<String> errors) {
        if (count == 0 && errors.isEmpty()) {
            errors.add("등록할 수강생을 입력해주세요.");
        } else if (count > MAX_STUDENTS) {
            errors.add("한 번에 최대 " + MAX_STUDENTS + "명까지 등록할 수 있습니다. (입력: " + count + "명)");
        }
    }

    private static String rangeError(int lineNo) {
        return lineNo + "번째 줄: 행은 1~" + SeatGrid.ROWS + ", 열은 1~" + SeatGrid.COLS + " 사이여야 합니다.";
    }
}
