import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 1] 불변식 검사기 테스트.
 *
 * 목적: (a) 정상 연산 이후 검사기가 통과하는지, (b) 검사기 자체가 각 손상을 실제로 잡아내는지.
 * (b) 가 없으면 "검사기가 항상 통과하는" 버그를 놓친다. 그래서 정상 힙을 만든 뒤 필드를 직접 손상시키고
 * 기대한 규칙(Rule)으로 실패하는지 확인한다.
 */
@DisplayName("1. Invariant Checker")
class InvariantCheckerTest {

    // ------------------------------------------------------------------ 정상 구조는 통과

    @Test
    void validHeapsPassAfterEveryKindOfOperation() {
        Main.FibonacciHeap h = TestHeaps.heapOf(50, 20, 80, 20, -5, 7, 33, 61, 12, 90, 4, 18);
        HeapInvariantChecker.check(h);
        h.extractMin();                                  // consolidate
        HeapInvariantChecker.check(h);
        h.decreaseKey(90, 1);                            // cut 가능
        HeapInvariantChecker.check(h);
        h.decreaseKey(61, 0);
        HeapInvariantChecker.check(h);
        Main.FibonacciHeap other = TestHeaps.heapOf(3, 3, 100);
        h.union(other);
        HeapInvariantChecker.check(h);
        HeapInvariantChecker.check(other);
        assertEquals(List.of(0, 1, 3, 3, 4, 7, 12, 18, 20, 20, 33, 50, 80, 100), TestHeaps.drain(h));
    }

