import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 6] 메타모픽 테스트.
 *
 * 개별 입력의 정답을 미리 적지 않고, "입력을 이렇게 바꾸면 출력은 이런 관계여야 한다" 를 검사한다.
 * 기준 구현(PriorityQueue)도 쓰지 않으므로, 기준 구현과 테스트 대상이 같은 실수를 하는 경우에도 독립적인 확인이 된다.
 */
@DisplayName("6. Metamorphic relations")
class MetamorphicTest {

    private static final int ROUNDS = 10 * TestHeaps.scale();

    /** MR1: 같은 원소를 어떤 순서로 넣어도 모두 꺼낸 결과는 같다 */
    @Test
    void insertionOrderDoesNotChangeDrainResult() {
        Random rnd = new Random(1);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> keys = randomKeys(rnd, 1 + rnd.nextInt(300), round % 2 == 0 ? 20 : 1_000_000);
            List<Integer> reference = drainAfterInserting(keys, rnd);
            for (int perm = 0; perm < 3; perm++) {
                List<Integer> shuffled = new ArrayList<>(keys);
                Collections.shuffle(shuffled, rnd);
                assertEquals(reference, drainAfterInserting(shuffled, rnd), "round " + round + " perm " + perm);
            }
        }
    }

    /** MR2: drain(meld(A, B)) == sort(A ∪ B) == merge(drain(A), drain(B)), 그리고 meld 는 교환 법칙을 만족 */
    @Test
    void meldEqualsUnionOfParts() {
        Random rnd = new Random(2);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> ka = randomKeys(rnd, rnd.nextInt(150), 50);
            List<Integer> kb = randomKeys(rnd, rnd.nextInt(150), 50);

            Main.FibonacciHeap ab = build(ka, rnd);
            ab.union(build(kb, rnd));
            Main.FibonacciHeap ba = build(kb, rnd);
            ba.union(build(ka, rnd));

            List<Integer> merged = merge(TestHeaps.drain(build(ka, rnd)), TestHeaps.drain(build(kb, rnd)));
            List<Integer> union = new ArrayList<>(ka);
            union.addAll(kb);
            Collections.sort(union);

            assertEquals(union, merged);
            assertEquals(merged, TestHeaps.drain(ab), "drain(meld(A,B))");
            assertEquals(merged, TestHeaps.drain(ba), "drain(meld(B,A))");
        }
    }

    /** MR3: 어떤 연산 이력 뒤에도, 반복 추출 결과는 오름차순(비내림차순)이다 */
    @Test
    void drainIsAlwaysNonDecreasing() {
        for (long seed = 0; seed < ROUNDS; seed++) {
            Main.FibonacciHeap h = afterRandomHistory(seed);
            List<Integer> out = TestHeaps.drain(h);
            for (int i = 1; i < out.size(); i++) {
                assertTrue(out.get(i - 1) <= out.get(i), "seed " + seed + ": " + out.get(i - 1) + " > " + out.get(i));
            }
        }
    }

    /** MR4: decreaseKey(x, k) 후 멀티셋 = 기존 - {old} + {k}, min' = min(min, k), 결과는 같은 멀티셋을 새로 넣은 힙과 같다 */
    @Test
    void decreaseKeyRelation() {
        Random rnd = new Random(4);
        for (int round = 0; round < ROUNDS * 5; round++) {
            Main.FibonacciHeap h = afterRandomHistory(round);
            if (h.isEmpty()) continue;
            List<Main.FibonacciHeap.Node> nodes = new ArrayList<>(HeapInvariantChecker.check(h));
            Main.FibonacciHeap.Node x = nodes.get(rnd.nextInt(nodes.size()));
            List<Integer> before = HeapInvariantChecker.keys(h);
            int oldMin = h.minimum();
            int old = x.key;
            int k = (int) Math.max(Integer.MIN_VALUE, (long) old - rnd.nextInt(1000));

            h.decreaseKey(x, k);

            List<Integer> expected = new ArrayList<>(before);
            expected.remove(Integer.valueOf(old));
            expected.add(k);
            Collections.sort(expected);
            assertEquals(k, x.key, "대상 키가 감소");
            assertEquals(Math.min(oldMin, k), h.minimum(), "min' = min(min, k)");
            assertEquals(expected, HeapInvariantChecker.keys(h), "멀티셋 관계");
            assertEquals(TestHeaps.drain(build(expected, rnd)), TestHeaps.drain(h), "새로 만든 힙과 같은 출력");
        }
    }

    /** MR5: t 개를 넣으면 size 가 t 만큼 증가하고, 비어 있지 않은 힙에서 extractMin 하면 정확히 1 감소 */
    @Test
    void sizeChangesByExactAmount() {
        Random rnd = new Random(5);
        for (int round = 0; round < ROUNDS; round++) {
            Main.FibonacciHeap h = afterRandomHistory(round + 100);
            int before = h.size();
            int t = rnd.nextInt(50);
            for (int i = 0; i < t; i++) h.insert(rnd.nextInt(100));
            assertEquals(before + t, h.size());
            int s = h.size();
            if (s > 0) {
                h.extractMin();
                assertEquals(s - 1, h.size());
            }
            HeapInvariantChecker.check(h);
        }
    }

    /** MR6: 중복이 많은 입력에서도 연산 전후 멀티셋(값별 개수)이 정확히 보존된다 */
    @Test
    void multisetIsPreservedWithHeavyDuplicates() {
        Random rnd = new Random(6);
        for (int round = 0; round < ROUNDS; round++) {
            Main.FibonacciHeap h = new Main.FibonacciHeap();
            Map<Integer, Integer> counts = new HashMap<>();
            for (int i = 0; i < 200; i++) {
                int k = rnd.nextInt(4);
                h.insert(k);
                counts.merge(k, 1, Integer::sum);
            }
            for (int i = 0; i < 40; i++) {
                int removed = h.extractMin();
                counts.merge(removed, -1, Integer::sum);
                int old = rnd.nextInt(4);
                if (counts.getOrDefault(old, 0) > 0) {
                    h.decreaseKey(old, old - 1);
                    counts.merge(old, -1, Integer::sum);
                    counts.merge(old - 1, 1, Integer::sum);
                }
            }
            Map<Integer, Integer> actual = new HashMap<>();
            for (int k : TestHeaps.drain(h)) actual.merge(k, 1, Integer::sum);
            counts.values().removeIf(c -> c == 0);
            assertEquals(counts, actual, "round " + round);
        }
    }

    /** MR7: 최솟값 k 개를 꺼냈다가 다시 넣으면 최종 출력은 원래와 같다 */
    @Test
    void extractThenReinsertIsIdentity() {
        Random rnd = new Random(7);
        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> keys = randomKeys(rnd, 1 + rnd.nextInt(200), 100);
            List<Integer> reference = drainAfterInserting(keys, rnd);
            Main.FibonacciHeap h = build(keys, rnd);
            int k = rnd.nextInt(keys.size() + 1);
            List<Integer> taken = new ArrayList<>();
            for (int i = 0; i < k; i++) taken.add(h.extractMin());
            for (int x : taken) h.insert(x);
            assertEquals(reference, TestHeaps.drain(h));
        }
    }

    // ------------------------------------------------------------------ 도우미

    private static List<Integer> randomKeys(Random rnd, int n, int bound) {
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) keys.add(rnd.nextInt(bound) - bound / 2);
        return keys;
    }

    /** 삽입 중간중간 extractMin + 재삽입으로 내부 구조를 다양하게 만든 뒤 반환 (멀티셋은 그대로) */
    private static Main.FibonacciHeap build(List<Integer> keys, Random rnd) {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        for (int k : keys) {
            h.insert(k);
            if (rnd.nextInt(10) == 0) h.insert(h.extractMin());
        }
        return h;
    }

    private static List<Integer> drainAfterInserting(List<Integer> keys, Random rnd) {
        return TestHeaps.drain(build(keys, rnd));
    }

    private static Main.FibonacciHeap afterRandomHistory(long seed) {
        Random rnd = new Random(seed);
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        List<Main.FibonacciHeap.Node> live = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            int r = rnd.nextInt(10);
            if (r < 5 || live.isEmpty()) {
                live.add(h.insert(rnd.nextInt(10_000)));
            } else if (r < 7) {
                live.remove(h.minNode());
                h.extractMin();
            } else {
                Main.FibonacciHeap.Node n = live.get(rnd.nextInt(live.size()));
                h.decreaseKey(n, n.key - rnd.nextInt(500));
            }
        }
        HeapInvariantChecker.check(h);
        return h;
    }

    private static List<Integer> merge(List<Integer> a, List<Integer> b) {
        List<Integer> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.size() || j < b.size()) {
            if (j >= b.size() || (i < a.size() && a.get(i) <= b.get(j))) out.add(a.get(i++));
            else out.add(b.get(j++));
        }
        return out;
    }
}
