import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 8] 회귀 테스트: src/test/resources/regressions/*.ops 의 시퀀스를 STRICT 설정으로 다시 실행한다.
 *
 * 무작위 테스트가 실패하면 FailureReporter 가 만든 *.min.ops 를 이 디렉터리에 복사해 넣는다.
 * 버그를 고친 뒤에도 같은 최소 시퀀스가 계속 검사되므로 같은 버그가 다시 들어오면 바로 실패한다.
 */
@DisplayName("8b. Regression sequences")
class RegressionTest {

    @Test
    void allSavedSequencesPass() throws Exception {
        URL url = RegressionTest.class.getClassLoader().getResource("regressions");
        assertTrue(url != null, "클래스패스에 regressions 디렉터리가 있어야 함");
        Path dir = Paths.get(url.toURI());
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".ops")).sorted().collect(Collectors.toList());
        }
        assertTrue(!files.isEmpty(), "회귀 파일이 하나 이상 있어야 함");

        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<HeapOp> ops = HeapOp.parseAll(lines);
            int heaps = HeapOp.heapCount(lines, 1);
            try {
                HeapHarness.run(ops, heaps, FibHeapSubject::new, HeapHarness.Config.STRICT);
            } catch (HeapHarness.Failure f) {
                failures.add(file.getFileName() + " : " + f.getMessage());
            }
        }
        if (!failures.isEmpty()) fail("회귀 실패:\n  " + String.join("\n  ", failures));
    }
}
