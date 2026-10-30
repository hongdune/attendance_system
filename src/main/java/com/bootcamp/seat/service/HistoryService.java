package com.bootcamp.seat.service;

import com.bootcamp.seat.domain.History;
import com.bootcamp.seat.repository.HistoryRepository;
import com.bootcamp.seat.repository.HistorySpecs;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class HistoryService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;
    /** 엑셀 내보내기 최대 행 수 */
    public static final int MAX_EXPORT_ROWS = 100_000;

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id"));
    private static final String[] HEADERS = {"번호", "수강생명", "기수", "상태변경", "변경시간"};

    private final HistoryRepository historyRepository;

    public HistoryService(HistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * 검색 조건. 종료일시가 분 단위(초 0)로 들어오면 그 분의 마지막 초까지 포함한다
     * (datetime-local 입력은 분 단위이므로 "12:06까지"에 12:06:44 기록이 포함되도록).
     */
    public record Filter(String batchName, String studentName, LocalDateTime startDate, LocalDateTime endDate) {

        LocalDateTime inclusiveEnd() {
            if (endDate == null) {
                return null;
            }
            boolean minutePrecision = endDate.getSecond() == 0 && endDate.getNano() == 0;
            return minutePrecision ? endDate.plusMinutes(1).minusNanos(1) : endDate;
        }
    }

    public Page<History> search(Filter filter, int page, int size) {
        validateRange(filter);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, LATEST_FIRST);
        return historyRepository.findAll(spec(filter), pageable);
    }

    public byte[] exportExcel(Filter filter) {
        validateRange(filter);
        List<History> rows = historyRepository
                .findAll(spec(filter), PageRequest.of(0, MAX_EXPORT_ROWS, LATEST_FIRST))
                .getContent();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("출입 히스토리");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            for (int i = 0; i < rows.size(); i++) {
                History h = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(h.getStudentName());
                row.createCell(2).setCellValue(h.getBatchName());
                row.createCell(3).setCellValue(h.getToStatus().getLabel());
                Cell time = row.createCell(4);
                time.setCellValue(h.getChangedAt());
                time.setCellStyle(dateStyle);
            }
            int[] widths = {8, 16, 16, 12, 22};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 파일 생성 실패", e);
        }
    }

    private static CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void validateRange(Filter filter) {
        if (filter.startDate() != null && filter.endDate() != null
                && filter.startDate().isAfter(filter.inclusiveEnd())) {
            throw SeatException.badRequest("시작일시가 종료일시보다 늦습니다.");
        }
    }

    private static Specification<History> spec(Filter filter) {
        LocalDateTime start = filter.startDate() == null ? null : filter.startDate().truncatedTo(ChronoUnit.SECONDS);
        return HistorySpecs.filter(filter.batchName(), filter.studentName(), start, filter.inclusiveEnd());
    }
}
