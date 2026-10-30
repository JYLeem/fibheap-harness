import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeSet;

/**
 * 응용별 힙과 알고리즘 검증 (외부 라이브러리 없음 — IntelliJ 에서 main 으로 바로 실행).
 *
 * 기준(정답) 구현
 *   IndexedFibonacciHeap  ↔ 정렬된 집합(TreeSet) 모델, 매 연산 후 validate()
 *   Dijkstra              ↔ Bellman-Ford(작은 그래프), binary heap Dijkstra(큰 그래프), 경로 가중치 재계산
 *   Prim                  ↔ Kruskal(union-find) 총 비용, 결과가 신장 숲인지(간선 수·사이클 없음)
 *   MeldableFibonacciHeap ↔ (키, 순서) TreeSet 모델 — 여러 힙 + meld/delete/updateKey/잘못된 핸들
 *   JobScheduler          ↔ 시나리오 기대 실행 순서
 *
 * 실행: java -cp out ApplicationsTest   (실패하면 seed 와 함께 종료 코드 1)
 */
public class ApplicationsTest {

    private interface TestCase { void run() throws Exception; }

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        test("IndexedFibonacciHeap: 무작위 연산 vs 정렬 집합", ApplicationsTest::indexedHeapRandom);
        test("IndexedFibonacciHeap: 경계·잘못된 요청", ApplicationsTest::indexedHeapEdges);
        test("IndexedFibonacciHeap: 연쇄 절단이 차수 상한을 지킴", ApplicationsTest::indexedHeapDegreeBound);
        test("Dijkstra: Bellman-Ford 와 비교 (작은 그래프 300개)", ApplicationsTest::dijkstraVsBellmanFord);
        test("Dijkstra: binary heap Dijkstra 와 비교 (큰 그래프)", ApplicationsTest::dijkstraVsBinaryHeap);
        test("Dijkstra: 배송 지도 기대값", ApplicationsTest::dijkstraScenario);
        test("Prim: Kruskal 과 비교 + 신장 숲 검사", ApplicationsTest::primVsKruskal);
        test("Prim: 캠퍼스 지도 기대값", ApplicationsTest::primScenario);
        test("MeldableFibonacciHeap: 여러 힙 무작위 연산 vs 모델", ApplicationsTest::meldableRandom);
        test("MeldableFibonacciHeap: 잘못된 핸들 거부", ApplicationsTest::meldableHandleValidation);
        test("MeldableFibonacciHeap: 같은 키는 삽입 순서(FIFO)", ApplicationsTest::meldableFifoTies);
        test("JobScheduler: 시나리오 실행 순서", ApplicationsTest::schedulerScenario);
        test("JobScheduler: 연쇄 장애 이관 후 소속 추적", ApplicationsTest::schedulerChainedFailover);
        System.out.println("------------------------------------------------------------");
        System.out.println("통과 " + passed + ", 실패 " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void test(String name, TestCase tc) {
        long t0 = System.nanoTime();
        try {
            tc.run();
            passed++;
            System.out.printf("[PASS] %-55s %6d ms%n", name, (System.nanoTime() - t0) / 1_000_000);
        } catch (Throwable e) {
            failed++;
            System.out.println("[FAIL] " + name + "\n       " + e);
            StackTraceElement[] st = e.getStackTrace();
            for (int i = 0; i < Math.min(3, st.length); i++) System.out.println("         at " + st[i]);
        }
    }

    // ================================================================== IndexedFibonacciHeap

    private static void indexedHeapRandom() {
        for (long seed = 1; seed <= 40; seed++) {
            Random rnd = new Random(seed);
            int n = 1 + rnd.nextInt(400);
            IndexedFibonacciHeap h = new IndexedFibonacciHeap(n);
            long[] keyOf = new long[n];
            boolean[] in = new boolean[n];
            TreeSet<long[]> model = new TreeSet<>(Comparator.<long[]>comparingLong(a -> a[0]).thenComparingLong(a -> a[1]));
            for (int step = 0; step < 4000; step++) {
                int op = rnd.nextInt(10);
                int v = rnd.nextInt(n);
                String ctx = "seed " + seed + " step " + step;
                if (op < 4) {                                         // insert (또는 이미 있으면 거부 확인)
                    long k = rnd.nextInt(50) - 10;
                    if (in[v]) {
                        expectThrows(IllegalArgumentException.class, () -> h.insert(v, k), ctx + " 중복 insert");
                    } else {
                        h.insert(v, k);
                        in[v] = true;
                        keyOf[v] = k;
                        model.add(new long[]{k, v});
                    }
                } else if (op < 7) {                                  // decreaseKey
                    if (!in[v]) {
                        expectThrows(IllegalArgumentException.class, () -> h.decreaseKey(v, -1000), ctx + " 없는 원소");
                        continue;
                    }
                    long k = keyOf[v] - rnd.nextInt(30);
                    model.remove(new long[]{keyOf[v], v});
                    h.decreaseKey(v, k);
                    keyOf[v] = k;
                    model.add(new long[]{k, v});
                } else {                                              // extractMin
                    if (model.isEmpty()) {
                        expectThrows(NoSuchElementException.class, h::extractMin, ctx + " 빈 힙");
                        continue;
                    }
                    long expectedKey = model.first()[0];
                    int got = h.extractMin();
                    eq(expectedKey, keyOf[got], ctx + " extractMin 키");
                    eq(true, in[got], ctx + " 꺼낸 원소가 힙에 있던 원소");
                    model.remove(new long[]{keyOf[got], got});
                    in[got] = false;
                    eq(true, h.wasRemoved(got), ctx + " wasRemoved");
                }
                h.validate();
                eq(model.size(), h.size(), ctx + " size");
                if (!model.isEmpty()) eq(model.first()[0], h.minKey(), ctx + " minKey");
            }
        }
    }

    private static void indexedHeapEdges() {
        IndexedFibonacciHeap h = new IndexedFibonacciHeap(5);
        expectThrows(NoSuchElementException.class, h::extractMin, "빈 힙 extractMin");
        expectThrows(NoSuchElementException.class, h::peekMin, "빈 힙 peekMin");
        expectThrows(IndexOutOfBoundsException.class, () -> h.insert(5, 0), "범위 밖 id");
        h.insert(0, Long.MAX_VALUE);
        h.insert(1, Long.MIN_VALUE);
        h.insert(2, 0);
        eq(1, h.extractMin(), "MIN_VALUE 먼저");
        expectThrows(IllegalArgumentException.class, () -> h.decreaseKey(1, -5), "꺼낸 원소 decreaseKey 거부");
        expectThrows(IllegalArgumentException.class, () -> h.decreaseKey(3, -5), "넣은 적 없는 원소 거부");
        expectThrows(IllegalArgumentException.class, () -> h.decreaseKey(2, 1), "키 증가 거부");
        h.decreaseKey(0, Long.MIN_VALUE);
        h.validate();
        eq(0, h.extractMin(), "MAX → MIN 감소");
        h.insert(1, 3);                                              // 꺼낸 원소 재삽입 허용
        eq(2, h.extractMin(), "키 0");
        eq(1, h.extractMin(), "재삽입 원소");
        eq(true, h.isEmpty(), "빈 힙");
        h.validate();
        new IndexedFibonacciHeap(0).validate();
    }

    /**
     * 값은 맞지만 구조가 망가지는 결함(마킹·연쇄 절단 누락)을 잡는 테스트.
     * 65개를 넣고 1개를 꺼내면 원소 64개짜리 트리 하나(루트 차수 6)가 된다.
     * 깊이 2 이상인 원소를 모두 아주 작은 키로 줄여 잘라낸 뒤 꺼낸다.
     * 올바르면 연쇄 절단으로 차수가 줄고, 마킹이 없으면 루트가 자식 6개를 가진 7개짜리 트리가 되어
     * 차수 상한 floor(log_phi 7) = 4 를 넘는다 → validate() 실패.
     */
    private static void indexedHeapDegreeBound() {
        int n = 65;
        IndexedFibonacciHeap h = new IndexedFibonacciHeap(n);
        for (int v = 0; v < n; v++) h.insert(v, 1000 + v);
        h.extractMin();
        h.validate();
        List<Integer> deep = new ArrayList<>();
        for (int v = 1; v < n; v++) {
            int depth = 0;
            for (int p = h.parentOf(v); p != -1; p = h.parentOf(p)) depth++;
            if (depth >= 2) deep.add(v);
        }
        eq(57, deep.size(), "깊이 2 이상 원소 수 (64 - 루트 1 - 자식 6)");
        long small = -deep.size();
        for (int v : deep) {
            h.decreaseKey(v, small++);
            h.validate();
        }
        for (int i = 0; i < deep.size(); i++) {
            h.extractMin();
            h.validate();
        }
        eq(7, h.size(), "루트와 원래 자식 6개만 남음");
    }

    // ================================================================== Dijkstra

    private static void dijkstraVsBellmanFord() {
        for (long seed = 1; seed <= 300; seed++) {
            Random rnd = new Random(seed);
            int n = 1 + rnd.nextInt(40);
            double p = rnd.nextDouble() * 0.5;
            Graph g = Graph.random(n, p, rnd.nextBoolean() ? 3 : 100, false, seed);   // 0 가중치 포함
            int s = rnd.nextInt(n);
            Dijkstra.Result r = Dijkstra.shortestPaths(g, s);
            long[] expected = bellmanFord(g, s);
            if (!Arrays.equals(expected, r.dist)) {
                throw new AssertionError("seed " + seed + ": 기대 " + Arrays.toString(expected) + ", 실제 " + Arrays.toString(r.dist));
            }
            checkPaths(g, r, "seed " + seed);
        }
    }

    private static void dijkstraVsBinaryHeap() {
        long[][] configs = {{2_000, 0, 1_000}, {20_000, 1, 1_000_000}, {800, 2, 10}};
        for (long[] c : configs) {
            int n = (int) c[0];
            Graph g = c[1] == 2 ? Graph.random(n, 0.3, (int) c[2], false, 77)
                    : Graph.randomSparse(n, 8, (int) c[2], c[1] == 1, 42 + n);
            for (int s : new int[]{0, n / 2, n - 1}) {
                Dijkstra.Result r = Dijkstra.shortestPaths(g, s);
                long[] expected = binaryHeapDijkstra(g, s);
                if (!Arrays.equals(expected, r.dist)) throw new AssertionError("n=" + n + " s=" + s + " 거리 불일치");
                checkPaths(g, r, "n=" + n + " s=" + s);
            }
        }
    }

    private static void dijkstraScenario() {
        Dijkstra.Result r = Dijkstra.shortestPaths(FibonacciApplications.deliveryMap(), 0);
        eq("[0, 7, 9, 20, 20, 11, 29, 33]", Arrays.toString(r.dist), "배송 지도 거리");
        eq(List.of(0, 2, 5, 4, 6, 7), r.path(7), "신도시 경로");
    }

    /** 경로의 실제 간선 가중치 합 == dist, 경로 시작 == source */
    private static void checkPaths(Graph g, Dijkstra.Result r, String ctx) {
        for (int t = 0; t < g.vertexCount(); t++) {
            List<Integer> path = r.path(t);
            if (!r.reachable(t)) {
                eq(true, path.isEmpty(), ctx + " 도달 불가 경로");
                continue;
            }
            eq(r.source, (long) path.get(0), ctx + " 경로 시작");
            long sum = 0;
            for (int i = 1; i < path.size(); i++) sum += minEdge(g, path.get(i - 1), path.get(i));
            eq(r.dist[t], sum, ctx + " 경로 " + t + " 가중치 합");
        }
    }

    private static long minEdge(Graph g, int u, int v) {
        long best = Long.MAX_VALUE;
        for (int e = g.firstEdge(u); e != -1; e = g.nextEdge(e)) if (g.target(e) == v) best = Math.min(best, g.weight(e));
        if (best == Long.MAX_VALUE) throw new AssertionError("경로에 없는 간선 " + u + "→" + v);
        return best;
    }

    static long[] bellmanFord(Graph g, int s) {
        int n = g.vertexCount();
        long[] d = new long[n];
        Arrays.fill(d, Dijkstra.INF);
        d[s] = 0;
        for (int i = 0; i < n; i++) {
            boolean changed = false;
            for (int u = 0; u < n; u++) {
                if (d[u] == Dijkstra.INF) continue;
                for (int e = g.firstEdge(u); e != -1; e = g.nextEdge(e)) {
                    long nd = d[u] + g.weight(e);
                    if (nd < d[g.target(e)]) {
                        d[g.target(e)] = nd;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        return d;
    }

    /** 비교 기준: java.util.PriorityQueue (binary heap) + lazy deletion */
    static long[] binaryHeapDijkstra(Graph g, int s) {
        int n = g.vertexCount();
        long[] d = new long[n];
        Arrays.fill(d, Dijkstra.INF);
        boolean[] done = new boolean[n];
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        d[s] = 0;
        pq.add(new long[]{0, s});
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int u = (int) top[1];
            if (done[u]) continue;
            done[u] = true;
            for (int e = g.firstEdge(u); e != -1; e = g.nextEdge(e)) {
                int v = g.target(e);
                long nd = d[u] + g.weight(e);
                if (nd < d[v]) {
                    d[v] = nd;
                    pq.add(new long[]{nd, v});
                }
            }
        }
        return d;
    }

    // ================================================================== Prim

    private static void primVsKruskal() {
        for (long seed = 1; seed <= 300; seed++) {
            Random rnd = new Random(seed);
            int n = 1 + rnd.nextInt(60);
            Graph g = Graph.random(n, rnd.nextDouble() * 0.3, rnd.nextBoolean() ? 5 : 1000, true, seed);  // 끊어진 그래프 포함
            PrimMST.Result r = PrimMST.minimumSpanningForest(g);
            long[] kr = kruskal(g);
            String ctx = "seed " + seed + " n=" + n;
            eq(kr[0], r.totalWeight, ctx + " 총 비용");
            eq(kr[1], (long) r.components, ctx + " 구성 요소 수");
            eq(n - r.components, r.edges.size(), ctx + " 간선 수 = n - 구성 요소");
            int[] uf = ufNew(n);
            long sum = 0;
            for (PrimMST.Edge e : r.edges) {
                eq(e.weight, minEdge(g, e.u, e.v), ctx + " 간선 " + e + " 이 그래프의 최소 간선");
                eq(true, ufUnion(uf, e.u, e.v), ctx + " 사이클 없음");
                sum += e.weight;
            }
            eq(r.totalWeight, sum, ctx + " 간선 합 = totalWeight");
        }
    }

    private static void primScenario() {
        Graph g = new Graph(7);
        int[][] ducts = {{0, 1, 4}, {0, 2, 8}, {1, 2, 11}, {1, 3, 8}, {2, 4, 7}, {2, 5, 1}, {3, 4, 2},
                {3, 6, 4}, {4, 5, 6}, {5, 6, 2}, {4, 6, 14}, {0, 6, 9}};
        for (int[] d : ducts) g.addUndirectedEdge(d[0], d[1], d[2]);
        eq(21L, PrimMST.minimumSpanningForest(g).totalWeight, "캠퍼스 총 공사비");
    }

    /** {총 비용, 구성 요소 수} */
    static long[] kruskal(Graph g) {
        int n = g.vertexCount();
        List<int[]> edges = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            for (int e = g.firstEdge(u); e != -1; e = g.nextEdge(e)) edges.add(new int[]{g.weight(e), u, g.target(e)});
        }
        edges.sort(Comparator.comparingInt(a -> a[0]));
        int[] uf = ufNew(n);
        long total = 0;
        int components = n;
        for (int[] e : edges) {
            if (ufUnion(uf, e[1], e[2])) {
                total += e[0];
                components--;
            }
        }
        return new long[]{total, components};
    }

    private static int[] ufNew(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        return p;
    }

    private static int ufFind(int[] p, int x) {
        while (p[x] != x) x = p[x] = p[p[x]];
        return x;
    }

    private static boolean ufUnion(int[] p, int a, int b) {
        int ra = ufFind(p, a), rb = ufFind(p, b);
        if (ra == rb) return false;
        p[ra] = rb;
        return true;
    }

    // ================================================================== MeldableFibonacciHeap

    /** 모델 원소: 키와 순서는 핸들에서 읽는다 (순서 규칙 = (key, sequence) 사전순) */
    private static final Comparator<MeldableFibonacciHeap.Handle<Integer>> ORDER =
            Comparator.<MeldableFibonacciHeap.Handle<Integer>>comparingLong(h -> h.key())
                    .thenComparingLong(h -> h.sequence());

    private static void meldableRandom() {
        for (long seed = 1; seed <= 60; seed++) {
            Random rnd = new Random(seed);
            int k = 1 + rnd.nextInt(4);
            List<MeldableFibonacciHeap<Integer>> heaps = new ArrayList<>();
            List<TreeSet<MeldableFibonacciHeap.Handle<Integer>>> model = new ArrayList<>();
            Map<MeldableFibonacciHeap.Handle<Integer>, Integer> ownerOf = new HashMap<>();
            List<MeldableFibonacciHeap.Handle<Integer>> everything = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                heaps.add(new MeldableFibonacciHeap<>("h" + i));
                model.add(new TreeSet<>(ORDER));
            }
            int value = 0;
            for (int step = 0; step < 3000; step++) {
                String ctx = "seed " + seed + " step " + step;
                int i = rnd.nextInt(k);
                MeldableFibonacciHeap<Integer> h = heaps.get(i);
                TreeSet<MeldableFibonacciHeap.Handle<Integer>> m = model.get(i);
                int op = rnd.nextInt(20);
                if (op < 7) {                                                      // insert
                    MeldableFibonacciHeap.Handle<Integer> x = h.insert(value++, rnd.nextInt(30));
                    m.add(x);
                    ownerOf.put(x, i);
                    everything.add(x);
                } else if (op < 10) {                                              // extractMin
                    MeldableFibonacciHeap.Handle<Integer> expected = m.isEmpty() ? null : m.first();
                    MeldableFibonacciHeap.Handle<Integer> got = h.extractMin();
                    if (expected != got) throw new AssertionError(ctx + " extractMin: 기대 " + expected + ", 실제 " + got);
                    if (got != null) {
                        m.remove(got);
                        ownerOf.remove(got);
                        eq(false, got.isQueued(), ctx + " 꺼낸 핸들 isQueued");
                    }
                } else if (op < 16 && !everything.isEmpty()) {                     // decrease / update / delete (임의 핸들)
                    MeldableFibonacciHeap.Handle<Integer> x = everything.get(rnd.nextInt(everything.size()));
                    Integer owner = ownerOf.get(x);
                    MeldableFibonacciHeap<Integer> target = heaps.get(rnd.nextInt(k));
                    boolean valid = owner != null && heaps.get(owner) == target;
                    int kind = op - 10;                                            // 0~1 decrease, 2~3 update, 4~5 delete
                    long newKey = kind < 2 ? x.key() - rnd.nextInt(10) : x.key() + rnd.nextInt(21) - 10;
                    if (!valid) {                                                  // 빠진 핸들 / 다른 힙의 핸들 → 거부
                        Runnable call = kind < 2 ? () -> target.decreaseKey(x, newKey)
                                : kind < 4 ? () -> target.updateKey(x, newKey) : () -> target.delete(x);
                        expectThrows(IllegalArgumentException.class, call::run, ctx + " 잘못된 핸들 " + x);
                    } else {
                        TreeSet<MeldableFibonacciHeap.Handle<Integer>> om = model.get(owner);
                        om.remove(x);
                        if (kind < 2) {
                            target.decreaseKey(x, newKey);
                            om.add(x);
                        } else if (kind < 4) {
                            target.updateKey(x, newKey);
                            om.add(x);
                        } else {
                            target.delete(x);
                            ownerOf.remove(x);
                            eq(false, x.isQueued(), ctx + " 삭제된 핸들 isQueued");
                        }
                    }
                } else if (op < 18) {                                              // meld
                    int j = rnd.nextInt(k);
                    if (j == i) {
                        expectThrows(IllegalArgumentException.class, () -> h.meld(h), ctx + " 자기 병합 거부");
                    } else {
                        h.meld(heaps.get(j));
                        for (MeldableFibonacciHeap.Handle<Integer> x : model.get(j)) ownerOf.put(x, i);
                        m.addAll(model.get(j));
                        model.get(j).clear();
                    }
                } else {                                                           // peek
                    MeldableFibonacciHeap.Handle<Integer> expected = m.isEmpty() ? null : m.first();
                    if (expected != h.peek()) throw new AssertionError(ctx + " peek 불일치");
                }
                for (int t = 0; t < k; t++) {
                    heaps.get(t).validate();
                    eq(model.get(t).size(), heaps.get(t).size(), ctx + " h" + t + " size");
                }
                if (step % 97 == 0) {                                              // 소속 추적 전체 확인
                    for (MeldableFibonacciHeap.Handle<Integer> x : everything) {
                        Integer o = ownerOf.get(x);
                        MeldableFibonacciHeap<Integer> where = MeldableFibonacciHeap.heapOf(x);
                        if ((o == null ? null : heaps.get(o)) != where) throw new AssertionError(ctx + " heapOf(" + x + ")");
                    }
                }
            }
        }
    }

    private static void meldableHandleValidation() {
        MeldableFibonacciHeap<String> a = new MeldableFibonacciHeap<>("A");
        MeldableFibonacciHeap<String> b = new MeldableFibonacciHeap<>("B");
        MeldableFibonacciHeap.Handle<String> x = a.insert("x", 10);
        MeldableFibonacciHeap.Handle<String> y = b.insert("y", 20);
        expectThrows(IllegalArgumentException.class, () -> a.decreaseKey(y, 1), "다른 힙의 핸들");
        a.meld(b);
        eq(true, a.contains(y), "병합 후 y 는 A 소속");
        expectThrows(IllegalArgumentException.class, () -> b.decreaseKey(y, 1), "병합으로 비워진 B 로 접근");
        a.decreaseKey(y, 1);
        eq("y", a.extractMin().value(), "y 먼저");
        expectThrows(IllegalArgumentException.class, () -> a.decreaseKey(y, 0), "꺼낸 핸들");
        expectThrows(IllegalArgumentException.class, () -> a.delete(y), "꺼낸 핸들 삭제");
        expectThrows(IllegalArgumentException.class, () -> a.decreaseKey(x, 11), "키 증가는 decreaseKey 로 불가");
        expectThrows(IllegalArgumentException.class, () -> a.meld(a), "자기 병합");
        b.insert("z", 5);                                                          // 비워진 B 재사용
        eq(1, b.size(), "B 재사용");
        a.validate();
        b.validate();
        eq(null, new MeldableFibonacciHeap<String>("E").extractMin(), "빈 힙 extractMin = null");
    }

    private static void meldableFifoTies() {
        MeldableFibonacciHeap<String> h = new MeldableFibonacciHeap<>("H");
        MeldableFibonacciHeap<String> other = new MeldableFibonacciHeap<>("O");
        h.insert("a", 5);
        other.insert("b", 5);
        h.insert("c", 5);
        MeldableFibonacciHeap.Handle<String> d = h.insert("d", 9);
        h.meld(other);
        h.decreaseKey(d, 5);                                       // 키만 같아지고 순서(제출 순)는 유지 → a b c d
        StringBuilder sb = new StringBuilder();
        for (MeldableFibonacciHeap.Handle<String> x = h.extractMin(); x != null; x = h.extractMin()) sb.append(x.value());
        eq("abcd", sb.toString(), "같은 키는 삽입 순서");
    }

    // ================================================================== JobScheduler

    private static void schedulerScenario() {
        JobScheduler s = new JobScheduler();
        s.addServer("A");
        s.addServer("B");
        JobScheduler.Job backup = s.submit("A", "백업", JobScheduler.time(10, 0));
        JobScheduler.Job logs = s.submit("A", "로그", JobScheduler.time(11, 30));
        s.submit("A", "리포트", JobScheduler.time(9, 30));
        JobScheduler.Job mail = s.submit("A", "메일", JobScheduler.time(12, 0));
        s.submit("B", "정산", JobScheduler.time(9, 45));
        s.submit("B", "이미지", JobScheduler.time(13, 0));
        s.submit("B", "캐시", JobScheduler.time(10, 15));
        eq("리포트", s.runNext("A").name, "A 첫 실행");
        s.escalate(mail, JobScheduler.time(9, 40));
        s.postpone(backup, JobScheduler.time(14, 0));
        s.cancel(logs);
        s.failover("A", "B");
        eq("B", s.serverOf(backup), "이관 후 백업 소속");
        eq(5, s.queued("B"), "B 대기 수");
        List<String> order = new ArrayList<>();
        for (JobScheduler.Job j = s.runNext("B"); j != null; j = s.runNext("B")) order.add(j.name);
        eq(List.of("메일", "정산", "캐시", "이미지", "백업"), order, "B 실행 순서");
        eq(JobScheduler.Status.CANCELLED, logs.status(), "로그 상태");
        eq("B", mail.ranOn(), "메일 실행 서버");
        expectThrows(IllegalArgumentException.class, () -> s.escalate(logs, 0), "취소된 작업");
        expectThrows(IllegalArgumentException.class, () -> s.cancel(mail), "실행된 작업");
        expectThrows(NoSuchElementException.class, () -> s.runNext("A"), "제거된 서버");
    }

    private static void schedulerChainedFailover() {
        JobScheduler s = new JobScheduler();
        int servers = 6;
        for (int i = 0; i < servers; i++) s.addServer("S" + i);
        List<JobScheduler.Job> jobs = new ArrayList<>();
        Random rnd = new Random(5);
        for (int i = 0; i < 600; i++) jobs.add(s.submit("S" + (i % servers), "job" + i, rnd.nextInt(1000)));
        for (int i = servers - 1; i >= 1; i--) s.failover("S" + i, "S" + (i - 1));   // S5→S4→…→S1→S0 연쇄 이관
        for (JobScheduler.Job j : jobs) eq("S0", s.serverOf(j), j.name + " 소속");
        long prev = Long.MIN_VALUE;
        int count = 0;
        for (JobScheduler.Job j = s.runNext("S0"); j != null; j = s.runNext("S0")) {
            eq(true, j.deadline() >= prev, "마감 오름차순");
            prev = j.deadline();
            count++;
        }
        eq(600, count, "전체 실행 수");
    }

    // ================================================================== 도우미

    private interface ThrowingRunnable { void run() throws Exception; }

    private static void expectThrows(Class<? extends Throwable> type, ThrowingRunnable r, String what) {
        try {
            r.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError(what + ": " + type.getSimpleName() + " 기대, 실제 " + t, t);
        }
        throw new AssertionError(what + ": " + type.getSimpleName() + " 가 발생하지 않음");
    }

    private static void eq(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": 기대 " + expected + ", 실제 " + actual);
        }
    }

    private static void eq(long expected, long actual, String what) {
        if (expected != actual) throw new AssertionError(what + ": 기대 " + expected + ", 실제 " + actual);
    }
}
