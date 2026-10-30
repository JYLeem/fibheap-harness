package fibheap;

import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * [기법 10] Fibonacci heap vs java.util.PriorityQueue(binary heap) JMH 벤치마크.
 *
 * - 원소 수 n 을 1k → 10k → 100k 로 늘리며 측정한다 (@Param).
 * - 두 힙 모두 같은 시드로 만든 같은 키·같은 감소 대상·같은 감소폭을 쓴다.
 * - 상태를 바꾸는 측정(decrease, meld)은 매 호출 전에 새 힙을 만든다 (Level.Invocation — 준비 시간은 측정 제외).
 *   한 호출이 n 개 연산을 하므로 Level.Invocation 의 타이머 오차보다 충분히 길다.
 * - PriorityQueue 에는 decrease-key 가 없으므로 "새 항목 삽입 + 꺼낼 때 낡은 항목 버리기(lazy)" 로 대응한다.
 *   decrease 단독 비교는 비용이 drain 으로 미뤄지므로 공정하지 않다 → decreaseThenDrain 을 함께 본다.
 *
 * 결과는 평균 시간(µs/호출)이다. 이를 n 으로 나눈 값이 원소당 비용이고, n 이 10배일 때 몇 배가 되는지로
 * 상각 복잡도와 대조한다. 측정 결과는 이 JVM·이 하드웨어·이 데이터 분포에서의 값일 뿐, 어느 힙이 "항상" 빠르다는 근거가 아니다.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
@State(Scope.Benchmark)
public class HeapBenchmark {

    @Param({"1000", "10000", "100000"})
    public int n;

    int[] keys;
    int[] targets;
    int[] deltas;
    Main.FibonacciHeap fibBuilt;
    PriorityQueue<Integer> pqBuilt;

    @Setup(Level.Trial)
    public void generate() {
        Random rnd = new Random(12345L + n);
        keys = new int[n];
        targets = new int[n];
        deltas = new int[n];
        for (int i = 0; i < n; i++) {
            keys[i] = rnd.nextInt(1_000_000_000);
            targets[i] = rnd.nextInt(n);
            deltas[i] = 1 + rnd.nextInt(1_000_000);
        }
        fibBuilt = new Main.FibonacciHeap();
        pqBuilt = new PriorityQueue<>();
        for (int k : keys) {
            fibBuilt.insert(k);
            pqBuilt.add(k);
        }
        fibBuilt.extractMin();          // 통합된 실제 구조에서 findMin 측정
        pqBuilt.poll();
    }

    // ------------------------------------------------------------------ insert / findMin / extractMin

