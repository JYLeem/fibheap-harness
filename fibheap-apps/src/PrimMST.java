import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 케이스 3: 최소 신장 트리 (Prim) — 건물 사이 통신망을 최소 비용으로 연결.
 *
 * 힙 사용 방식 ({@link IndexedFibonacciHeap})
 *   - 키 = 정점을 현재 트리에 연결하는 가장 싼 간선의 비용.
 *   - extractMin: 연결 비용이 가장 싼 정점을 트리에 추가.
 *   - 새로 추가된 정점의 간선이 더 싸면 decreaseKey.
 *   → O(m + n log n). Dijkstra 와 같은 구조라 같은 힙을 그대로 쓴다.
 * 그래프가 끊어져 있으면 구성 요소마다 트리를 만든다 (최소 신장 숲).
 */
public final class PrimMST {

    public static final class Edge {
        public final int u;
        public final int v;
        public final long weight;

        Edge(int u, int v, long weight) {
            this.u = u;
            this.v = v;
            this.weight = weight;
        }

        @Override public String toString() { return u + "-" + v + "(" + weight + ")"; }
    }

    public static final class Result {
        public final List<Edge> edges;
        public final long totalWeight;
        public final int components;
        public final Dijkstra.Stats stats;

        Result(List<Edge> edges, long totalWeight, int components, Dijkstra.Stats stats) {
            this.edges = edges;
            this.totalWeight = totalWeight;
            this.components = components;
            this.stats = stats;
        }
    }

    private PrimMST() {}

    /** g 는 무방향 그래프여야 한다 (addUndirectedEdge 로 구성) */
    public static Result minimumSpanningForest(Graph g) {
        int n = g.vertexCount();
        long[] best = new long[n];
        int[] link = new int[n];
        Arrays.fill(best, Long.MAX_VALUE);
        Arrays.fill(link, -1);
        IndexedFibonacciHeap heap = new IndexedFibonacciHeap(n);
        Dijkstra.Stats stats = new Dijkstra.Stats();
        List<Edge> edges = new ArrayList<>();
        long total = 0;
        int components = 0;

        for (int start = 0; start < n; start++) {
            if (heap.wasRemoved(start)) continue;           // 이미 다른 트리에 포함
            components++;
            best[start] = 0;
            heap.insert(start, 0);
            stats.inserts++;
            while (!heap.isEmpty()) {
                int u = heap.extractMin();
                stats.extractMins++;
                if (link[u] != -1) {
                    edges.add(new Edge(link[u], u, best[u]));
                    total += best[u];
                }
                for (int e = g.firstEdge(u); e != -1; e = g.nextEdge(e)) {
                    int v = g.target(e);
                    if (heap.wasRemoved(v)) continue;
                    stats.relaxations++;
                    int w = g.weight(e);
                    if (w < best[v]) {
                        best[v] = w;
                        link[v] = u;
                        if (heap.contains(v)) {
                            heap.decreaseKey(v, w);
                            stats.decreaseKeys++;
                        } else {
                            heap.insert(v, w);
                            stats.inserts++;
                        }
                    }
                }
            }
        }
        return new Result(edges, total, components, stats);
    }
}
