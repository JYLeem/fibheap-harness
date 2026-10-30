import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 9] 커버리지 기반 테스트: 구현의 주요 분기를 하나씩 겨냥한다.
 *
 * 각 테스트 이름 앞의 표기는 Main.FibonacciHeap 의 분기다. JaCoCo 리포트에서 노란/빨간 분기를 찾으면
 * 그 조건을 만드는 테스트를 이 클래스에 추가한다. 분기를 "실행"만 하지 않고 결과와 불변식도 함께 검사한다.
 *
 * 정상 흐름에서 도달할 수 없는 분기 (커버리지 100% 를 목표로 하지 않는 이유)
 *   - decreaseKey(int,int) 의 nodes.isEmpty() : removeFromIndex 가 빈 목록을 즉시 지우므로 항상 false
 *   - removeFromIndex 의 nodes == null       : 인덱스에 없는 노드를 지울 때만 — 사전조건 위반(KnownGapTest)에서만 발생
 *   - validate()/check() 의 실패 분기         : 구현이 올바르면 실행되지 않음 (InvariantCheckerTest 는 테스트 쪽 검사기를 검증)
 */
@DisplayName("9. Branch-targeted tests")
class BranchTargetedTest {

    // ---------------------------------------------------------------- addRoot / insert

    @Test
    void addRoot_minNull_firstInsertBecomesMin() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node n = h.insert(3);
        assertSame(n, h.minNode());
        assertSame(n, n.left);
        assertSame(n, n.right);
        HeapInvariantChecker.check(h);
    }

    @Test
    void addRoot_smallerKeyUpdatesMin_largerOrEqualKeepsMin() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node first = h.insert(5);
        h.insert(8);
        h.insert(5);                                     // 같은 키: min 유지 (< 비교)
        assertSame(first, h.minNode());
        Main.FibonacciHeap.Node smaller = h.insert(1);
        assertSame(smaller, h.minNode());
        HeapInvariantChecker.check(h);
    }

    // ---------------------------------------------------------------- union

    @Test
    void union_otherIsThis_otherEmpty_thisEmpty() {
        Main.FibonacciHeap h = TestHeaps.heapOf(2, 1);
        h.union(h);                                      // other == this
        h.union(new Main.FibonacciHeap());               // other.min == null
        assertEquals(2, h.size());
        Main.FibonacciHeap empty = new Main.FibonacciHeap();
        empty.union(h);                                  // this.min == null
        assertEquals(1, empty.minimum());
        HeapInvariantChecker.check(empty);
        HeapInvariantChecker.check(h);
    }

    @Test
    void union_otherMinSmaller_andLarger() {
        Main.FibonacciHeap a = TestHeaps.heapOf(5);
        a.union(TestHeaps.heapOf(3));                    // b.key < a.key → min 교체
        assertEquals(3, a.minimum());
        a.union(TestHeaps.heapOf(9));                    // b.key >= a.key → 유지
        assertEquals(3, a.minimum());
        a.union(TestHeaps.heapOf(3));                    // 동률 → 유지
        HeapInvariantChecker.check(a);
        assertEquals(List.of(3, 3, 5, 9), TestHeaps.drain(a));
    }

    // ---------------------------------------------------------------- extractMin

    @Test
    void extractMin_singleRootWithoutChildren_becomesEmpty() {
        Main.FibonacciHeap h = TestHeaps.heapOf(4);
        assertEquals(4, h.extractMin());                 // z.child == null, z.right == z
        assertNull(h.minNode());
        HeapInvariantChecker.check(h);
    }

    @Test
    void extractMin_onlyRootWithChildren_promotesAndConsolidates() {
        Main.FibonacciHeap h = TestHeaps.singleTree(2, 10, 10);  // 루트 하나, 자식 있음
        assertEquals(1, TestHeaps.roots(h).size());
        assertEquals(20, h.extractMin());                // z.child != null, 승격 후 z.right != z
        HeapInvariantChecker.check(h);
        assertEquals(List.of(0, 1), TestHeaps.rootDegreesSorted(h));
    }

    @Test
    void extractMin_clearsMarksOfPromotedChildren() {
        // B2(루트 R=20): R 의 자식 a(차수0), p(차수1, 자식 g)
        Main.FibonacciHeap h = TestHeaps.singleTree(2, 10, 10);
        Main.FibonacciHeap.Node r = h.minNode();
        Main.FibonacciHeap.Node a = TestHeaps.childWithDegree(r, 0);
        Main.FibonacciHeap.Node p = TestHeaps.childWithDegree(r, 1);
        Main.FibonacciHeap.Node g = TestHeaps.childWithDegree(p, 0);
        h.decreaseKey(g, 15);                            // g 절단 → p marked
        assertTrue(p.mark);
        h.decreaseKey(a, 5);                             // a 절단 → R 의 자식은 marked 인 p 하나
        assertEquals(5, h.extractMin());
        assertEquals(15, h.extractMin());                // 이제 루트는 R 하나
        assertEquals(1, TestHeaps.roots(h).size());
        assertEquals(20, h.extractMin());                // marked 인 p 가 유일한 루트로 승격 (통합 시 link 되지 않음)
        assertSame(p, h.minNode());
        HeapInvariantChecker.check(h);                   // ROOT_MARK: 승격된 루트는 unmarked 여야 함
        assertFalse(p.mark);
    }

    // ---------------------------------------------------------------- consolidate / link

    @Test
    void consolidate_bothLinkDirections_andTies() {
        // 루트 리스트 순서에 따라 y.key < x.key 가 참/거짓이 되도록 두 순서를 모두 실행
        for (int[] keys : new int[][]{{1, 5, 3}, {1, 3, 5}, {1, 4, 4}}) {
            Main.FibonacciHeap h = TestHeaps.heapOf(keys);
            h.extractMin();
            HeapInvariantChecker.check(h);
            assertEquals(1, TestHeaps.roots(h).size());
            assertEquals(Math.min(keys[1], keys[2]), h.minimum());
            assertEquals(1, h.minNode().degree);
        }
    }

    @Test
    void link_firstChildAndAdditionalChild() {
        Main.FibonacciHeap h = TestHeaps.singleTree(2, 0, 1);    // B2: 루트에 자식 2개 (x.child null / non-null 둘 다)
        assertEquals(2, TestHeaps.children(h.minNode()).size());
        HeapInvariantChecker.check(h);
    }

    // ---------------------------------------------------------------- decreaseKey

    @Test
    void decreaseKeyByValue_absentKey() {
        Main.FibonacciHeap h = TestHeaps.heapOf(1);
        assertThrows(NoSuchElementException.class, () -> h.decreaseKey(2, 0));
    }

    @Test
    void decreaseKey_increaseRejected_equalIsNoOp() {
        Main.FibonacciHeap h = TestHeaps.heapOf(1, 2);
        Main.FibonacciHeap.Node n = h.insert(3);
        assertThrows(IllegalArgumentException.class, () -> h.decreaseKey(n, 4));
        h.decreaseKey(n, 3);
        assertEquals(3, n.key);
        HeapInvariantChecker.check(h);
    }

    @Test
    void decreaseKey_onRoot_becomesMinOrNot() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        h.insert(10);
        Main.FibonacciHeap.Node r = h.insert(20);
        h.decreaseKey(r, 15);                            // 루트(y == null), 새 min 아님
        assertEquals(10, h.minimum());
        h.decreaseKey(r, 5);                             // 루트, 새 min
        assertSame(r, h.minNode());
        HeapInvariantChecker.check(h);
    }

    @Test
    void decreaseKey_childWithoutOrderViolation_isNotCut() {
        Main.FibonacciHeap h = TestHeaps.singleTree(2, 10, 10);  // 루트 20
        Main.FibonacciHeap.Node c = TestHeaps.childWithDegree(h.minNode(), 0);
        Main.FibonacciHeap.Node parent = c.parent;
        h.decreaseKey(c, parent.key);                    // x.key == y.key → 순서 유지, cut 없음
        assertSame(parent, c.parent);
        assertFalse(parent.mark);
        HeapInvariantChecker.check(h);
    }

    // ---------------------------------------------------------------- cut

    @Test
    void cut_childPointerMovesToSiblingThenBecomesNull() {
        Main.FibonacciHeap h = TestHeaps.singleTree(2, 10, 10);  // 루트에 자식 2개
        Main.FibonacciHeap.Node root = h.minNode();
        Main.FibonacciHeap.Node pointed = root.child;
        Main.FibonacciHeap.Node sibling = pointed.right;
        h.decreaseKey(pointed, 1);                       // y.child == x, 형제 있음 → child = x.right
        assertSame(sibling, root.child);
        h.decreaseKey(sibling, 2);                       // y.child == x, 외동 → child = null
        assertNull(root.child);
        assertEquals(0, root.degree);
        HeapInvariantChecker.check(h);
    }

    @Test
    void cut_childNotPointedByParentKeepsChildPointer() {
        Main.FibonacciHeap h = TestHeaps.singleTree(2, 10, 10);
        Main.FibonacciHeap.Node root = h.minNode();
        Main.FibonacciHeap.Node pointed = root.child;
        h.decreaseKey(pointed.right, 1);                 // y.child != x
        assertSame(pointed, root.child);
        HeapInvariantChecker.check(h);
    }

    // ---------------------------------------------------------------- cascadingCut

    @Test
    void cascadingCut_parentIsRoot_unmarked_marked() {
        Main.FibonacciHeap h = TestHeaps.singleTree(3, 10, 10);
        Main.FibonacciHeap.Node root = h.minNode();
        Main.FibonacciHeap.Node p = TestHeaps.childWithDegree(root, 2);
        Main.FibonacciHeap.Node leafOfRoot = TestHeaps.childWithDegree(root, 0);

        h.decreaseKey(leafOfRoot, 1);                    // 부모가 루트: z == null → mark 하지 않음
        assertFalse(root.mark);
        h.decreaseKey(TestHeaps.childWithDegree(p, 0), 2);   // 부모 unmarked → mark
        assertTrue(p.mark);
        assertSame(root, p.parent);
        h.decreaseKey(TestHeaps.childWithDegree(p, 1), 3);   // 부모 marked → p 절단
        assertNull(p.parent);
        assertFalse(p.mark);
        HeapInvariantChecker.check(h);
    }
}
