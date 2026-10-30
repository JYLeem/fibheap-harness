import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 7-b] 파일 입력 퍼즈 테스트.
 *
 * data.txt: 무작위 토큰열(정상 구조를 깨뜨린 변형 포함) → Main.input 은
 *           명세상 올바르면 성공하고 내용이 같아야 하며, 아니면 InputFormatException 만 던져야 한다
 *           (NumberFormatException, IndexOutOfBounds 등 처리되지 않은 예외가 나오면 실패).
 * test.txt: 정상·비정상 명령 혼합 → 각 비어 있지 않은 줄마다 결과가 정확히 한 줄 있어야 하고,
 *           결과는 {@link CommandOracle}(PriorityQueue 기반 독립 해석기)과 같아야 한다.
 * 실패하면 seed 와 입력 파일을 target/harness-failures 에 저장한다.
 */
@DisplayName("7b. Fuzz: data.txt / test.txt parsing")
class FileParsingFuzzTest {

    private static final String[] GARBAGE = {
            "x", "1.5", "-", "+3", "--1", "0x10", "99999999999", "-2147483649", "١٢", "２", " ", "NaN", "null", "#"
    };

    @Test
    void randomDataFilesEitherParseCorrectlyOrRaiseFormatError() throws Exception {
        for (long seed : TestHeaps.seeds(70_000, 100 * TestHeaps.scale())) {
            Random rnd = new Random(seed);
            String data = randomData(rnd);
            List<List<Integer>> expected = CommandOracle.parseData(data);
            try {
                MainPipeline.Run run = MainPipeline.run(data, drainCommands(expected));
                if (expected == null) {
                    save("data-accepted", seed, data, "");
                    fail("seed " + seed + ": 명세 위반 data.txt 가 받아들여짐: " + show(data));
                }
                assertEquals(expectedDrain(expected), run.resultLines, "seed " + seed + ": 읽어 들인 내용");
            } catch (Main.InputFormatException e) {
                if (expected != null) {
                    save("data-rejected", seed, data, "");
                    fail("seed " + seed + ": 올바른 data.txt 가 거부됨 (" + e.getMessage() + "): " + show(data));
                }
            } catch (Exception | Error e) {
                if (e instanceof AssertionError) throw e;
                save("data-crash", seed, data, "");
                throw new AssertionError("seed " + seed + ": 처리되지 않은 예외 " + e + " / 입력 " + show(data), e);
            }
        }
    }

