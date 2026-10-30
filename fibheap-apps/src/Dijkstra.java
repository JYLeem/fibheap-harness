import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 케이스 1: 단일 출발점 최단 경로 (Dijkstra) — 배송 경로 탐색.
 *
 * 힙 사용 방식 ({@link IndexedFibonacciHeap})
 *   - 키 = 출발점에서 정점까지 지금까지 찾은 최단 거리.
 *   - extractMin: 거리가 가장 짧은 정점을 확정한다 (정점마다 1번, 총 n번).
 *   - 간선 완화로 더 짧은 거리를 찾으면 힙에 없으면 insert, 있으면 decreaseKey (간선마다 최대 1번, 총 최대 m번).
 *   → 시간 O(m + n log n). binary heap(decreaseKey O(log n))이면 O((n + m) log n).
 *     간선이 많은 그래프(m ≫ n)에서 decreaseKey 횟수가 extractMin 보다 훨씬 많아지는 것이 Fibonacci heap 을 쓰는 이유다.
 */
public final class Dijkstra {

    public static final long INF = Long.MAX_VALUE;

    /** 힙 연산 횟수 (분석용) */
    public static final class Stats {
        public long inserts;
        public long extractMins;
        public long decreaseKeys;
        public long relaxations;

        @Override public String toString() {
            return "insert " + inserts + ", extractMin " + extractMins + ", decreaseKey " + decreaseKeys
                    + " (간선 검사 " + relaxations + ")";
        }
    }

    public static final class Result {
        public final int source;
        public final long[] dist;
        public final int[] prev;
        public final Stats stats;

        Result(int source, long[] dist, int[] prev, Stats stats) {
            this.source = source;
            this.dist = dist;
            this.prev = prev;
            this.stats = stats;
        }

        public boolean reachable(int t) { return dist[t] != INF; }

        /** source → t 경로 (도달 불가면 빈 목록) */
        public List<Integer> path(int t) {
            if (!reachable(t)) return List.of();
            List<Integer> p = new ArrayList<>();
            for (int v = t; v != -1; v = prev[v]) p.add(v);
            Collections.reverse(p);
            return p;
        }
    }

    private Dijkstra() {}

    public static Result shortestPaths(Graph g, int source) {
        int n = g.vertexCount();
        if (source < 0 || source >= n) throw new IndexOutOfBoundsException("출발점 범위 밖: " + source);
        long[] dist = new long[n];
        int[] prev = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(prev, -1);
        Stats stats = new Stats();

        IndexedFibonacciHeap heap = new IndexedFibonacciHeap(n);
        dist[source] = 0;
        heap.insert(source, 0);
        stats.inserts++;

        while (!heap.isEmpty()) {
            int u = heap.extractMin();                     // u 의 최단 거리 확정
            stats.extractMins++;
            for (int e = g.firstEdge(u); e != -1; e = g.nextEdge(e)) {
                int v = g.target(e);
                if (heap.wasRemoved(v)) continue;          // 이미 확정된 정점
                stats.relaxations++;
                long nd = dist[u] + g.weight(e);
                if (nd < dist[v]) {
                    dist[v] = nd;
                    prev[v] = u;
                    if (heap.contains(v)) {
                        heap.decreaseKey(v, nd);
                        stats.decreaseKeys++;
                    } else {
                        heap.insert(v, nd);
                        stats.inserts++;
                    }
                }
            }
        }
        return new Result(source, dist, prev, stats);
    }
}
