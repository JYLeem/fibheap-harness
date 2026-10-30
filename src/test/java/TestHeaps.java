import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** 테스트 공통 도우미: 힙 구성, 구조 조회, 실행 규모 설정 */
final class TestHeaps {

    private TestHeaps() {}

    /**
     * 실행 규모: -Dharness.scale=quick | default | long
     * quick 는 변이 테스트(변이마다 전체 스위트 실행)용, long 은 야간 퍼즈용.
     */
    static int scale() {
        String s = System.getProperty("harness.scale", "default");
        switch (s) {
            case "quick": return 1;
            case "long": return 20;
            default: return 4;
        }
    }

    /** -Dharness.seed=N 이 있으면 그 시드 하나만, 없으면 base 부터 count 개 */
    static long[] seeds(long base, int count) {
        String fixed = System.getProperty("harness.seed");
        if (fixed != null && !fixed.isBlank()) return new long[]{Long.parseLong(fixed.trim())};
        long[] seeds = new long[count];
        for (int i = 0; i < count; i++) seeds[i] = base + i;
        return seeds;
    }

    static Main.FibonacciHeap heapOf(int... keys) {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        for (int k : keys) h.insert(k);
        return h;
    }

    /** 모두 꺼내며 매 단계 불변식 검사 */
    static List<Integer> drain(Main.FibonacciHeap h) {
        List<Integer> out = new ArrayList<>();
        while (!h.isEmpty()) {
            out.add(h.extractMin());
            HeapInvariantChecker.check(h);
        }
        return out;
    }

    /**
     * 차수 k 의 이항 트리 하나만 루트에 있는 힙을 만든다.
     * base, base+step, ... 2^k + 1 개를 넣고 최솟값을 하나 빼면, 차수 0 루트 2^k 개가 통합되어 B_k 하나가 된다.
     */
    static Main.FibonacciHeap singleTree(int k, int base, int step) {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        for (int i = 0; i <= (1 << k); i++) h.insert(base + i * step);
        h.extractMin();
        HeapInvariantChecker.check(h);
        if (roots(h).size() != 1 || h.minNode().degree != k) {
            throw new AssertionError("B" + k + " 구성 실패: 루트 " + roots(h).size() + "개, 차수 " + h.minNode().degree);
        }
        return h;
    }

    static List<Main.FibonacciHeap.Node> roots(Main.FibonacciHeap h) {
        return Main.FibonacciHeap.siblings(h.minNode());
    }

    static List<Main.FibonacciHeap.Node> children(Main.FibonacciHeap.Node n) {
        return Main.FibonacciHeap.siblings(n.child);
    }

    static Main.FibonacciHeap.Node childWithDegree(Main.FibonacciHeap.Node parent, int degree) {
        for (Main.FibonacciHeap.Node c : children(parent)) if (c.degree == degree) return c;
        throw new AssertionError(InvariantText.of(parent) + " 에 차수 " + degree + " 인 자식이 없음");
    }

    static List<Integer> rootDegreesSorted(Main.FibonacciHeap h) {
        List<Integer> d = new ArrayList<>();
        for (Main.FibonacciHeap.Node r : roots(h)) d.add(r.degree);
        d.sort(null);
        return d;
    }

    static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class InvariantText {
        static String of(Main.FibonacciHeap.Node n) { return HeapInvariantChecker.describe(n); }
    }
}
