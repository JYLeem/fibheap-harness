import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 4] 경계 및 예외 상황 테스트.
 *
 * 기대 동작은 실제 구현(Main.FibonacciHeap)의 API 계약을 따른다.
 * ┌───────────────────────────────────────────┬──────────────────────────────────────────────┐
 * │ 상황                                       │ 계약(기대 동작)                                │
 * ├───────────────────────────────────────────┼──────────────────────────────────────────────┤
 * │ 빈 힙 minimum() / extractMin()             │ null 반환, 상태 변화 없음                        │
 * │ decreaseKey(old, new) 에서 old 없음         │ NoSuchElementException, 상태 변화 없음          │
 * │ decreaseKey(…, new > old)                  │ IllegalArgumentException, 상태 변화 없음         │
 * │ decreaseKey(…, new == old)                 │ 아무 것도 하지 않음 (인덱스 순서도 유지)           │
 * │ decreaseKey(old, new), old 가 여러 개       │ 인덱스에서 먼저 찾은 노드 하나만 변경               │
 * │ union(this)                                │ 아무 것도 하지 않음                              │
 * │ union(빈 힙) / 빈 힙.union(x)               │ 원소 합집합, 넘겨준 힙은 빈 힙(재사용 가능)        │
 * │ decreaseKey(Node) 에 삭제된/다른 힙의 노드   │ 사전조건 위반 — 구현이 검사하지 않음 (KnownGapTest) │
 * └───────────────────────────────────────────┴──────────────────────────────────────────────┘
 */
@DisplayName("4. Edge cases")
class EdgeCaseTest {

    // ------------------------------------------------------------------ 빈 힙 / 원소 하나

