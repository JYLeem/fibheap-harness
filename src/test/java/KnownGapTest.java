import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * [기법 4] 사전조건 위반: 잘못된 노드 참조.
 *
 * 현재 구현의 decreaseKey(Node, int) 는 "이 힙에 살아 있는 노드" 라는 사전조건을 검사하지 않는다.
 * 이 클래스는 권장 계약(= IllegalArgumentException 으로 거부하고 힙을 손상시키지 않음)을 테스트로 고정해 둔 것이며,
 * 처음 구현에서는 3개 모두 실패했고, 소속 힙 토큰(union-find)을 추가해 수정했다.
 * 수정 후에는 기본 실행에 포함되어 회귀 테스트 역할을 한다.
 *   실행: mvn test -Dgroups=known-gap -Dharness.excludedGroups=perf
 *
 * 텍스트 명령(test.txt)은 값 기반 decrease_key 만 쓰므로 이 문제가 드러나지 않는다.
 * Node 핸들을 외부에 노출하는 사용(예: Dijkstra 의 정점 → Node 배열)에서는 실제 위험이 된다.
 */
@Tag("known-gap")
@DisplayName("4b. Known gaps: invalid node references")
class KnownGapTest {

    @Test
    void decreaseKeyOfExtractedNodeShouldBeRejected() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node stale = h.insert(1);
        h.insert(5);
        h.insert(7);
        h.extractMin();                                   // stale 노드는 이제 힙에 없다
        assertThrows(IllegalArgumentException.class, () -> h.decreaseKey(stale, 0));
        assertDoesNotThrow(() -> HeapInvariantChecker.check(h), "거부 후에도 힙은 정상이어야 함");
    }

    @Test
    void decreaseKeyOfNodeFromAnotherHeapShouldBeRejected() {
        Main.FibonacciHeap a = TestHeaps.heapOf(10, 20);
        Main.FibonacciHeap b = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node foreign = b.insert(30);
        assertThrows(IllegalArgumentException.class, () -> a.decreaseKey(foreign, 1));
        assertDoesNotThrow(() -> HeapInvariantChecker.check(a));
        assertDoesNotThrow(() -> HeapInvariantChecker.check(b));
    }

    @Test
    void decreaseKeyThroughHeapThatWasMeldedAwayShouldBeRejected() {
        Main.FibonacciHeap a = TestHeaps.heapOf(10);
        Main.FibonacciHeap b = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node moved = b.insert(30);
        a.union(b);                                        // moved 는 이제 a 소속, b 는 빈 힙
        assertThrows(IllegalArgumentException.class, () -> b.decreaseKey(moved, 1));
        assertDoesNotThrow(() -> HeapInvariantChecker.check(a));
        assertDoesNotThrow(() -> HeapInvariantChecker.check(b));
    }
}
