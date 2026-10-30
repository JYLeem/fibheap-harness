import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 케이스 1(Dijkstra)·케이스 3(Prim) 전용 Fibonacci heap.
 *
 * 그래프 알고리즘에 맞춘 설계
 *   - 원소는 0 ~ capacity-1 의 정수 id(정점 번호)이고, 키는 long(거리 합 오버플로 방지).
 *   - 노드를 객체로 만들지 않고 배열(key/parent/child/left/right/degree/mark)에 저장한다.
 *     정점 번호가 곧 노드 핸들이므로 decreaseKey(v, k) 에서 노드를 찾는 비용이 없고(값 → 노드 HashMap 불필요),
 *     정점마다 객체를 할당하지 않아 GC 부담이 적다.
 *   - 정점 상태(NEW / IN_HEAP / REMOVED)를 함께 관리한다. Dijkstra·Prim 에서 "이미 확정된 정점" 판정이
 *     그대로 wasRemoved(v) 이고, 힙에 없는 정점의 decreaseKey 는 IllegalArgumentException 으로 거부한다.
 *
 * 연산과 비용 (분할상환)
 *   insert O(1), peekMin/minKey O(1), decreaseKey O(1), extractMin O(log n)
 *
 * 단일 스레드 전용.
 */
public final class IndexedFibonacciHeap {

    private static final byte NEW = 0;
    private static final byte IN_HEAP = 1;
    private static final byte REMOVED = 2;
    private static final int NIL = -1;
    /** int 범위의 원소 수에서 가능한 최대 차수(약 44)보다 넉넉한 크기 */
    private static final int MAX_DEGREE = 64;

    private final long[] key;
    private final int[] parent;
    private final int[] child;
    private final int[] left;
    private final int[] right;
    private final int[] degree;
    private final boolean[] mark;
    private final byte[] state;

    /** consolidate 용 작업 버퍼 (매번 할당하지 않음) */
    private final int[] rootBuffer;
    private final int[] byDegree = new int[MAX_DEGREE];

    private int min = NIL;
    private int size;

    public IndexedFibonacciHeap(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity 는 0 이상이어야 합니다: " + capacity);
        key = new long[capacity];
        parent = new int[capacity];
        child = new int[capacity];
        left = new int[capacity];
        right = new int[capacity];
        degree = new int[capacity];
        mark = new boolean[capacity];
        state = new byte[capacity];
        rootBuffer = new int[capacity];
        Arrays.fill(byDegree, NIL);
    }

    public int capacity() { return key.length; }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    /** v 가 현재 힙에 있는가 */
    public boolean contains(int v) {
        checkRange(v);
        return state[v] == IN_HEAP;
    }

    /** v 가 extractMin 으로 이미 꺼내졌는가 (Dijkstra 의 "확정", Prim 의 "트리에 포함") */
    public boolean wasRemoved(int v) {
        checkRange(v);
        return state[v] == REMOVED;
    }

    public long keyOf(int v) {
        requireInHeap(v);
        return key[v];
    }

    /** 최솟값을 가진 원소 id (빈 힙이면 NoSuchElementException) */
    public int peekMin() {
        if (min == NIL) throw new NoSuchElementException("빈 힙입니다");
        return min;
    }

    public long minKey() {
        return key[peekMin()];
    }

    /** v 를 키 k 로 삽입. 이미 힙에 있으면 IllegalArgumentException. 꺼낸 원소를 다시 넣는 것은 허용 */
    public void insert(int v, long k) {
        checkRange(v);
        if (state[v] == IN_HEAP) throw new IllegalArgumentException("원소 " + v + " 는 이미 힙에 있습니다");
        key[v] = k;
        child[v] = NIL;
        degree[v] = 0;
        left[v] = v;
        right[v] = v;
        state[v] = IN_HEAP;
        size++;
        addRoot(v);
    }

    /** 최솟값 원소를 꺼내 id 를 반환 (빈 힙이면 NoSuchElementException) */
    public int extractMin() {
        int z = min;
        if (z == NIL) throw new NoSuchElementException("빈 힙입니다");

        int c = child[z];
        if (c != NIL) {
            // 자식들의 parent/mark 를 지우고, 자식 원형 리스트 전체를 루트 리스트에 한 번에 이어 붙인다
            int cur = c;
            do {
                parent[cur] = NIL;
                mark[cur] = false;
                cur = right[cur];
            } while (cur != c);
            int zRight = right[z];
            int cLast = left[c];
            right[z] = c;
            left[c] = z;
            right[cLast] = zRight;
            left[zRight] = cLast;
            child[z] = NIL;
        }

        if (right[z] == z) {
            min = NIL;
        } else {
            int next = right[z];
            unlink(z);
            min = next;
            consolidate();
        }
        degree[z] = 0;
        state[z] = REMOVED;
        size--;
        return z;
    }

    /** v 의 키를 k(≤ 현재 키)로 줄인다. v 가 힙에 없거나 k 가 더 크면 IllegalArgumentException */
    public void decreaseKey(int v, long k) {
        requireInHeap(v);
        if (k > key[v]) {
            throw new IllegalArgumentException("새 키 " + k + " 가 현재 키 " + key[v] + " 보다 큽니다 (원소 " + v + ")");
        }
        if (k == key[v]) return;
        key[v] = k;
        int p = parent[v];
        if (p != NIL && key[v] < key[p]) {
            cut(v, p);
            cascadingCut(p);
        }
        if (key[v] < key[min]) min = v;
    }

    // ------------------------------------------------------------------ 내부 연산

