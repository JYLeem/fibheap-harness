import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [기법 1] Fibonacci Heap 구조 불변식 검사기.
 *
 * 구현의 validate() 를 재사용하지 않고 테스트 쪽에서 독립적으로 다시 검사한다
 * (구현과 검사기가 같은 버그를 공유하지 않도록).
 * 손상된 구조에서도 무한 루프에 빠지지 않도록 모든 순회에 상한(size)과 방문 집합을 둔다.
 *
 * 검사 규칙 (위반 시 {@link Violation} 에 규칙 코드가 붙는다)
 *   EMPTY_STATE      min == null 과 size == 0 이 서로 일치
 *   LINK_CONSISTENCY x.right.left == x, x.left.right == x, null 링크 없음
 *   LIST_NOT_CLOSED  원형 리스트가 시작 노드로 돌아옴 (중간 재방문·무한 연결 탐지)
 *   DUPLICATE_VISIT  한 노드가 두 트리/두 부모에서 도달되지 않음
 *   ROOT_PARENT      루트의 parent == null
 *   ROOT_MARK        루트는 mark == false
 *   MIN_POINTER      min 이 루트 중 최솟값 (힙 순서와 결합하면 전체 최솟값)
 *   HEAP_ORDER       부모 키 <= 자식 키
 *   PARENT_POINTER   자식의 parent 가 실제 부모
 *   DEGREE           degree == 실제 자식 수
 *   SIZE             size == 도달 가능한 노드 수
 *   DEGREE_BOUND     degree <= floor(log_phi(size))  (Fibonacci heap 의 차수 상한)
 *   INDEX            decrease_key 용 키 인덱스가 도달 가능한 노드와 정확히 일치
 */
final class HeapInvariantChecker {

    enum Rule {
        EMPTY_STATE, LINK_CONSISTENCY, LIST_NOT_CLOSED, DUPLICATE_VISIT, ROOT_PARENT, ROOT_MARK,
        MIN_POINTER, HEAP_ORDER, PARENT_POINTER, DEGREE, SIZE, DEGREE_BOUND, INDEX
    }

    static final class Violation extends AssertionError {
        final Rule rule;

        Violation(Rule rule, String detail) {
            super("[" + rule + "] " + detail);
            this.rule = rule;
        }
    }

    private static final double LOG_PHI = Math.log((1 + Math.sqrt(5)) / 2);
    private static final Field INDEX_FIELD;

