import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * 응용별 간이 성능 비교 (참고용; 정식 측정은 JMH 권장).
 *   1) Dijkstra: Fibonacci heap(decreaseKey) vs java.util.PriorityQueue(binary heap + lazy 재삽입)
 *      밀집 그래프(m ≫ n)와 희소 그래프. 힙 연산 횟수도 함께 출력한다.
 *   2) 스케줄러 장애 이관: MeldableFibonacciHeap.meld vs PriorityQueue.addAll
 * 같은 그래프·같은 출발점·같은 키를 쓰고, 워밍업 후 반복 측정의 중앙값을 보고한다.
 * 실행 시간은 이 JVM·이 하드웨어에서의 값이며 상각 시간 복잡도와 같지 않다.
 *
 * 실행: java -Xmx2g -cp out ApplicationsBenchmark
 */
public class ApplicationsBenchmark {

    private static long sink;

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version") + ", 중앙값(ms), 워밍업 3회 + 측정 7회");
        System.out.println();
        dijkstra("밀집 그래프 n=2,000, p=0.5", Graph.random(2_000, 0.5, 1_000_000, false, 1));
        dijkstra("희소 그래프 n=200,000, 정점당 5간선", Graph.randomSparse(200_000, 5, 1_000_000, false, 2));
        failover(200_000);
        System.out.println("(sink=" + (sink & 1) + ")");
    }

    private static void dijkstra(String title, Graph g) {
        Dijkstra.Result r = Dijkstra.shortestPaths(g, 0);
        long[] pqCounts = new long[2];
        long[] expected = binaryHeapDijkstra(g, 0, pqCounts);
        if (!Arrays.equals(expected, r.dist)) throw new AssertionError("거리 불일치");

        double fib = median(() -> sink += Dijkstra.shortestPaths(g, 0).dist[g.vertexCount() - 1]);
        double bin = median(() -> sink += binaryHeapDijkstra(g, 0, new long[2])[g.vertexCount() - 1]);
        System.out.println("[Dijkstra] " + title + "  (간선 " + g.edgeCount() + ")");
        System.out.println("  Fibonacci heap : " + r.stats);
        System.out.println("  binary heap    : push " + pqCounts[0] + ", poll " + pqCounts[1]
                + " (그중 낡은 항목 " + (pqCounts[1] - g.vertexCount()) + "개 버림)");
        System.out.printf(Locale.ROOT, "  시간 Fibonacci %.1f ms / binary %.1f ms  (Fib/binary = %.2f)%n%n", fib, bin, fib / bin);
    }

    private static void failover(int n) {
        Random rnd = new Random(3);
        long[] keysA = new long[n];
        long[] keysB = new long[n];
        for (int i = 0; i < n; i++) {
            keysA[i] = rnd.nextInt(1_000_000);
            keysB[i] = rnd.nextInt(1_000_000);
        }
        double fib = medianWithSetup(() -> {
            MeldableFibonacciHeap<Integer> a = new MeldableFibonacciHeap<>("A");
            MeldableFibonacciHeap<Integer> b = new MeldableFibonacciHeap<>("B");
            for (int i = 0; i < n; i++) {
                a.insert(i, keysA[i]);
                b.insert(i, keysB[i]);
            }
            a.extractMin();
            b.extractMin();
            long t0 = System.nanoTime();
            a.meld(b);
            long t = System.nanoTime() - t0;
            sink += a.size();
            return t;
        });
        double bin = medianWithSetup(() -> {
            PriorityQueue<Long> a = new PriorityQueue<>();
            PriorityQueue<Long> b = new PriorityQueue<>();
            for (int i = 0; i < n; i++) {
                a.add(keysA[i]);
                b.add(keysB[i]);
            }
            long t0 = System.nanoTime();
            a.addAll(b);
            b.clear();
            long t = System.nanoTime() - t0;
            sink += a.size();
            return t;
        });
        System.out.println("[스케줄러 장애 이관] 대기열 " + n + "건 + " + n + "건 병합");
        System.out.printf(Locale.ROOT, "  Fibonacci meld %.4f ms / PriorityQueue.addAll %.1f ms%n%n", fib, bin);
    }

    // ------------------------------------------------------------------ 측정 도우미

    private interface Timed { long run(); }

    private static double median(Runnable r) {
        return medianWithSetup(() -> {
            long t0 = System.nanoTime();
            r.run();
            return System.nanoTime() - t0;
        });
    }

    private static double medianWithSetup(Timed t) {
        for (int i = 0; i < 3; i++) t.run();
        long[] times = new long[7];
        for (int i = 0; i < times.length; i++) times[i] = t.run();
        Arrays.sort(times);
        return times[times.length / 2] / 1e6;
    }

    /** counts[0] = push 수, counts[1] = poll 수 */
    static long[] binaryHeapDijkstra(Graph g, int s, long[] counts) {
        int n = g.vertexCount();
        long[] d = new long[n];
        Arrays.fill(d, Dijkstra.INF);
        boolean[] done = new boolean[n];
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        d[s] = 0;
        pq.add(new long[]{0, s});
        counts[0]++;
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            counts[1]++;
            int u = (int) top[1];
            if (done[u]) continue;
            done[u] = true;
            for (int e = g.firstEdge(u); e != -1; e = g.nextEdge(e)) {
                int v = g.target(e);
                long nd = d[u] + g.weight(e);
                if (nd < d[v]) {
                    d[v] = nd;
                    pq.add(new long[]{nd, v});
                    counts[0]++;
                }
            }
        }
        return d;
    }
}
