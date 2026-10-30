import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 8] 실패 케이스 축소 검증.
 *
 * 실제 구현에 버그를 넣을 수 없으므로(구현 비수정 원칙), 어댑터 수준에서 결함을 주입한 {@link FaultyHeap} 로
 * "긴 무작위 시퀀스에서 실패 → 최소 재현 시퀀스로 축소 → 회귀 파일 저장 → 파일로 다시 재현" 전 과정을 검증한다.
 */
@DisplayName("8. Test case minimization")
class MinimizerTest {

    /** 결함 주입: decreaseKey 후에는 findMin 이 갱신되지 않은 캐시 값을 돌려준다 (min 포인터 갱신 누락을 흉내) */
    static final class FaultyHeap implements HeapSubject {
        private final FibHeapSubject real = new FibHeapSubject();
        private Integer cachedMin;

        @Override public Object insert(int key) {
            Object n = real.insert(key);
            cachedMin = real.findMin();
            return n;
        }
        @Override public Integer findMin() { return cachedMin; }
        @Override public Integer extractMin() {
            Integer v = real.extractMin();
            cachedMin = real.findMin();
            return v;
        }
        @Override public Object minHandle() { return real.minHandle(); }
        @Override public void decreaseKey(Object h, int k) { real.decreaseKey(h, k); }
        @Override public void decreaseKeyByValue(int o, int n) { real.decreaseKeyByValue(o, n); }
        @Override public void meld(HeapSubject other) {
            real.meld(((FaultyHeap) other).real);
            cachedMin = real.findMin();
            ((FaultyHeap) other).cachedMin = null;
        }
        @Override public int size() { return real.size(); }
        @Override public int keyOf(Object h) { return real.keyOf(h); }
        @Override public void checkInvariants() { real.checkInvariants(); }
        @Override public Set<Object> reachableHandles() { return real.reachableHandles(); }
    }

    private static final Supplier<HeapSubject> FAULTY = FaultyHeap::new;

    @Test
    void randomFailureShrinksToMinimalReproduction() throws Exception {
        long seed = 4242;
        List<HeapOp> ops = OpSequenceGenerator.generate(seed, 2_000, 3, OpSequenceGenerator.Profile.WIDE);
        HeapHarness.Failure failure = assertThrows(HeapHarness.Failure.class,
                () -> HeapHarness.run(ops, 3, FAULTY, HeapHarness.Config.STRICT));

        List<HeapOp> prefix = ops.subList(0, failure.step + 1);
        List<HeapOp> minimal = SequenceMinimizer.minimizeWithValues(prefix, 3, FAULTY, failure.signature);

        // 최소 재현: insert 하나 + 그 노드를 감소시키는 연산 하나 (감소 직후 findMin 비교에서 실패)
        assertEquals(2, minimal.size(), "최소 시퀀스: " + minimal);
        assertEquals(HeapOp.Kind.INSERT, minimal.get(0).kind);
        assertTrue(minimal.get(1).kind == HeapOp.Kind.DECREASE_NODE || minimal.get(1).kind == HeapOp.Kind.DECREASE_VALUE);
        assertTrue(SequenceMinimizer.failsWith(minimal, 3, FAULTY, failure.signature), "축소본도 같은 오류로 실패");
        assertTrue(prefix.size() > 10 * minimal.size(), "원본 " + prefix.size() + "개 → " + minimal.size() + "개");

        // 1-최소성: 어떤 연산을 하나 빼도 더 이상 같은 오류가 나지 않는다
        for (int i = 0; i < minimal.size(); i++) {
            List<HeapOp> smaller = new ArrayList<>(minimal);
            smaller.remove(i);
            assertTrue(!SequenceMinimizer.failsWith(smaller, 3, FAULTY, failure.signature), "제거 가능한 연산이 남음: " + i);
        }
        // 정상 구현에서는 같은 축소본이 통과한다 (결함이 구현이 아니라 주입된 것임을 확인)
        HeapHarness.run(minimal, 3, FibHeapSubject::new, HeapHarness.Config.STRICT);
    }

    @Test
    void reporterSavesReplayableRegressionFile() throws Exception {
        long seed = 99;
        List<HeapOp> ops = OpSequenceGenerator.generate(seed, 500, 2, OpSequenceGenerator.Profile.SMALL_RANGE);
        String previous = System.getProperty("harness.minimize");
        System.setProperty("harness.minimize", "true");      // 변이 실행 등에서 꺼 두었더라도 이 테스트는 축소를 검증
        AssertionError error;
        try {
            error = assertThrows(AssertionError.class, () -> FailureReporter.runOrReport(
                    "MinimizerSelfCheck", seed, "profile=SMALL_RANGE, length=500, heaps=2",
                    ops, 2, FAULTY, HeapHarness.Config.DEFAULT));
        } finally {
            if (previous == null) System.clearProperty("harness.minimize");
            else System.setProperty("harness.minimize", previous);
        }
        assertTrue(error.getMessage().contains("seed           : 99"), error.getMessage());
        assertTrue(error.getMessage().contains("최소 재현 시퀀스"), error.getMessage());

        Path file = FailureReporter.failureDir().resolve("MinimizerSelfCheck-seed99.min.ops");
        assertTrue(Files.exists(file), "회귀 파일 저장: " + file);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<HeapOp> replay = HeapOp.parseAll(lines);
        assertEquals(2, HeapOp.heapCount(lines, -1));
        assertTrue(replay.size() <= 3, "저장된 최소 시퀀스: " + replay);

        String sig = lines.stream().filter(l -> l.startsWith("# signature: ")).findFirst()
                .map(l -> l.substring("# signature: ".length())).orElse(null);
        assertNotNull(sig);
        assertTrue(SequenceMinimizer.failsWith(replay, 2, FAULTY, sig), "파일만으로 같은 오류가 재현됨");
    }

    @Test
    void ddminFindsMinimalFailureInducingSubset() {
        List<Integer> input = new ArrayList<>();
        for (int i = 0; i < 100; i++) input.add(i);
        // 13, 57, 91 이 모두 있어야 실패하는 합성 조건
        List<Integer> result = SequenceMinimizer.minimize(input,
                l -> l.contains(13) && l.contains(57) && l.contains(91));
        assertEquals(List.of(13, 57, 91), result);
    }

    @Test
    void textFormatRoundTrips() {
        List<HeapOp> ops = List.of(HeapOp.insert(0, -5), HeapOp.findMin(1), HeapOp.extractMin(2),
                HeapOp.decreaseNode(0, 3, -2), HeapOp.decreaseValue(1, 0, Integer.MAX_VALUE), HeapOp.meld(0, 2));
        assertEquals(ops, HeapOp.parseAll(List.of(HeapOp.format(ops).split("\n"))));
    }
}