    @Test
    void emptyHeapFindMinAndExtractMinReturnNull() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        assertNull(h.minimum());
        assertNull(h.extractMin());
        assertNull(h.extractMin(), "반복 호출해도 null");
        assertEquals(0, h.size());
        assertTrue(h.isEmpty());
        HeapInvariantChecker.check(h);
    }

    @Test
    void singleElementExtractLeavesReusableEmptyHeap() {
        Main.FibonacciHeap h = TestHeaps.heapOf(42);
        assertEquals(42, h.minimum());
        assertEquals(42, h.extractMin());
        assertTrue(h.isEmpty());
        HeapInvariantChecker.check(h);
        h.insert(7);
        assertEquals(7, h.minimum());
        HeapInvariantChecker.check(h);
    }

    // ------------------------------------------------------------------ 키 값 경계

    @Test
    void duplicateKeysAreAllKeptAndExtracted() {
        Main.FibonacciHeap h = TestHeaps.heapOf(3, 3, 3, 3, 3, 1, 1);
        assertEquals(List.of(1, 1, 3, 3, 3, 3, 3), TestHeaps.drain(h));
    }

    @Test
    void negativeAndExtremeIntegerKeys() {
        Main.FibonacciHeap h = TestHeaps.heapOf(Integer.MAX_VALUE, Integer.MIN_VALUE, 0, -1,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE + 1);
        assertEquals(Integer.MIN_VALUE, h.minimum());
        assertEquals(List.of(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0,
                Integer.MAX_VALUE, Integer.MAX_VALUE), TestHeaps.drain(h));
    }

    @Test
    void decreaseToIntegerMinValueAtBoundary() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node max = h.insert(Integer.MAX_VALUE);
        Main.FibonacciHeap.Node nearMin = h.insert(Integer.MIN_VALUE + 1);
        h.insert(Integer.MIN_VALUE);
        h.insert(5);
        h.extractMin();                                   // MIN 제거, 통합
        h.decreaseKey(max, Integer.MIN_VALUE);            // MAX → MIN : 가능한 가장 큰 감소폭
        HeapInvariantChecker.check(h);
        h.decreaseKey(nearMin, Integer.MIN_VALUE);        // 동률 최솟값
        HeapInvariantChecker.check(h);
        assertEquals(Integer.MIN_VALUE, h.minimum());
        assertEquals(List.of(Integer.MIN_VALUE, Integer.MIN_VALUE, 5), TestHeaps.drain(h));
    }

    // ------------------------------------------------------------------ 같은 키 노드 / 노드 식별

    @Test
    void decreaseByValueChangesFirstFoundNodeAmongDuplicates() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node a = h.insert(7);
        Main.FibonacciHeap.Node b = h.insert(7);
        Main.FibonacciHeap.Node c = h.insert(7);
        h.decreaseKey(7, 5);
        assertEquals(5, a.key, "가장 먼저 들어온 노드가 바뀐다");
        assertEquals(7, b.key);
        assertEquals(7, c.key);
        h.decreaseKey(7, 6);
        assertEquals(6, b.key, "그다음 노드");
        assertEquals(7, c.key);
        HeapInvariantChecker.check(h);
    }

    @Test
    void decreasedNodeGoesToEndOfNewKeyOrder() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node a = h.insert(9);
        Main.FibonacciHeap.Node b = h.insert(5);
        h.decreaseKey(a, 5);                              // a 가 키 5 목록의 뒤에 붙는다
        h.decreaseKey(5, 1);
        assertEquals(1, b.key, "키 5 목록에서 먼저 있던 b 가 바뀐다");
        assertEquals(5, a.key);
        HeapInvariantChecker.check(h);
    }

    @Test
    void decreaseToSameKeyIsNoOpAndKeepsOrder() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node a = h.insert(4);
        Main.FibonacciHeap.Node b = h.insert(4);
        h.decreaseKey(a, 4);                              // 같은 키: 변화 없음, 순서 유지
        h.decreaseKey(4, 4);
        h.decreaseKey(4, 0);
        assertEquals(0, a.key);
        assertEquals(4, b.key);
        HeapInvariantChecker.check(h);
    }

    // ------------------------------------------------------------------ 통합(consolidate)

    @Test
    void extractMinOfNodeWithManyChildren() {
        Main.FibonacciHeap h = TestHeaps.singleTree(4, 0, 1);           // 키 1..16 의 B4
        assertEquals(4, h.minNode().degree);
        assertEquals(1, h.extractMin());                                // 자식 4개가 루트로 올라감
        HeapInvariantChecker.check(h);
        assertEquals(List.of(0, 1, 2, 3), TestHeaps.rootDegreesSorted(h), "15 = 1111(2) → 차수 0,1,2,3");
        assertEquals(2, h.minimum());
    }

    @Test
    void sameDegreeRootsAreRepeatedlyConsolidated() {
        Main.FibonacciHeap h = TestHeaps.singleTree(6, 0, 1);           // 키 1..64, B6 하나
        for (int k = 1000; k < 1064; k++) h.insert(k);                  // 차수 0 루트 64개 추가
        assertEquals(65, TestHeaps.roots(h).size());
        assertEquals(1, h.extractMin());
        HeapInvariantChecker.check(h);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), TestHeaps.rootDegreesSorted(h), "127 = 1111111(2)");
        assertEquals(127, h.size());
    }

    // ------------------------------------------------------------------ decreaseKey / cascading cut

    @Test
    void decreaseKeyBelowMinimumMovesMinPointer() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);         // 루트 20
        Main.FibonacciHeap.Node deep = TestHeaps.childWithDegree(TestHeaps.childWithDegree(h.minNode(), 2), 1);
        h.decreaseKey(deep, 5);
        HeapInvariantChecker.check(h);
        assertSame(deep, h.minNode());
        assertNull(deep.parent);
        assertEquals(5, h.extractMin());
        HeapInvariantChecker.check(h);
    }

    @Test
    void cascadingCutPropagatesThroughMarkedAncestors() {
        Main.FibonacciHeap h = TestHeaps.singleTree(5, 10, 10);         // 키 20..330, 루트 R = 20
        Main.FibonacciHeap.Node r = h.minNode();
        Main.FibonacciHeap.Node a = TestHeaps.childWithDegree(r, 4);
        Main.FibonacciHeap.Node b = TestHeaps.childWithDegree(a, 3);
        Main.FibonacciHeap.Node c = TestHeaps.childWithDegree(b, 2);
        Main.FibonacciHeap.Node c0 = TestHeaps.childWithDegree(c, 0);
        Main.FibonacciHeap.Node c1 = TestHeaps.childWithDegree(c, 1);
        Main.FibonacciHeap.Node b0 = TestHeaps.childWithDegree(b, 0);

        h.decreaseKey(c0, 1);                       // C 가 처음 자식을 잃음 → mark 만
        HeapInvariantChecker.check(h);
        assertSame(b, c.parent, "처음 자식을 잃은 C 는 잘리지 않는다");
        assertTrue(c.mark);

        h.decreaseKey(b0, 2);                       // B 도 처음 자식을 잃음 → mark
        HeapInvariantChecker.check(h);
        assertTrue(b.mark);
        assertFalse(a.mark);

        h.decreaseKey(c1, 3);                       // C 가 두 번째로 잃음 → C 절단 → B(marked) 절단 → A mark
        HeapInvariantChecker.check(h);
        assertNull(c.parent, "C 는 루트가 됨");
        assertFalse(c.mark, "루트가 되면 mark 해제");
        assertNull(b.parent, "연쇄 절단으로 B 도 루트가 됨");
        assertFalse(b.mark);
        assertSame(r, a.parent, "A 는 처음 자식을 잃었으므로 남는다");
        assertTrue(a.mark);
        assertEquals(3, a.degree);
        assertEquals(1, b.degree);
        assertEquals(0, c.degree);
        assertEquals(5, r.degree);
        assertEquals(6, TestHeaps.roots(h).size(), "R, c0, b0, c1, C, B");
        assertEquals(1, h.minimum());

        List<Integer> expected = new ArrayList<>();
        for (Main.FibonacciHeap.Node n : HeapInvariantChecker.check(h)) expected.add(n.key);
        expected.sort(null);
        assertEquals(expected, TestHeaps.drain(h));
    }

    /**
     * 마킹·연쇄 절단이 빠지면 값은 여전히 맞지만 "차수 상한"이 깨진다 → 값 비교 테스트로는 못 잡는 결함.
     * B6 를 만든 뒤 깊이 2 이상인 모든 노드를 아주 작은 키로 잘라내고 꺼낸다.
     * 올바른 구현: 자식을 두 번 잃은 노드가 연쇄적으로 잘려 차수 상한이 유지된다.
     * 마킹이 없으면: 루트가 차수 6 을 유지한 채 7 노드짜리 "별"이 되어 DEGREE_BOUND(floor(log_phi 7) = 4) 위반.
     */
    @Test
    void degreeBoundSurvivesAdversarialCuts() {
        Main.FibonacciHeap h = TestHeaps.singleTree(6, 1_000, 10);
        Main.FibonacciHeap.Node root = h.minNode();
        List<Main.FibonacciHeap.Node> deep = new ArrayList<>();
        for (Main.FibonacciHeap.Node c : TestHeaps.children(root)) collectDescendants(c, deep);
        int small = -deep.size();
        for (Main.FibonacciHeap.Node d : deep) {
            h.decreaseKey(d, small++);
            HeapInvariantChecker.check(h);
        }
        for (int i = 0; i < deep.size(); i++) {
            h.extractMin();
            HeapInvariantChecker.check(h);
        }
        assertEquals(7, h.size(), "루트와 원래 자식 6개만 남음");
    }

    private static void collectDescendants(Main.FibonacciHeap.Node n, List<Main.FibonacciHeap.Node> out) {
        for (Main.FibonacciHeap.Node c : TestHeaps.children(n)) {
            out.add(c);
            collectDescendants(c, out);
        }
    }

    // ------------------------------------------------------------------ meld

    @Test
    void meldEmptyAndNonEmptyInAllDirections() {
        Main.FibonacciHeap e1 = new Main.FibonacciHeap();
        Main.FibonacciHeap e2 = new Main.FibonacciHeap();
        e1.union(e2);
        HeapInvariantChecker.check(e1);
        assertTrue(e1.isEmpty());

        Main.FibonacciHeap full = TestHeaps.heapOf(4, 2, 9);
        full.union(e1);                                   // 비어 있지 않음 ← 빈 힙
        HeapInvariantChecker.check(full);
        assertEquals(3, full.size());

        Main.FibonacciHeap target = new Main.FibonacciHeap();
        target.union(full);                               // 빈 힙 ← 비어 있지 않음
        HeapInvariantChecker.check(target);
        HeapInvariantChecker.check(full);
        assertEquals(3, target.size());
        assertTrue(full.isEmpty(), "넘겨준 힙은 빈 힙");
        full.insert(1);                                   // 넘겨준 힙은 독립적으로 재사용 가능
        assertEquals(1, full.size());
        assertEquals(3, target.size());
        assertEquals(List.of(2, 4, 9), TestHeaps.drain(target));
    }

    @Test
    void selfMeldIsNoOp() {
        Main.FibonacciHeap h = TestHeaps.heapOf(5, 3, 8);
        h.extractMin();
        h.union(h);
        HeapInvariantChecker.check(h);
        assertEquals(2, h.size());
        assertEquals(List.of(5, 8), TestHeaps.drain(h));
    }

    @Test
    void repeatedMeldOfSameHeapIsHarmless() {
        Main.FibonacciHeap a = TestHeaps.heapOf(1, 2);
        Main.FibonacciHeap b = TestHeaps.heapOf(3);
        a.union(b);
        a.union(b);                                       // b 는 이미 빈 힙 → 변화 없음
        b.union(a);                                       // 역방향 병합: 모두 b 로 이동
        HeapInvariantChecker.check(a);
        HeapInvariantChecker.check(b);
        assertTrue(a.isEmpty());
        assertEquals(List.of(1, 2, 3), TestHeaps.drain(b));
    }

    @Test
    void nodesFromMeldedHeapRemainAddressableInTarget() {
        Main.FibonacciHeap a = TestHeaps.heapOf(10, 20);
        Main.FibonacciHeap b = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node fromB = b.insert(30);
        b.insert(40);
        a.union(b);
        a.decreaseKey(fromB, 1);                          // 핸들 API
        a.decreaseKey(40, 2);                             // 값 API (인덱스 병합 확인)
        HeapInvariantChecker.check(a);
        assertEquals(List.of(1, 2, 10, 20), TestHeaps.drain(a));
    }

    // ------------------------------------------------------------------ 잘못된 요청

    @Test
    void decreaseKeyOfAbsentValueThrowsAndLeavesHeapUnchanged() {
        Main.FibonacciHeap h = TestHeaps.heapOf(5, 1, 9);
        h.extractMin();
        List<Integer> before = HeapInvariantChecker.keys(h);
        assertThrows(NoSuchElementException.class, () -> h.decreaseKey(100, 1));
        assertThrows(NoSuchElementException.class, () -> new Main.FibonacciHeap().decreaseKey(0, -1),
                "빈 힙");
        assertEquals(before, HeapInvariantChecker.keys(h));
    }

    @Test
    void increasingKeyIsRejectedForBothApis() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node n = h.insert(5);
        h.insert(2);
        assertThrows(IllegalArgumentException.class, () -> h.decreaseKey(n, 6));
        assertThrows(IllegalArgumentException.class, () -> h.decreaseKey(5, Integer.MAX_VALUE));
        assertEquals(5, n.key);
        assertEquals(List.of(2, 5), HeapInvariantChecker.keys(h));
    }

    @Test
    void absentKeyIsReportedBeforeIncreaseCheck() {
        Main.FibonacciHeap h = TestHeaps.heapOf(5);
        assertThrows(NoSuchElementException.class, () -> h.decreaseKey(4, 99),
                "없는 키 + 증가 요청이면 '없는 키' 가 먼저 보고된다");
    }
}
