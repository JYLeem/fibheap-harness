import java.util.ArrayList;
import java.util.List;

/**
 * 케이스 2(멀티 서버 작업 스케줄러) 전용 Fibonacci heap.
 *
 * 스케줄러에 맞춘 설계
 *   - 원소는 임의 객체 T, 키는 long(마감 시각 등). insert 가 돌려주는 {@link Handle} 로 원소를 식별한다.
 *   - 같은 키끼리는 삽입 순서(sequence)로 비교한다 → 같은 마감이면 먼저 제출된 작업이 먼저 실행(FIFO, 결정적 순서).
 *   - meld O(1): 서버 장애 시 대기열 전체를 다른 서버로 넘긴다. 노드를 하나씩 옮기지 않는다.
 *   - delete O(log n): 작업 취소. updateKey: 마감을 늦추는 경우(키 증가)는 delete + 재삽입.
 *   - 핸들 검증: 이미 꺼낸/취소한 작업, 다른 서버 대기열의 작업을 잘못 넘기면 IllegalArgumentException.
 *     meld 후에도 O(1)을 유지하려고 노드마다 소속 힙을 직접 저장하지 않고, "소유자 토큰"을
 *     union-find(경로 압축 + 랭크 합치기)로 묶는다. 소속 확인은 거의 O(1)(역 아커만 함수)이다.
 *
 * 연산과 비용 (분할상환)
 *   insert O(1), peek O(1), meld O(1), decreaseKey O(1)+α, extractMin / delete O(log n)
 *
 * 단일 스레드 전용 (sequence 카운터가 동기화되지 않음).
 */
public final class MeldableFibonacciHeap<T> {

    private static final int MAX_DEGREE = 64;
    private static long sequenceCounter;

    /** 힙 원소이자 노드. 외부에는 key / value / sequence 만 공개한다 */
    public static final class Handle<T> {
        private long key;
        private final T value;
        private long sequence;
        private Handle<T> parent;
        private Handle<T> child;
        private Handle<T> left = this;
        private Handle<T> right = this;
        private int degree;
        private boolean mark;
        /** 소속 힙의 소유자 토큰. 힙에서 빠지면 null */
        private Owner owner;

        private Handle(T value, long key) {
            this.value = value;
            this.key = key;
        }

        public long key() { return key; }

        public T value() { return value; }

        /** 같은 키 사이의 순서 (작을수록 먼저) */
        public long sequence() { return sequence; }

        /** 현재 어떤 힙에 들어 있는가 */
        public boolean isQueued() { return owner != null; }

        @Override public String toString() { return value + "@" + key; }
    }

    /** union-find 노드. 루트의 heap 이 실제 소속 힙 */
    private static final class Owner {
        Owner parent = this;
        int rank;
        MeldableFibonacciHeap<?> heap;

        Owner(MeldableFibonacciHeap<?> heap) { this.heap = heap; }
    }

    private final String name;
    private Handle<T> min;
    private int size;
    private Owner owner;

    public MeldableFibonacciHeap(String name) {
        this.name = name;
        this.owner = new Owner(this);
    }

    public String name() { return name; }

    public int size() { return size; }

    public boolean isEmpty() { return min == null; }

    /** 최솟값 원소 (빈 힙이면 null) */
    public Handle<T> peek() { return min; }

    public Handle<T> insert(T value, long key) {
        Handle<T> h = new Handle<>(value, key);
        insertNode(h, key);
        return h;
    }

    /** 최솟값 원소를 꺼낸다 (빈 힙이면 null). 꺼낸 핸들은 더 이상 이 힙에서 쓸 수 없다 */
    public Handle<T> extractMin() {
        Handle<T> z = min;
        if (z == null) return null;

        if (z.child != null) {
            Handle<T> c = z.child;
            Handle<T> cur = c;
            do {
                cur.parent = null;
                cur.mark = false;
                cur = cur.right;
            } while (cur != c);
            Handle<T> zRight = z.right;
            Handle<T> cLast = c.left;
            z.right = c;
            c.left = z;
            cLast.right = zRight;
            zRight.left = cLast;
            z.child = null;
        }

        if (z.right == z) {
            min = null;
        } else {
            Handle<T> next = z.right;
            unlink(z);
            min = next;
            consolidate();
        }
        z.degree = 0;
        z.owner = null;
        size--;
        return z;
    }

