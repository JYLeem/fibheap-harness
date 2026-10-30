import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 4·7-b] 파일 입출력 경계 테스트 (자료구조 테스트와 분리).
 * data.txt / test.txt / result.txt 명세를 직접 확인한다.
 */
@DisplayName("7b. File parsing edge cases")
class FileParsingTest {

    private static final String DATA = "3\n3\n5 1 9\n2\n3 7\n0\n";

    // ------------------------------------------------------------------ data.txt

    @Test
    void missingDataFileIsReported() {
        assertThrows(NoSuchFileException.class, () -> Main.input("/nonexistent/dir/data.txt"));
    }

    @Test
    void emptyDataFileIsFormatError() {
        assertThrows(Main.InputFormatException.class, () -> MainPipeline.input(""));
        assertThrows(Main.InputFormatException.class, () -> MainPipeline.input("   \n\n\t"));
    }

    @Test
    void malformedDataIsFormatError() {
        String[] bad = {
                "2\n3\n1 2",          // 원소 부족
                "1\n2\n1 x",          // 정수 아님
                "1\n1\n5 6",          // 남는 값
                "-1",                 // 음수 m
                "1\n-2",              // 음수 n
                "1\n2\n1 2.5",        // 실수
                "1\n1\n99999999999",  // int 범위 초과
                "x",                  // m 이 정수 아님
        };
        for (String data : bad) {
            assertThrows(Main.InputFormatException.class, () -> MainPipeline.input(data), "입력: " + data.replace("\n", "⏎"));
        }
    }

    @Test
    void zeroHeapsAndEmptyHeapsAreValid() {
        assertDoesNotThrow(() -> MainPipeline.input("0"));
        assertDoesNotThrow(() -> MainPipeline.input("2 0 0"));
    }

    @Test
    void layoutIndependentTokensBomAndCrlfAreAccepted() throws Exception {
        String data = "﻿2\r\n2 4\t1\r\n\r\n1   8\r\n";
        MainPipeline.Run run = MainPipeline.run(data, "top 0\npop 0\npop 0\npop 0\ntop 1\n");
        assertEquals(List.of("1", "1", "4", "EMPTY", "8"), run.resultLines);
    }

    @Test
    void invalidUtf8IsReportedAsIoError() {
        byte[] data = {'1', ' ', '1', ' ', (byte) 0xC3, (byte) 0x28};
        assertThrows(IOException.class, () -> MainPipeline.run(data, "top 0\n".getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------ test.txt / result.txt

    @Test
    void everyCommandProducesExactlyOneResultLine() throws Exception {
        String commands = String.join("\n",
                "top 0",                 // 1
                "pop 0",                 // 1
                "push 2 4",              // OK
                "decrease_key 1 7 2",    // OK
                "union 0 1",             // OK
                "",                      // 빈 줄 → 결과 없음
                "   ",                   // 공백만 → 결과 없음
                "top 0",                 // 2
                "pop 1",                 // EMPTY
                "decrease_key 0 100 1",  // ERROR 없는 키
                "decrease_key 0 5 6",    // ERROR 증가
                "decrease_key 0 5 5",    // OK (같은 키)
                "union 0 0",             // ERROR 자기 자신
                "jump 0",                // ERROR 알 수 없는 명령
                "PUSH 0 1",              // ERROR 대소문자 구분
                "push 9 1",              // ERROR 범위
                "push -1 1",             // ERROR 음수 번호
                "push 0",                // ERROR 인자 부족
                "pop 0 0",               // ERROR 인자 초과
                "push 0 abc",            // ERROR 정수 아님
                "push 0 2147483648",     // ERROR int 범위 초과
                "\tpush   0\t-3  ",      // OK 공백·탭 혼합
                "pop 0", "pop 0", "pop 0", "pop 0", "pop 0", "pop 0");
        MainPipeline.Run run = MainPipeline.run(DATA, commands);
        List<String> expected = List.of("1", "1", "OK", "OK", "OK", "2", "EMPTY",
                "ERROR", "ERROR", "OK", "ERROR", "ERROR", "ERROR", "ERROR", "ERROR", "ERROR", "ERROR", "ERROR",
                "ERROR", "OK", "-3", "2", "3", "5", "9", "EMPTY");
        assertEquals(expected.size(), run.resultLines.size(), "결과 줄 수: " + run.resultLines);
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(CommandOracle.matches(expected.get(i), run.resultLines.get(i)),
                    i + "번째 결과: 기대 " + expected.get(i) + ", 실제 " + run.resultLines.get(i));
        }
    }

    @Test
    void resultFileUsesLfAndUtf8() throws Exception {
        MainPipeline.Run run = MainPipeline.run(DATA, "top 0\njump 0\n");
        String text = new String(run.resultBytes, StandardCharsets.UTF_8);
        assertFalse(text.contains("\r"), "CR 없이 LF 만 사용");
        assertTrue(text.endsWith("\n"), "마지막 줄도 LF 로 끝남");
        assertTrue(text.contains("알 수 없는 명령어"), "UTF-8 한글 사유");
    }

    @Test
    void missingTestFileIsReported() throws Exception {
        MainPipeline.input(DATA);
        assertThrows(NoSuchFileException.class, () -> Main.execute("/nonexistent/dir/test.txt"));
    }
}