    @Benchmark
    public Object fibInsert() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        for (int k : keys) h.insert(k);
        return h;
    }

    @Benchmark
    public Object pqInsert() {
        PriorityQueue<Integer> q = new PriorityQueue<>();
        for (int k : keys) q.add(k);
        return q;
    }

    @Benchmark
    public Integer fibFindMin() {
        return fibBuilt.minimum();
    }

    @Benchmark
    public Integer pqFindMin() {
        return pqBuilt.peek();
    }

    /** 삽입 + 전체 삭제. extractMin 비용 = 이 값 - insert 값 */
    @Benchmark
    public long fibInsertThenDrain() {
        Main.FibonacciHeap h = new Main.FibonacciHeap();
        for (int k : keys) h.insert(k);
        long acc = 0;
        while (!h.isEmpty()) acc += h.extractMin();
        return acc;
    }

    @Benchmark
    public long pqInsertThenDrain() {
        PriorityQueue<Integer> q = new PriorityQueue<>();
        for (int k : keys) q.add(k);
        long acc = 0;
        while (!q.isEmpty()) acc += q.poll();
        return acc;
    }

    // ------------------------------------------------------------------ decreaseKey

    @State(Scope.Thread)
    public static class FibDecrease {
        Main.FibonacciHeap heap;
        Main.FibonacciHeap.Node[] nodes;

        @Setup(Level.Invocation)
        public void setUp(HeapBenchmark b) {
            heap = new Main.FibonacciHeap();
            nodes = new Main.FibonacciHeap.Node[b.n];
            for (int i = 0; i < b.n; i++) nodes[i] = heap.insert(b.keys[i]);
            Main.FibonacciHeap.Node min = heap.minNode();
            heap.extractMin();          // 트리를 만들어 cut / cascading cut 이 실제로 일어나게 함
            for (int i = 0; i < b.n; i++) if (nodes[i] == min) nodes[i] = nodes[(i + 1) % b.n];
        }
    }

    @State(Scope.Thread)
    public static class PqDecrease {
        PriorityQueue<Long> q;
        int[] key;
        int[] targets;
        boolean[] removed;

        @Setup(Level.Invocation)
        public void setUp(HeapBenchmark b) {
            q = new PriorityQueue<>();
            key = b.keys.clone();
            targets = b.targets.clone();
            removed = new boolean[b.n];
            for (int i = 0; i < b.n; i++) q.add(encode(key[i], i));
            int id = decodeId(q.peek());
            pop();
            for (int i = 0; i < b.n; i++) if (targets[i] == id) targets[i] = (id + 1) % b.n;
        }

        void decrease(int id, int delta) {
            int nk = Math.max(0, key[id] - delta);
            if (nk == key[id]) return;
            key[id] = nk;
            q.add(encode(nk, id));
        }

        long pop() {
            while (!q.isEmpty()) {
                long e = q.poll();
                int id = decodeId(e);
                if (!removed[id] && (int) (e >>> 20) == key[id]) {
                    removed[id] = true;
                    return key[id];
                }
            }
            return -1;
        }

        static long encode(int k, int id) { return ((long) k << 20) | id; }
        static int decodeId(long e) { return (int) (e & ((1 << 20) - 1)); }
    }

    @Benchmark
    public int fibDecreaseKeys(FibDecrease s) {
        for (int i = 0; i < n; i++) {
            Main.FibonacciHeap.Node x = s.nodes[targets[i]];
            s.heap.decreaseKey(x, Math.max(0, x.key - deltas[i]));
        }
        return s.heap.size();
    }

    @Benchmark
    public int pqLazyDecrease(PqDecrease s) {
        for (int i = 0; i < n; i++) s.decrease(s.targets[i], deltas[i]);
        return s.q.size();
    }

    @Benchmark
    public long fibDecreaseThenDrain(FibDecrease s) {
        fibDecreaseKeys(s);
        long acc = 0;
        while (!s.heap.isEmpty()) acc += s.heap.extractMin();
        return acc;
    }

    @Benchmark
    public long pqLazyDecreaseThenDrain(PqDecrease s) {
        pqLazyDecrease(s);
        long acc = 0;
        for (long v = s.pop(); v >= 0; v = s.pop()) acc += v;
        return acc;
    }

    // ------------------------------------------------------------------ meld

    @State(Scope.Thread)
    public static class FibMeld {
        Main.FibonacciHeap a;
        Main.FibonacciHeap b;

        @Setup(Level.Invocation)
        public void setUp(HeapBenchmark bench) {
            a = new Main.FibonacciHeap();
            b = new Main.FibonacciHeap();
            for (int i = 0; i < bench.n; i++) (i % 2 == 0 ? a : b).insert(bench.keys[i]);
        }
    }

    @State(Scope.Thread)
    public static class PqMeld {
        PriorityQueue<Integer> a;
        PriorityQueue<Integer> b;

        @Setup(Level.Invocation)
        public void setUp(HeapBenchmark bench) {
            a = new PriorityQueue<>();
            b = new PriorityQueue<>();
            for (int i = 0; i < bench.n; i++) (i % 2 == 0 ? a : b).add(bench.keys[i]);
        }
    }

    /** 루트 리스트 연결은 O(1)이지만, 이 구현은 키 인덱스(HashMap)도 병합하므로 O(서로 다른 키 수) */
    @Benchmark
    public int fibMeld(FibMeld s) {
        s.a.union(s.b);
        return s.a.size();
    }

    @Benchmark
    public int pqMeld(PqMeld s) {
        s.a.addAll(s.b);
        return s.a.size();
    }
}