    @Test
    void randomCommandFilesMatchOracleLineByLine() throws Exception {
        for (long seed : TestHeaps.seeds(80_000, 60 * TestHeaps.scale())) {
            Random rnd = new Random(seed);
            int m = 1 + rnd.nextInt(4);
            List<List<Integer>> initial = new ArrayList<>();
            StringBuilder data = new StringBuilder().append(m).append('\n');
            for (int i = 0; i < m; i++) {
                int n = rnd.nextInt(6);
                List<Integer> keys = new ArrayList<>();
                data.append(n).append('\n');
                for (int j = 0; j < n; j++) {
                    int k = rnd.nextInt(30);
                    keys.add(k);
                    data.append(k).append(' ');
                }
                data.append('\n');
                initial.add(keys);
            }
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < 150; i++) lines.add(randomCommand(rnd, m));
            String commands = String.join(rnd.nextBoolean() ? "\n" : "\r\n", lines);

            CommandOracle oracle = new CommandOracle(initial);
            List<String> expected = new ArrayList<>();
            for (String line : commands.split("\r?\n", -1)) {
                String r = oracle.apply(line);
                if (r != null) expected.add(r);
            }
            MainPipeline.Run run;
            try {
                run = MainPipeline.run(data.toString(), commands);
            } catch (Exception e) {
                save("commands-crash", seed, data.toString(), commands);
                throw new AssertionError("seed " + seed + ": 실행 중 예외 " + e, e);
            }
            if (expected.size() != run.resultLines.size()) {
                save("commands-linecount", seed, data.toString(), commands);
                fail("seed " + seed + ": 결과 줄 수 기대 " + expected.size() + ", 실제 " + run.resultLines.size());
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!CommandOracle.matches(expected.get(i), run.resultLines.get(i))) {
                    save("commands-mismatch", seed, data.toString(), commands);
                    fail("seed " + seed + ", 결과 " + i + "번째 줄: 기대 " + expected.get(i)
                            + ", 실제 " + run.resultLines.get(i));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 생성기

    private static String randomData(Random rnd) {
        List<String> tokens = new ArrayList<>();
        int m = rnd.nextInt(4);
        tokens.add(String.valueOf(m));
        for (int i = 0; i < m; i++) {
            int n = rnd.nextInt(5);
            tokens.add(String.valueOf(n));
            for (int j = 0; j < n; j++) tokens.add(String.valueOf(rnd.nextInt(200) - 100));
        }
        int mutations = rnd.nextInt(3);                       // 0 이면 정상 파일
        for (int i = 0; i < mutations && !tokens.isEmpty(); i++) {
            int pos = rnd.nextInt(tokens.size());
            switch (rnd.nextInt(5)) {
                case 0: tokens.remove(pos); break;                                      // 누락
                case 1: tokens.add(pos, String.valueOf(rnd.nextInt(10))); break;        // 추가
                case 2: tokens.set(pos, GARBAGE[rnd.nextInt(GARBAGE.length)]); break;   // 잘못된 값
                case 3: tokens.set(pos, String.valueOf(-1 - rnd.nextInt(3))); break;    // 음수
                default: tokens.set(pos, String.valueOf(rnd.nextInt())); break;         // 아주 큰 값
            }
        }
        StringBuilder sb = new StringBuilder(rnd.nextInt(10) == 0 ? "﻿" : "");
        String[] seps = {" ", "\n", "\t", "  ", "\r\n", " \n "};
        for (String t : tokens) sb.append(t).append(seps[rnd.nextInt(seps.length)]);
        return sb.toString();
    }

    private static String randomCommand(Random rnd, int m) {
        int h = rnd.nextInt(10) == 0 ? m + rnd.nextInt(3) : rnd.nextInt(m);
        String sp = rnd.nextInt(8) == 0 ? "\t " : " ";
        switch (rnd.nextInt(12)) {
            case 0: case 1: return "push" + sp + h + sp + (rnd.nextInt(60) - 10);
            case 2: return "pop" + sp + h;
            case 3: return "top" + sp + h;
            case 4: case 5: return "decrease_key" + sp + h + sp + rnd.nextInt(30) + sp + (rnd.nextInt(40) - 10);
            case 6: return "union" + sp + h + sp + rnd.nextInt(m + 1);
            case 7: return "";
            case 8: return GARBAGE[rnd.nextInt(GARBAGE.length)] + " " + h;
            case 9: return "push " + h;                                   // 인자 부족
            case 10: return "pop " + h + " " + h;                          // 인자 초과
            default: return "push " + GARBAGE[rnd.nextInt(GARBAGE.length)] + " 1";
        }
    }

    private static String drainCommands(List<List<Integer>> heaps) {
        if (heaps == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < heaps.size(); i++) {
            for (int j = 0; j <= heaps.get(i).size(); j++) sb.append("pop ").append(i).append('\n');
        }
        return sb.toString();
    }

    private static List<String> expectedDrain(List<List<Integer>> heaps) {
        List<String> out = new ArrayList<>();
        for (List<Integer> keys : heaps) {
            List<Integer> sorted = new ArrayList<>(keys);
            sorted.sort(null);
            for (int k : sorted) out.add(String.valueOf(k));
            out.add("EMPTY");
        }
        return out;
    }

    private static void save(String kind, long seed, String data, String commands) {
        try {
            Path dir = FailureReporter.failureDir().resolve("parsing-" + kind + "-seed" + seed);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("data.txt"), data, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("test.txt"), commands, StandardCharsets.UTF_8);
            System.err.println("퍼즈 실패 입력 저장: " + dir.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("퍼즈 실패 입력 저장 실패: " + e);
        }
    }

    private static String show(String s) {
        return s.replace("\n", "⏎").replace("\r", "␍").replace("\t", "⇥").replace("﻿", "<BOM>");
    }
}