    private void consolidate() {
        int roots = 0;
        int w = min;
        do {
            rootBuffer[roots++] = w;
            w = right[w];
        } while (w != min);

        int maxDegree = -1;
        for (int i = 0; i < roots; i++) {
            int x = rootBuffer[i];
            int d = degree[x];
            while (byDegree[d] != NIL) {
                int y = byDegree[d];
                if (key[y] < key[x]) {
                    int t = x;
                    x = y;
                    y = t;
                }
                link(y, x);
                byDegree[d] = NIL;
                d++;
            }
            byDegree[d] = x;
            if (d > maxDegree) maxDegree = d;
        }

        min = NIL;
        for (int d = 0; d <= maxDegree; d++) {
            int r = byDegree[d];
            if (r != NIL) {
                byDegree[d] = NIL;
                if (min == NIL || key[r] < key[min]) min = r;
            }
        }
    }

    /** 루트 y 를 루트 x 의 자식으로 */
    private void link(int y, int x) {
        unlink(y);
        parent[y] = x;
        if (child[x] == NIL) {
            child[x] = y;
        } else {
            insertAfter(child[x], y);
        }
        degree[x]++;
        mark[y] = false;
    }

    private void cut(int x, int p) {
        if (child[p] == x) child[p] = (right[x] == x) ? NIL : right[x];
        unlink(x);
        degree[p]--;
        addRoot(x);
    }

    private void cascadingCut(int y) {
        while (parent[y] != NIL) {
            if (!mark[y]) {
                mark[y] = true;
                return;
            }
            int z = parent[y];
            cut(y, z);
            y = z;
        }
    }

    /** 분리된(left == right == 자신) 노드 x 를 루트 리스트에 추가 */
    private void addRoot(int x) {
        parent[x] = NIL;
        mark[x] = false;
        if (min == NIL) {
            left[x] = x;
            right[x] = x;
            min = x;
        } else {
            insertAfter(min, x);
            if (key[x] < key[min]) min = x;
        }
    }

    private void insertAfter(int a, int x) {
        left[x] = a;
        right[x] = right[a];
        left[right[a]] = x;
        right[a] = x;
    }

    private void unlink(int x) {
        right[left[x]] = right[x];
        left[right[x]] = left[x];
        left[x] = x;
        right[x] = x;
    }

    private void checkRange(int v) {
        if (v < 0 || v >= key.length) {
            throw new IndexOutOfBoundsException("원소 id " + v + " 가 범위(0~" + (key.length - 1) + ")를 벗어났습니다");
        }
    }

    private void requireInHeap(int v) {
        checkRange(v);
        if (state[v] != IN_HEAP) {
            throw new IllegalArgumentException("원소 " + v + " 는 힙에 없습니다 (" + (state[v] == NEW ? "삽입된 적 없음" : "이미 꺼냄") + ")");
        }
    }

    // ------------------------------------------------------------------ 불변식 검사 (테스트용)

    /** 테스트용: v 의 부모 (루트면 -1) */
    int parentOf(int v) {
        requireInHeap(v);
        return parent[v];
    }

    /** 구조 불변식을 검사하고, 위반 시 IllegalStateException */
    void validate() {
        int inHeap = 0;
        for (byte s : state) if (s == IN_HEAP) inHeap++;
        check(inHeap == size, "IN_HEAP 상태 수 " + inHeap + " ≠ size " + size);
        if (min == NIL) {
            check(size == 0, "min 이 없는데 size = " + size);
            return;
        }
        check(state[min] == IN_HEAP, "min 이 힙에 없는 원소를 가리킴");
        int[] count = {0};
        int maxDeg = (int) Math.floor(Math.log(size) / Math.log((1 + Math.sqrt(5)) / 2) + 1e-9);
        for (int r : ring(min)) {
            check(parent[r] == NIL, "루트 " + r + " 의 parent 가 NIL 이 아님");
            check(!mark[r], "루트 " + r + " 가 marked");
            check(key[min] <= key[r], "min 이 루트 최솟값이 아님");
            subtree(r, count, maxDeg);
        }
        check(count[0] == size, "도달 가능한 노드 " + count[0] + " ≠ size " + size);
    }

    private void subtree(int x, int[] count, int maxDeg) {
        check(state[x] == IN_HEAP, "힙에 없는 원소 " + x + " 가 트리에 있음");
        check(++count[0] <= size, "도달 가능한 노드가 size 보다 많음 (사이클 의심)");
        check(degree[x] <= maxDeg, "원소 " + x + " 의 차수 " + degree[x] + " 가 상한 " + maxDeg + " 초과");
        List<Integer> kids = child[x] == NIL ? List.of() : ring(child[x]);
        check(kids.size() == degree[x], "원소 " + x + " 의 degree " + degree[x] + " ≠ 자식 수 " + kids.size());
        for (int c : kids) {
            check(parent[c] == x, "자식 " + c + " 의 parent 가 " + x + " 가 아님");
            check(key[c] >= key[x], "힙 순서 위반: " + x + "(" + key[x] + ") > " + c + "(" + key[c] + ")");
            subtree(c, count, maxDeg);
        }
    }

    private List<Integer> ring(int start) {
        List<Integer> list = new ArrayList<>();
        int x = start;
        do {
            check(left[right[x]] == x && right[left[x]] == x, "원소 " + x + " 의 left/right 불일치");
            list.add(x);
            check(list.size() <= size, "원형 리스트가 닫히지 않음");
            x = right[x];
        } while (x != start);
        return list;
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new IllegalStateException(message);
    }
}