    @Test
    void checkerReturnsExactlyTheReachableNodes() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);
        assertEquals(8, HeapInvariantChecker.check(h).size());
        assertEquals(List.of(20, 30, 40, 50, 60, 70, 80, 90), HeapInvariantChecker.keys(h));
    }

    // ------------------------------------------------------------------ 손상 탐지

    @Test
    void detectsHeapOrderViolation() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);
        Main.FibonacciHeap.Node child = TestHeaps.childWithDegree(h.minNode(), 2);
        child.key = h.minNode().key - 1;                  // 부모보다 작은 자식
        assertRule(HeapInvariantChecker.Rule.HEAP_ORDER, h);
    }

    @Test
    void detectsRootWithParent() {
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2, 3);
        List<Main.FibonacciHeap.Node> roots = TestHeaps.roots(h);
        roots.get(1).parent = roots.get(2);
        assertRule(HeapInvariantChecker.Rule.ROOT_PARENT, h);
    }

    @Test
    void detectsMarkedRoot() {
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2, 3);
        TestHeaps.roots(h).get(2).mark = true;
        assertRule(HeapInvariantChecker.Rule.ROOT_MARK, h);
    }

    @Test
    void detectsBrokenLeftRightLinks() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);
        Main.FibonacciHeap.Node c = TestHeaps.childWithDegree(h.minNode(), 1);
        c.left = c;                                      // c.left.right != c
        assertRule(HeapInvariantChecker.Rule.LINK_CONSISTENCY, h);
    }

    @Test
    void detectsListLongerThanSize() {
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2, 3, 4);
        TestHeaps.setField(h, "size", 2);                // 링이 size 보다 길다 → 끊김/무한 연결 의심
        assertRule(HeapInvariantChecker.Rule.LIST_NOT_CLOSED, h);
    }

    @Test
    void detectsWrongDegree() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);
        TestHeaps.childWithDegree(h.minNode(), 2).degree = 1;
        assertRule(HeapInvariantChecker.Rule.DEGREE, h);
    }

    @Test
    void detectsWrongParentPointer() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);
        Main.FibonacciHeap.Node p = TestHeaps.childWithDegree(h.minNode(), 2);
        TestHeaps.childWithDegree(p, 0).parent = h.minNode();
        assertRule(HeapInvariantChecker.Rule.PARENT_POINTER, h);
    }

    @Test
    void detectsSharedSubtree() {
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2, 3);
        h.extractMin();                                  // 2 ← 3 트리 하나
        Main.FibonacciHeap.Node root = h.minNode();
        Main.FibonacciHeap.Node extra = h.insert(0);     // 새 루트 0 이 같은 자식을 공유하도록 조작
        extra.child = root.child;
        extra.degree = 1;
        HeapInvariantChecker.Violation v = assertThrows(HeapInvariantChecker.Violation.class,
                () -> HeapInvariantChecker.check(h));
        assertTrue(v.rule == HeapInvariantChecker.Rule.PARENT_POINTER || v.rule == HeapInvariantChecker.Rule.DUPLICATE_VISIT,
                "공유된 서브트리가 PARENT_POINTER 또는 DUPLICATE_VISIT 로 탐지되어야 함: " + v.rule);
    }

    @Test
    void detectsWrongMinPointer() {
        Main.FibonacciHeap h = TestHeaps.heapOf(5, 1, 9);
        Main.FibonacciHeap.Node notMin = null;
        for (Main.FibonacciHeap.Node r : TestHeaps.roots(h)) if (r.key == 9) notMin = r;
        TestHeaps.setField(h, "min", notMin);
        assertRule(HeapInvariantChecker.Rule.MIN_POINTER, h);
    }

    @Test
    void detectsSizeMismatch() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);
        TestHeaps.setField(h, "size", 9);
        assertRule(HeapInvariantChecker.Rule.SIZE, h);
    }

    @Test
    void detectsEmptyStateMismatch() {
        Main.FibonacciHeap h = TestHeaps.heapOf(1);
        TestHeaps.setField(h, "size", 0);
        assertRule(HeapInvariantChecker.Rule.EMPTY_STATE, h);

        Main.FibonacciHeap empty = new Main.FibonacciHeap();
        TestHeaps.setField(empty, "size", 3);
        assertRule(HeapInvariantChecker.Rule.EMPTY_STATE, empty);
    }

    @Test
    void detectsDegreeBoundViolation() {
        // 키 1 루트가 자식 3개(2,3,4)를 직접 갖는 "별" 모양: size 4 에서 허용 차수는 floor(log_phi 4) = 2
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2, 3, 4);
        Main.FibonacciHeap.Node root = null;
        List<Main.FibonacciHeap.Node> roots = TestHeaps.roots(h);
        for (Main.FibonacciHeap.Node r : roots) if (r.key == 1) root = r;
        Main.FibonacciHeap.Node first = null, prev = null;
        for (Main.FibonacciHeap.Node r : roots) {
            if (r == root) continue;
            r.parent = root;
            if (first == null) first = r;
            if (prev != null) {
                prev.right = r;
                r.left = prev;
            }
            prev = r;
        }
        prev.right = first;
        first.left = prev;
        root.left = root;
        root.right = root;
        root.child = first;
        root.degree = 3;
        assertRule(HeapInvariantChecker.Rule.DEGREE_BOUND, h);
    }

    @Test
    void detectsStaleIndexEntry() throws Exception {
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2);
        Main.FibonacciHeap.Node ghost = new Main.FibonacciHeap.Node(2);
        indexOf(h).get(2).add(ghost);                    // 힙에 없는 노드가 인덱스에 남음
        assertRule(HeapInvariantChecker.Rule.INDEX, h);
    }

    @Test
    void detectsIndexKeyMismatch() throws Exception {
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2);
        Main.FibonacciHeap.Node two = indexOf(h).get(2).get(0);
        two.key = 3;                                      // 키만 바뀌고 인덱스는 그대로
        assertRule(HeapInvariantChecker.Rule.INDEX, h);
    }

    // ------------------------------------------------------------------ 도우미

    private static void assertRule(HeapInvariantChecker.Rule expected, Main.FibonacciHeap h) {
        HeapInvariantChecker.Violation v = assertThrows(HeapInvariantChecker.Violation.class,
                () -> HeapInvariantChecker.check(h), "손상이 탐지되어야 함: " + expected);
        assertEquals(expected, v.rule, "탐지 규칙: " + v.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<Main.FibonacciHeap.Node>> indexOf(Main.FibonacciHeap h) throws Exception {
        Field f = Main.FibonacciHeap.class.getDeclaredField("index");
        f.setAccessible(true);
        return (Map<Integer, List<Main.FibonacciHeap.Node>>) f.get(h);
    }
}