    static {
        try {
            INDEX_FIELD = Main.FibonacciHeap.class.getDeclaredField("index");
            INDEX_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private HeapInvariantChecker() {}

    /** 모든 불변식을 검사하고, 도달 가능한 노드 집합(identity)을 반환한다. */
    static Set<Main.FibonacciHeap.Node> check(Main.FibonacciHeap heap) {
        Main.FibonacciHeap.Node min = heap.minNode();
        int size = heap.size();
        Map<Integer, List<Main.FibonacciHeap.Node>> index = index(heap);
        Set<Main.FibonacciHeap.Node> reachable = identitySet();

        if (min == null) {
            if (!(size == 0)) throw new Violation(Rule.EMPTY_STATE, "min == null 인데 size = " + size);
            if (!(index.isEmpty())) throw new Violation(Rule.INDEX, "빈 힙인데 인덱스에 키 " + index.keySet() + " 가 남아 있음");
            return reachable;
        }
        if (!(size > 0)) throw new Violation(Rule.EMPTY_STATE, "min = " + describe(min) + " 인데 size = " + size);

        List<Main.FibonacciHeap.Node> roots = ring(min, size, "루트 리스트");
        int minRootKey = Integer.MAX_VALUE;
        for (Main.FibonacciHeap.Node r : roots) {
            if (!(r.parent == null)) throw new Violation(Rule.ROOT_PARENT, "루트 " + describe(r) + " 의 parent 가 " + describe(r.parent));
            if (!(!r.mark)) throw new Violation(Rule.ROOT_MARK, "루트 " + describe(r) + " 가 marked 상태");
            minRootKey = Math.min(minRootKey, r.key);
        }
        if (!(min.key == minRootKey)) throw new Violation(Rule.MIN_POINTER, "min 키 " + min.key + " 가 루트 최솟값 " + minRootKey + " 와 다름");

        int maxDegree = (int) Math.floor(Math.log(size) / LOG_PHI + 1e-9);
        Deque<Main.FibonacciHeap.Node> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            Main.FibonacciHeap.Node x = stack.pop();
            if (!(reachable.add(x))) throw new Violation(Rule.DUPLICATE_VISIT, describe(x) + " 를 두 번 방문함 (트리 사이 공유 또는 사이클)");
            if (!(reachable.size() <= size)) throw new Violation(Rule.SIZE, "도달 가능한 노드가 size(" + size + ")보다 많음");
            if (!(x.degree <= maxDegree)) throw new Violation(Rule.DEGREE_BOUND, describe(x) + " 의 degree 가 상한 " + maxDegree + " (size=" + size + ") 초과");

            List<Main.FibonacciHeap.Node> children =
                    x.child == null ? List.of() : ring(x.child, size, describe(x) + " 의 자식 리스트");
            if (!(children.size() == x.degree)) throw new Violation(Rule.DEGREE, describe(x) + " 의 degree " + x.degree + " ≠ 실제 자식 수 " + children.size());
            for (Main.FibonacciHeap.Node c : children) {
                if (!(c.parent == x)) throw new Violation(Rule.PARENT_POINTER, "자식 " + describe(c) + " 의 parent 가 " + describe(c.parent) + " (기대: " + describe(x) + ")");
                if (!(c.key >= x.key)) throw new Violation(Rule.HEAP_ORDER, "부모 " + describe(x) + " > 자식 " + describe(c));
                stack.push(c);
            }
        }
        if (!(reachable.size() == size)) throw new Violation(Rule.SIZE, "size = " + size + " 인데 도달 가능한 노드는 " + reachable.size() + "개");
        checkIndex(index, reachable, size);
        return reachable;
    }

    /** 도달 가능한 노드의 키 목록 (검사 포함) */
    static List<Integer> keys(Main.FibonacciHeap heap) {
        List<Integer> keys = new ArrayList<>();
        for (Main.FibonacciHeap.Node n : check(heap)) keys.add(n.key);
        Collections.sort(keys);
        return keys;
    }

    // ------------------------------------------------------------------ 내부

    private static List<Main.FibonacciHeap.Node> ring(Main.FibonacciHeap.Node start, int limit, String where) {
        List<Main.FibonacciHeap.Node> list = new ArrayList<>();
        Set<Main.FibonacciHeap.Node> seen = identitySet();
        Main.FibonacciHeap.Node x = start;
        do {
            if (!(x != null)) throw new Violation(Rule.LINK_CONSISTENCY, where + ": null 링크");
            if (!(seen.add(x))) throw new Violation(Rule.LIST_NOT_CLOSED, where + ": 시작 노드로 돌아오기 전에 " + describe(x) + " 를 다시 만남");
            if (!(x.left != null && x.right != null)) throw new Violation(Rule.LINK_CONSISTENCY, where + ": " + describe(x) + " 의 left/right 가 null");
            if (!(x.right.left == x)) throw new Violation(Rule.LINK_CONSISTENCY, where + ": " + describe(x) + ".right.left 가 자신이 아님");
            if (!(x.left.right == x)) throw new Violation(Rule.LINK_CONSISTENCY, where + ": " + describe(x) + ".left.right 가 자신이 아님");
            list.add(x);
            if (!(list.size() <= limit)) throw new Violation(Rule.LIST_NOT_CLOSED, where + ": size(" + limit + ")개를 넘는 노드가 이어짐");
            x = x.right;
        } while (x != start);
        return list;
    }

    private static void checkIndex(Map<Integer, List<Main.FibonacciHeap.Node>> index,
                                   Set<Main.FibonacciHeap.Node> reachable, int size) {
        Set<Main.FibonacciHeap.Node> seen = identitySet();
        int indexed = 0;
        for (Map.Entry<Integer, List<Main.FibonacciHeap.Node>> e : index.entrySet()) {
            if (!(!e.getValue().isEmpty())) throw new Violation(Rule.INDEX, "키 " + e.getKey() + " 의 빈 리스트가 남아 있음");
            for (Main.FibonacciHeap.Node n : e.getValue()) {
                if (!(n.key == e.getKey())) throw new Violation(Rule.INDEX, "인덱스 키 " + e.getKey() + " 아래에 키 " + n.key + " 노드가 있음");
                if (!(reachable.contains(n))) throw new Violation(Rule.INDEX, "인덱스의 " + describe(n) + " 가 힙에서 도달 불가능 (삭제·병합된 노드가 남음)");
                if (!(seen.add(n))) throw new Violation(Rule.INDEX, describe(n) + " 가 인덱스에 중복 등록");
                indexed++;
            }
        }
        if (!(indexed == size)) throw new Violation(Rule.INDEX, "인덱스 노드 수 " + indexed + " ≠ size " + size);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<Main.FibonacciHeap.Node>> index(Main.FibonacciHeap heap) {
        try {
            return (Map<Integer, List<Main.FibonacciHeap.Node>>) INDEX_FIELD.get(heap);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<Main.FibonacciHeap.Node> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    static String describe(Main.FibonacciHeap.Node n) {
        if (n == null) return "null";
        return "Node(key=" + n.key + ", deg=" + n.degree + (n.mark ? ", marked" : "") + ")";
    }

}