    /** 키를 k(≤ 현재 키)로 줄인다. 순서(sequence)는 유지한다 */
    public void decreaseKey(Handle<T> h, long k) {
        checkHandle(h);
        if (k > h.key) {
            throw new IllegalArgumentException("새 키 " + k + " 가 현재 키 " + h.key + " 보다 큽니다: " + h.value);
        }
        if (k == h.key) return;
        h.key = k;
        Handle<T> p = h.parent;
        if (p != null && less(h, p)) {
            cut(h, p);
            cascadingCut(p);
        }
        if (less(h, min)) min = h;
    }

    /** 원소 삭제: 루트로 잘라 올린 뒤 min 으로 지정하고 꺼낸다 (키를 -∞ 로 바꾸지 않음) */
    public void delete(Handle<T> h) {
        checkHandle(h);
        Handle<T> p = h.parent;
        if (p != null) {
            cut(h, p);
            cascadingCut(p);
        }
        min = h;
        extractMin();
    }

    /** 키 변경. 줄이면 decreaseKey, 늘리면 delete 후 같은 핸들을 새 순서로 다시 넣는다 */
    public void updateKey(Handle<T> h, long k) {
        checkHandle(h);
        if (k <= h.key) {
            decreaseKey(h, k);
            return;
        }
        delete(h);
        insertNode(h, k);
    }

    /** other 의 모든 원소를 이 힙으로 옮긴다. other 는 빈 힙이 되고 계속 쓸 수 있다 */
    public void meld(MeldableFibonacciHeap<T> other) {
        if (other == this) throw new IllegalArgumentException("자기 자신과는 병합할 수 없습니다: " + name);
        if (other.min == null) return;
        if (min == null) {
            min = other.min;
        } else {
            Handle<T> a = min;
            Handle<T> b = other.min;
            Handle<T> aRight = a.right;
            Handle<T> bLeft = b.left;
            a.right = b;
            b.left = a;
            bLeft.right = aRight;
            aRight.left = bLeft;
            if (less(b, a)) min = b;
        }
        size += other.size;

        // 소속 정보 합치기: 노드를 건드리지 않고 소유자 토큰만 연결
        Owner ra = find(owner);
        Owner rb = find(other.owner);
        Owner root;
        if (ra.rank < rb.rank) {
            ra.parent = rb;
            root = rb;
        } else {
            rb.parent = ra;
            if (ra.rank == rb.rank) ra.rank++;
            root = ra;
        }
        root.heap = this;
        owner = root;

        other.min = null;
        other.size = 0;
        other.owner = new Owner(other);
    }

    /** h 가 이 힙에 들어 있는가 */
    public boolean contains(Handle<T> h) {
        return h != null && h.owner != null && find(h.owner).heap == this;
    }

    /** h 가 들어 있는 힙 (빠져 있으면 null) */
    @SuppressWarnings("unchecked")
    public static <T> MeldableFibonacciHeap<T> heapOf(Handle<T> h) {
        return h.owner == null ? null : (MeldableFibonacciHeap<T>) find(h.owner).heap;
    }

    @Override public String toString() { return "MeldableFibonacciHeap(" + name + ", size=" + size + ")"; }

    // ------------------------------------------------------------------ 내부 연산

    private void insertNode(Handle<T> h, long key) {
        h.key = key;
        h.sequence = sequenceCounter++;
        h.child = null;
        h.degree = 0;
        h.left = h;
        h.right = h;
        h.owner = owner;
        size++;
        addRoot(h);
    }

    private static <T> boolean less(Handle<T> a, Handle<T> b) {
        return a.key < b.key || (a.key == b.key && a.sequence < b.sequence);
    }

    @SuppressWarnings("unchecked")
    private void consolidate() {
        List<Handle<T>> roots = new ArrayList<>();
        Handle<T> w = min;
        do {
            roots.add(w);
            w = w.right;
        } while (w != min);

        Handle<T>[] byDegree = (Handle<T>[]) new Handle[MAX_DEGREE];
        int maxDegree = -1;
        for (Handle<T> root : roots) {
            Handle<T> x = root;
            int d = x.degree;
            while (byDegree[d] != null) {
                Handle<T> y = byDegree[d];
                if (less(y, x)) {
                    Handle<T> t = x;
                    x = y;
                    y = t;
                }
                link(y, x);
                byDegree[d] = null;
                d++;
            }
            byDegree[d] = x;
            if (d > maxDegree) maxDegree = d;
        }
        min = null;
        for (int d = 0; d <= maxDegree; d++) {
            Handle<T> r = byDegree[d];
            if (r != null && (min == null || less(r, min))) min = r;
        }
    }

