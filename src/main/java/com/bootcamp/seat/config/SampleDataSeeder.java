package com.bootcamp.seat.config;

import com.bootcamp.seat.domain.Batch;
import com.bootcamp.seat.domain.History;
import com.bootcamp.seat.domain.SeatStatus;
import com.bootcamp.seat.domain.Student;
import com.bootcamp.seat.repository.BatchRepository;
import com.bootcamp.seat.repository.HistoryRepository;
import com.bootcamp.seat.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 개발용 샘플 데이터. seat.seed.enabled=true 이고 기수가 하나도 없을 때만 실행된다.
 * 2인 1조 책상 배치: 열 2·3 / 5·6 / 8·9 / 11
 */
@Component
@ConditionalOnProperty(name = "seat.seed.enabled", havingValue = "true")
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    static final int[] DESK_COLUMNS = {2, 3, 5, 6, 8, 9, 11};

    private static final String[] SURNAMES = {
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍"};
    private static final String[] GIVEN_NAMES = {
            "민준", "서연", "도윤", "하은", "시우", "지유", "주원", "서윤", "예준", "하린", "지호", "수아",
            "건우", "지아", "현우", "채원", "유준", "다은", "준서", "소율", "은우", "예린", "선우", "유나"};

    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final HistoryRepository historyRepository;
    private final Clock clock;

    public SampleDataSeeder(BatchRepository batchRepository, StudentRepository studentRepository,
                            HistoryRepository historyRepository, Clock clock) {
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (batchRepository.count() > 0) {
            return;
        }
        Random random = new Random(20260902L);
        List<String> names = uniqueNames(random, 32 + 24);
        seedBatch("AINLP5", names.subList(0, 32), random);
        seedBatch("BESP5", names.subList(32, 56), random);
        log.info("샘플 데이터 생성: 기수 AINLP5(32명), BESP5(24명)");
    }

    private void seedBatch(String batchName, List<String> names, Random random) {
        Batch batch = batchRepository.save(new Batch(batchName));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime morning = now.minusHours(3);

        for (int i = 0; i < names.size(); i++) {
            int row = i / DESK_COLUMNS.length + 1;
            int col = DESK_COLUMNS[i % DESK_COLUMNS.length];
            Student student = studentRepository.save(new Student(names.get(i), batch, row, col, morning));

            // 대부분 입실, 일부는 다양한 상태로 (타이머·경고·위험 표시를 바로 확인할 수 있게)
            record(student, i == 3 ? SeatStatus.LATE : SeatStatus.IN, morning.plusMinutes(random.nextInt(20)));
            switch (i) {
                case 1 -> record(student, SeatStatus.WC, now.minusMinutes(3));
                case 4 -> record(student, SeatStatus.SMOKE, now.minusMinutes(12));
                case 7 -> record(student, SeatStatus.STORE, now.minusMinutes(6));
                case 10 -> record(student, SeatStatus.OUT_OFFICE, now.minusMinutes(95));
                case 13 -> record(student, SeatStatus.ABSENT, morning);
                case 16 -> record(student, SeatStatus.GIVEUP, morning.plusMinutes(30));
                default -> {
                }
            }
            if (i == 5 || i == 8) {
                student.enterMeetingRoom(i == 5 ? 1 : 2);
            }
        }
    }

    /** 상태를 바꾸고 히스토리를 남긴다 (실제 서비스와 같은 규칙) */
    private void record(Student student, SeatStatus status, LocalDateTime at) {
        student.changeStatus(status, at);
        historyRepository.save(History.of(student));
    }

    private static List<String> uniqueNames(Random random, int count) {
        Set<String> names = new LinkedHashSet<>();
        while (names.size() < count) {
            names.add(SURNAMES[random.nextInt(SURNAMES.length)] + GIVEN_NAMES[random.nextInt(GIVEN_NAMES.length)]);
        }
        return new ArrayList<>(names);
    }
}
