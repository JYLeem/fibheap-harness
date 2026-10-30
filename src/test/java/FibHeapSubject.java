import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Main.FibonacciHeap 을 {@link HeapSubject} 로 감싸는 어댑터. 구현 코드는 수정하지 않는다. */
final class FibHeapSubject implements HeapSubject {

    final Main.FibonacciHeap heap;

    FibHeapSubject() {
        this(new Main.FibonacciHeap());
    }

    FibHeapSubject(Main.FibonacciHeap heap) {
        this.heap = heap;
    }

    @Override public Object insert(int key) { return heap.insert(key); }

    @Override public Integer findMin() { return heap.minimum(); }

    @Override public Integer extractMin() { return heap.extractMin(); }

    @Override public Object minHandle() { return heap.minNode(); }

    @Override public void decreaseKey(Object handle, int newKey) {
        heap.decreaseKey((Main.FibonacciHeap.Node) handle, newKey);
    }

    @Override public void decreaseKeyByValue(int oldKey, int newKey) { heap.decreaseKey(oldKey, newKey); }

    @Override public void meld(HeapSubject other) { heap.union(((FibHeapSubject) other).heap); }

    @Override public int size() { return heap.size(); }

    @Override public int keyOf(Object handle) { return ((Main.FibonacciHeap.Node) handle).key; }

    /**
     * 1차: 테스트 코드의 독립 검사기. 2차: 구현이 제공하는 validate().
     * 독립 검사기가 먼저 실행되므로, 링크가 끊긴 구조에서 구현 쪽 순회가 무한 루프에 빠지기 전에 실패한다.
     */
    @Override public void checkInvariants() {
        HeapInvariantChecker.check(heap);
        heap.validate();
    }

    @Override public Set<Object> reachableHandles() {
        Set<Object> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(HeapInvariantChecker.check(heap));
        return result;
    }

    @Override public String toString() { return "FibHeapSubject(size=" + heap.size() + ")"; }
}
