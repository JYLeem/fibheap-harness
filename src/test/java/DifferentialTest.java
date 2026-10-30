import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 2] 차등 테스트: java.util.PriorityQueue<Integer> 를 기준으로 결과를 비교한다.
 *
 * 앞부분은 PriorityQueue 와 직접 나란히 비교하는 명시적 시나리오,
 * 뒷부분은 {@link HeapHarness}(내부 기준이 PriorityQueue + 핸들 표)로 무작위 시퀀스를 비교한다.
 * decreaseKey 는 PriorityQueue 에서 remove(old) + add(new) 로 반영한다. 중복 키가 있어도
 * remove 는 같은 값 하나만 지우므로 멀티셋 의미가 유지된다.
 */
@DisplayName("2. Differential (vs PriorityQueue)")
class DifferentialTest {

    @Test
    void insertFindMinExtractMinMatchPriorityQueue() {
        int[] keys = {5, -3, 5, 0, Integer.MAX_VALUE, -3, Integer.MIN_VALUE, 42, 7, 7, 7, -1};
        Main.FibonacciHeap fib = new Main.FibonacciHeap();
        PriorityQueue<Integer> ref = new PriorityQueue<>();
        for (int k : keys) {
            fib.insert(k);
            ref.add(k);
            compare(fib, ref);
        }
        while (!ref.isEmpty()) {
            assertEquals(ref.poll(), fib.extractMin());
            compare(fib, ref);
        }
        assertNull(fib.extractMin(), "빈 힙 삭제는 null (PriorityQueue.poll 과 같음)");
        assertNull(ref.poll());
    }

    @Test
    void decreaseKeyByHandleMatchesRemoveAndReinsert() {
        Main.FibonacciHeap fib = new Main.FibonacciHeap();
        PriorityQueue<Integer> ref = new PriorityQueue<>();
        List<Main.FibonacciHeap.Node> handles = new ArrayList<>();
        for (int k = 10; k <= 330; k += 10) {
            handles.add(fib.insert(k));
            ref.add(k);
        }
        assertEquals(ref.poll(), fib.extractMin());     // 10 제거 → 32 개가 하나의 B5 로 통합
        handles.remove(0);
        compare(fib, ref);

        Random rnd = new Random(7);
        for (int i = 0; i < 200; i++) {
            Main.FibonacciHeap.Node n = handles.get(rnd.nextInt(handles.size()));
            int newKey = n.key - rnd.nextInt(30);
            ref.remove(n.key);
            ref.add(newKey);
            fib.decreaseKey(n, newKey);
            compare(fib, ref);
            if (i % 10 == 9) {                          // 가끔 삭제해서 다시 통합되게 함
                Main.FibonacciHeap.Node min = fib.minNode();
                assertEquals(ref.poll(), fib.extractMin());
                handles.remove(min);
                compare(fib, ref);
            }
        }
    }

    @Test
    void decreaseKeyByValueWithDuplicatesKeepsMultiset() {
        Main.FibonacciHeap fib = TestHeaps.heapOf(4, 4, 4, 9, 9, 1);
        PriorityQueue<Integer> ref = new PriorityQueue<>(List.of(4, 4, 4, 9, 9, 1));
        int[][] decreases = {{4, 2}, {9, 9}, {9, -7}, {4, 4}, {4, 0}};
        for (int[] d : decreases) {
            fib.decreaseKey(d[0], d[1]);
            ref.remove(d[0]);
            ref.add(d[1]);
            compare(fib, ref);
        }
        assertEquals(drain(ref), TestHeaps.drain(fib));
    }

    @Test
    void meldMatchesUnionOfQueues() {
        Main.FibonacciHeap a = TestHeaps.heapOf(8, -2, 8, 30);
        Main.FibonacciHeap b = TestHeaps.heapOf(-2, 5, Integer.MIN_VALUE);
        a.extractMin();                                  // a 를 통합된 상태로
        PriorityQueue<Integer> ra = new PriorityQueue<>(List.of(8, 8, 30));
        PriorityQueue<Integer> rb = new PriorityQueue<>(List.of(-2, 5, Integer.MIN_VALUE));
        a.union(b);
        ra.addAll(rb);
        rb.clear();
        compare(a, ra);
        compare(b, rb);
        assertEquals(drain(ra), TestHeaps.drain(a));
    }

    @Test
    void randomSequencesMatchReferenceModel() {
        OpSequenceGenerator.Profile[] profiles = {
                OpSequenceGenerator.Profile.SMALL_RANGE, OpSequenceGenerator.Profile.WIDE,
                OpSequenceGenerator.Profile.EXTREMES
        };
        for (OpSequenceGenerator.Profile p : profiles) {
            for (long seed : TestHeaps.seeds(1000 + p.ordinal() * 100L, 5 * TestHeaps.scale())) {
                List<HeapOp> ops = OpSequenceGenerator.generate(seed, 600, 2, p);
                FailureReporter.runOrReport("Differential-" + p, seed, "profile=" + p + ", length=600, heaps=2",
                        ops, 2, FibHeapSubject::new, HeapHarness.Config.DEFAULT);
            }
        }
    }

    private static void compare(Main.FibonacciHeap fib, PriorityQueue<Integer> ref) {
        HeapInvariantChecker.check(fib);
        assertEquals(ref.size(), fib.size(), "size");
        assertEquals(ref.peek(), fib.minimum(), "findMin");
        List<Integer> expected = new ArrayList<>(ref);
        expected.sort(null);
        assertEquals(expected, HeapInvariantChecker.keys(fib), "multiset");
    }

    private static List<Integer> drain(PriorityQueue<Integer> q) {
        List<Integer> out = new ArrayList<>();
        while (!q.isEmpty()) out.add(q.poll());
        return out;
    }
}
