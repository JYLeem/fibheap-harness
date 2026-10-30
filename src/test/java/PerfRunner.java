import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * [기법 10] 의존성 없는 성능 측정기 (JMH 를 쓸 수 없는 환경용 · 경향 확인용).
 * 정식 측정은 perf-jmh 모듈(JMH)을 사용한다.
 *
 * 비교 대상: java.util.PriorityQueue (배열 기반 binary heap). 같은 시드의 같은 데이터·같은 연산 순서를 쓴다.
 *   - decreaseKey: PriorityQueue 에는 decrease-key 가 없으므로, 실무에서 쓰는 "새 항목 삽입 + 꺼낼 때 낡은 항목 버리기(lazy)" 로 대응한다.
 *     그래서 decreaseKey 단독 측정은 공정하지 않으며, 그 비용이 drain 으로 넘어가는 것까지 본 'decrease+drain' 도 함께 측정한다.
 *   - meld: Fibonacci 는 루트 리스트 연결(O(1)) + 이 구현 고유의 키 인덱스 병합(O(병합되는 서로 다른 키 수)),
 *           PriorityQueue 는 addAll(O(m log n)).
 *
 * 실행: java -cp <test-classes>:<main-classes> PerfRunner [n1 n2 ...]
 *       -Dperf.warmup=5 -Dperf.reps=9
 * 출력 값은 "원소 하나당 ns" 의 중앙값이다. 측정 시간은 상각 시간 복잡도와 다르다(상수, 캐시, GC, JIT 영향 포함).
 */
public class PerfRunner {

    private static long sink;   // JIT 가 결과를 버리지 않도록

    interface Workload {
        /** 측정 구간만 반환 (ns) */
        long run(Data d);
    }

    static final class Data {
        final int n;
        final int[] keys;
        final int[] targets;
        final int[] deltas;

        Data(int n, long seed) {
            this.n = n;
            Random rnd = new Random(seed);
            keys = new int[n];
            targets = new int[n];
            deltas = new int[n];
            for (int i = 0; i < n; i++) {
                keys[i] = rnd.nextInt(1_000_000_000);
                targets[i] = rnd.nextInt(n);
                deltas[i] = 1 + rnd.nextInt(1_000_000);
            }
        }
    }

    public static void main(String[] args) {
        int[] sizes = args.length > 0 ? Arrays.stream(args).mapToInt(Integer::parseInt).toArray()
                : new int[]{1_000, 10_000, 100_000};
        int warmup = Integer.getInteger("perf.warmup", 5);
        int reps = Integer.getInteger("perf.reps", 9);

        String[] names = {"insert", "findMin", "extractMin(drain)", "decreaseKey", "decrease+drain", "meld"};
        Workload[] fib = {PerfRunner::fibInsert, PerfRunner::fibFindMin, PerfRunner::fibDrain,
                PerfRunner::fibDecrease, PerfRunner::fibDecreaseDrain, PerfRunner::fibMeld};
        Workload[] pq = {PerfRunner::pqInsert, PerfRunner::pqFindMin, PerfRunner::pqDrain,
                PerfRunner::pqDecrease, PerfRunner::pqDecreaseDrain, PerfRunner::pqMeld};

        System.out.printf(Locale.ROOT, "Java %s, warmup %d, reps %d — 단위: 원소당 ns (중앙값)%n",
                System.getProperty("java.version"), warmup, reps);
        System.out.printf(Locale.ROOT, "%-18s %9s %12s %12s %9s%n", "workload", "n", "Fibonacci", "BinaryPQ", "Fib/PQ");
        for (int w = 0; w < names.length; w++) {
            for (int n : sizes) {
                Data d = new Data(n, 12345L + n);
                double f = measure(fib[w], d, warmup, reps) / n;
                double p = measure(pq[w], d, warmup, reps) / n;
                System.out.printf(Locale.ROOT, "%-18s %9d %12.1f %12.1f %9.2f%n", names[w], n, f, p, f / p);
            }
        }
        System.out.println("(sink=" + (sink & 1) + ")");
    }

    private static double measure(Workload w, Data d, int warmup, int reps) {
        for (int i = 0; i < warmup; i++) w.run(d);
        long[] t = new long[reps];
        for (int i = 0; i < reps; i++) t[i] = w.run(d);
        Arrays.sort(t);
        return t[reps / 2];
    }

    // ------------------------------------------------------------------ Fibonacci heap

    private static Main.FibonacciHeap.Node[] build(Main.FibonacciHeap h, Data d) {
        Main.FibonacciHeap.Node[] nodes = new Main.FibonacciHeap.Node[d.n];
        for (int i = 0; i < d.n; i++) nodes[i] = h.insert(d.keys[i]);
        return nodes;
    }

    private static long fibInsert(Data d) {
        long t0 = System.nanoTime();
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        for (int k : d.keys) h.insert(k);
        long t = System.nanoTime() - t0;
        sink += h.size();
        return t;
    }

