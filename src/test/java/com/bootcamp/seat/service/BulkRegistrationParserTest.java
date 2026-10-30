package com.bootcamp.seat.service;

import com.bootcamp.seat.service.BulkRegistrationParser.Entry;
import com.bootcamp.seat.service.BulkRegistrationParser.Mode;
import com.bootcamp.seat.service.BulkRegistrationParser.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class BulkRegistrationParserTest {

    @Test
    void 쉼표와_줄바꿈으로_구분한_이름은_자동_배치로_인식한다() {
        Result r = BulkRegistrationParser.parse("홍길동, 김철수,이영희\n\n  박민수  \r\n최유리,");

        assertThat(r.mode()).isEqualTo(Mode.AUTO);
        assertThat(r.errors()).isEmpty();
        assertThat(r.entries()).extracting(Entry::line, Entry::name, Entry::row)
                .containsExactly(
                        tuple(1, "홍길동", null), tuple(1, "김철수", null), tuple(1, "이영희", null),
                        tuple(3, "박민수", null), tuple(4, "최유리", null));
    }

    @Test
    void 첫_줄이_이름_숫자_숫자이면_직접_지정으로_인식한다() {
        Result r = BulkRegistrationParser.parse("\n홍길동, 1, 2\n김철수,11,12");

        assertThat(r.mode()).isEqualTo(Mode.DIRECT);
        assertThat(r.errors()).isEmpty();
        assertThat(r.entries()).extracting(Entry::line, Entry::name, Entry::row, Entry::col)
                .containsExactly(tuple(2, "홍길동", 1, 2), tuple(3, "김철수", 11, 12));
    }

    @Test
    void 직접_지정_모드의_형식_오류와_범위_오류는_원문_줄_번호로_알려준다() {
        Result r = BulkRegistrationParser.parse("홍길동, 1, 1\n\n김철수, 1\n이영희, 12, 1\n박민수, 1, 0");

        assertThat(r.hasErrors()).isTrue();
        assertThat(r.errors()).hasSize(3);
        assertThat(r.errors().get(0)).startsWith("3번째 줄").contains("형식");
        assertThat(r.errors().get(1)).startsWith("4번째 줄").contains("1~11");
        assertThat(r.errors().get(2)).startsWith("5번째 줄");
        assertThat(r.entries()).extracting(Entry::name).containsExactly("홍길동");
    }

    @Test
    void 자동_배치_모드에서_숫자만_있는_이름은_오류로_본다() {
        // 첫 줄은 이름만, 다음 줄에 직접 지정 형식을 섞어 쓴 실수
        Result r = BulkRegistrationParser.parse("홍길동\n김철수, 1, 2");

        assertThat(r.mode()).isEqualTo(Mode.AUTO);
        assertThat(r.errors()).hasSize(2).allSatisfy(e -> assertThat(e).startsWith("2번째 줄"));
    }

    @Test
    void 최대_50명까지만_허용한다() {
        String fifty = IntStream.rangeClosed(1, 50).mapToObj(i -> "학생" + i).collect(Collectors.joining(","));

        assertThat(BulkRegistrationParser.parse(fifty).errors()).isEmpty();
        assertThat(BulkRegistrationParser.parse(fifty + ",학생51").errors())
                .singleElement().asString().contains("최대 50명").contains("51명");
    }

    @Test
    void 빈_입력과_너무_긴_이름은_오류다() {
        assertThat(BulkRegistrationParser.parse("  \n ").errors()).containsExactly("등록할 수강생을 입력해주세요.");
        assertThat(BulkRegistrationParser.parse(null).errors()).containsExactly("등록할 수강생을 입력해주세요.");
        assertThat(BulkRegistrationParser.parse("가".repeat(31)).errors())
                .singleElement().asString().startsWith("1번째 줄").contains("30자");
    }

    @Test
    void JSON_목록도_같은_규칙으로_검증한다() {
        Result r = BulkRegistrationParser.fromEntries(List.of(
                new Entry(1, " 홍길동 ", 1, 1),
                new Entry(2, "김철수", null, null),
                new Entry(3, "이영희", 1, null),
                new Entry(4, "", null, null)));

        assertThat(r.mode()).isEqualTo(Mode.DIRECT);
        assertThat(r.entries()).extracting(Entry::name).containsExactly("홍길동", "김철수");
        assertThat(r.errors()).hasSize(2);
        assertThat(r.errors().get(0)).startsWith("3번째 줄");
        assertThat(r.errors().get(1)).startsWith("4번째 줄");
    }
}
