import java.util.Arrays;
import java.util.Random;

/**
 * 가중치 그래프 (forward-star 인접 리스트, 가중치는 0 이상의 int).
 * 간선 e: from 정점의 목록을 head[u] → next[e] → ... 로 순회, 도착 정점 to[e], 가중치 weight[e].
 */
public final class Graph {

    private final int n;
    private final int[] head;
    private int[] next = new int[16];
    private int[] to = new int[16];
    private int[] weight = new int[16];
    private int m;

    public Graph(int vertexCount) {
        if (vertexCount < 0) throw new IllegalArgumentException("정점 수는 0 이상: " + vertexCount);
        this.n = vertexCount;
        this.head = new int[vertexCount];
        Arrays.fill(head, -1);
    }

    public int vertexCount() { return n; }

    /** 방향 간선 수 (무방향 간선은 2개로 센다) */
    public int edgeCount() { return m; }

    public void addEdge(int u, int v, int w) {
        if (u < 0 || u >= n || v < 0 || v >= n) throw new IndexOutOfBoundsException("정점 범위 밖: " + u + "→" + v);
        if (w < 0) throw new IllegalArgumentException("가중치는 0 이상이어야 합니다: " + w);
        if (m == to.length) {
            int cap = m * 2;
            next = Arrays.copyOf(next, cap);
            to = Arrays.copyOf(to, cap);
            weight = Arrays.copyOf(weight, cap);
        }
        to[m] = v;
        weight[m] = w;
        next[m] = head[u];
        head[u] = m++;
    }

    public void addUndirectedEdge(int u, int v, int w) {
        addEdge(u, v, w);
        addEdge(v, u, w);
    }

    int firstEdge(int u) { return head[u]; }

    int nextEdge(int e) { return next[e]; }

    int target(int e) { return to[e]; }

    int weight(int e) { return weight[e]; }

    /** 정점 쌍마다 확률 p 로 간선(가중치 0..maxWeight)을 만든 무작위 그래프 */
    public static Graph random(int n, double p, int maxWeight, boolean undirected, long seed) {
        Random rnd = new Random(seed);
        Graph g = new Graph(n);
        for (int u = 0; u < n; u++) {
            for (int v = undirected ? u + 1 : 0; v < n; v++) {
                if (u == v || rnd.nextDouble() >= p) continue;
                int w = rnd.nextInt(maxWeight + 1);
                if (undirected) g.addUndirectedEdge(u, v, w);
                else g.addEdge(u, v, w);
            }
        }
        return g;
    }

    /** 정점마다 평균 degree 개의 무작위 간선을 가진 희소 그래프 (O(n·degree) 생성) */
    public static Graph randomSparse(int n, int degree, int maxWeight, boolean undirected, long seed) {
        Random rnd = new Random(seed);
        Graph g = new Graph(n);
        for (int u = 0; u < n; u++) {
            for (int i = 0; i < degree; i++) {
                int v = rnd.nextInt(n);
                if (v == u) continue;
                int w = rnd.nextInt(maxWeight + 1);
                if (undirected) g.addUndirectedEdge(u, v, w);
                else g.addEdge(u, v, w);
            }
        }
        return g;
    }
}