    private void link(Handle<T> y, Handle<T> x) {
        unlink(y);
        y.parent = x;
        if (x.child == null) {
            x.child = y;
        } else {
            insertAfter(x.child, y);
        }
        x.degree++;
        y.mark = false;
    }

    private void cut(Handle<T> x, Handle<T> p) {
        if (p.child == x) p.child = (x.right == x) ? null : x.right;
        unlink(x);
        p.degree--;
        addRoot(x);
    }

    private void cascadingCut(Handle<T> y) {
        while (y.parent != null) {
            if (!y.mark) {
                y.mark = true;
                return;
            }
            Handle<T> z = y.parent;
            cut(y, z);
            y = z;
        }
    }

    private void addRoot(Handle<T> x) {
        x.parent = null;
        x.mark = false;
        if (min == null) {
            x.left = x;
            x.right = x;
            min = x;
        } else {
            insertAfter(min, x);
            if (less(x, min)) min = x;
        }
    }

    private static <T> void insertAfter(Handle<T> a, Handle<T> x) {
        x.left = a;
        x.right = a.right;
        a.right.left = x;
        a.right = x;
    }

    private static <T> void unlink(Handle<T> x) {
        x.left.right = x.right;
        x.right.left = x.left;
        x.left = x;
        x.right = x;
    }

    private void checkHandle(Handle<T> h) {
        if (h == null) throw new NullPointerException("handle 이 null 입니다");
        if (h.owner == null) throw new IllegalArgumentException("이미 힙에서 빠진 원소입니다: " + h.value);
        MeldableFibonacciHeap<?> actual = find(h.owner).heap;
        if (actual != this) {
            throw new IllegalArgumentException(h.value + " 는 " + name + " 이 아니라 " + actual.name + " 에 있습니다");
        }
    }

    private static Owner find(Owner o) {
        Owner root = o;
        while (root.parent != root) root = root.parent;
        while (o != root) {
            Owner next = o.parent;
            o.parent = root;
            o = next;
        }
        return root;
    }

    // ------------------------------------------------------------------ 불변식 검사 (테스트용)

    void validate() {
        if (min == null) {
            check(size == 0, "빈 힙인데 size = " + size);
            return;
        }
        check(find(owner) == owner && owner.heap == this, "힙의 소유자 토큰이 union-find 루트가 아님");
        int[] count = {0};
        int maxDeg = (int) Math.floor(Math.log(size) / Math.log((1 + Math.sqrt(5)) / 2) + 1e-9);
        for (Handle<T> r : ring(min)) {
            check(r.parent == null, "루트 " + r + " 의 parent 가 null 이 아님");
            check(!r.mark, "루트 " + r + " 가 marked");
            check(!less(r, min), "min 이 루트 최솟값이 아님");
            subtree(r, count, maxDeg);
        }
        check(count[0] == size, "도달 가능한 노드 " + count[0] + " ≠ size " + size);
    }

    private void subtree(Handle<T> x, int[] count, int maxDeg) {
        check(++count[0] <= size, "도달 가능한 노드가 size 보다 많음");
        check(x.owner != null && find(x.owner).heap == this, x + " 의 소속이 이 힙이 아님");
        check(x.degree <= maxDeg, x + " 의 차수 " + x.degree + " 가 상한 " + maxDeg + " 초과");
        List<Handle<T>> kids = x.child == null ? List.of() : ring(x.child);
        check(kids.size() == x.degree, x + " 의 degree " + x.degree + " ≠ 자식 수 " + kids.size());
        for (Handle<T> c : kids) {
            check(c.parent == x, c + " 의 parent 가 " + x + " 가 아님");
            check(!less(c, x), "힙 순서 위반: " + x + " > " + c);
            subtree(c, count, maxDeg);
        }
    }

    private List<Handle<T>> ring(Handle<T> start) {
        List<Handle<T>> list = new ArrayList<>();
        Handle<T> x = start;
        do {
            check(x.right.left == x && x.left.right == x, x + " 의 left/right 불일치");
            list.add(x);
            check(list.size() <= size, "원형 리스트가 닫히지 않음");
            x = x.right;
        } while (x != start);
        return list;
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new IllegalStateException(message);
    }
}