    private static long fibFindMin(Data d) {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        build(h, d);
        long acc = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < d.n; i++) acc += h.minimum();
        long t = System.nanoTime() - t0;
        sink += acc;
        return t;
    }

    private static long fibDrain(Data d) {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        build(h, d);
        long acc = 0;
        long t0 = System.nanoTime();
        while (!h.isEmpty()) acc += h.extractMin();
        long t = System.nanoTime() - t0;
        sink += acc;
        return t;
    }

    private static Main.FibonacciHeap.Node[] prepareDecrease(Main.FibonacciHeap h, Data d) {
        Main.FibonacciHeap.Node[] nodes = build(h, d);
        Main.FibonacciHeap.Node min = h.minNode();
        h.extractMin();                         // 통합해서 트리 구조를 만든다 (cut 이 실제로 일어나도록)
        for (int i = 0; i < nodes.length; i++) if (nodes[i] == min) nodes[i] = nodes[(i + 1) % nodes.length];
        return nodes;
    }

    private static void fibDecreaseLoop(Main.FibonacciHeap h, Main.FibonacciHeap.Node[] nodes, Data d) {
        for (int i = 0; i < d.n; i++) {
            Main.FibonacciHeap.Node x = nodes[d.targets[i]];
            h.decreaseKey(x, Math.max(0, x.key - d.deltas[i]));
        }
    }

    private static long fibDecrease(Data d) {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node[] nodes = prepareDecrease(h, d);
        long t0 = System.nanoTime();
        fibDecreaseLoop(h, nodes, d);
        long t = System.nanoTime() - t0;
        sink += h.size();
        return t;
    }

    private static long fibDecreaseDrain(Data d) {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        Main.FibonacciHeap.Node[] nodes = prepareDecrease(h, d);
        long acc = 0;
        long t0 = System.nanoTime();
        fibDecreaseLoop(h, nodes, d);
        while (!h.isEmpty()) acc += h.extractMin();
        long t = System.nanoTime() - t0;
        sink += acc;
        return t;
    }

    private static long fibMeld(Data d) {
        Main.FibonacciHeap a = new Main.FibonacciHeap();
        Main.FibonacciHeap b = new Main.FibonacciHeap();
        for (int i = 0; i < d.n; i++) (i % 2 == 0 ? a : b).insert(d.keys[i]);
        long t0 = System.nanoTime();
        a.union(b);
        long t = System.nanoTime() - t0;
        sink += a.size();
        return t;
    }

    // ------------------------------------------------------------------ PriorityQueue (binary heap)

    private static long pqInsert(Data d) {
        long t0 = System.nanoTime();
        PriorityQueue<Integer> q = new PriorityQueue<>();
        for (int k : d.keys) q.add(k);
        long t = System.nanoTime() - t0;
        sink += q.size();
        return t;
    }

    private static long pqFindMin(Data d) {
        PriorityQueue<Integer> q = new PriorityQueue<>();
        for (int k : d.keys) q.add(k);
        long acc = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < d.n; i++) acc += q.peek();
        long t = System.nanoTime() - t0;
        sink += acc;
        return t;
    }

    private static long pqDrain(Data d) {
        PriorityQueue<Integer> q = new PriorityQueue<>();
        for (int k : d.keys) q.add(k);
        long acc = 0;
        long t0 = System.nanoTime();
        while (!q.isEmpty()) acc += q.poll();
        long t = System.nanoTime() - t0;
        sink += acc;
        return t;
    }

    /** lazy decrease-key: (key << 20 | id) 를 새로 넣고, 꺼낼 때 현재 키와 다르면 버린다 */
    private static final class LazyPq {
        final PriorityQueue<Long> q = new PriorityQueue<>();
        final int[] key;
        final int[] targets;
        final boolean[] removed;

        LazyPq(Data d) {
            key = d.keys.clone();
            targets = d.targets.clone();
            removed = new boolean[d.n];
            for (int i = 0; i < d.n; i++) q.add(encode(key[i], i));
            int id = decodeId(q.peek());          // Fibonacci 쪽과 똑같이 최솟값 하나를 먼저 제거
            pop();
            for (int i = 0; i < d.n; i++) if (targets[i] == id) targets[i] = (id + 1) % d.n;
        }

        static long encode(int k, int id) { return ((long) k << 20) | id; }
        static int decodeId(long e) { return (int) (e & ((1 << 20) - 1)); }
        static int decodeKey(long e) { return (int) (e >>> 20); }

        void decrease(int id, int delta) {
            int nk = Math.max(0, key[id] - delta);
            if (nk == key[id]) return;
            key[id] = nk;
            q.add(encode(nk, id));
        }

        Integer pop() {
            while (!q.isEmpty()) {
                long e = q.poll();
                int id = decodeId(e);
                if (!removed[id] && decodeKey(e) == key[id]) {
                    removed[id] = true;
                    return key[id];
                }
            }
            return null;
        }
    }

    private static long pqDecrease(Data d) {
        LazyPq lq = new LazyPq(d);
        long t0 = System.nanoTime();
        for (int i = 0; i < d.n; i++) lq.decrease(lq.targets[i], d.deltas[i]);
        long t = System.nanoTime() - t0;
        sink += lq.q.size();
        return t;
    }

    private static long pqDecreaseDrain(Data d) {
        LazyPq lq = new LazyPq(d);
        long acc = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < d.n; i++) lq.decrease(lq.targets[i], d.deltas[i]);
        for (Integer v = lq.pop(); v != null; v = lq.pop()) acc += v;
        long t = System.nanoTime() - t0;
        sink += acc;
        return t;
    }

    private static long pqMeld(Data d) {
        PriorityQueue<Integer> a = new PriorityQueue<>();
        List<Integer> b = new ArrayList<>();
        PriorityQueue<Integer> bq = new PriorityQueue<>();
        for (int i = 0; i < d.n; i++) {
            if (i % 2 == 0) a.add(d.keys[i]);
            else b.add(d.keys[i]);
        }
        bq.addAll(b);
        long t0 = System.nanoTime();
        a.addAll(bq);
        long t = System.nanoTime() - t0;
        sink += a.size();
        return t;
    }
}
