import java.util.ArrayList;
import java.util.List;

/**
 * Fibonacci heap 응용 사례 데모.
 *   케이스 1: Dijkstra — 물류센터에서 각 배송지까지 최단 시간 경로   (IndexedFibonacciHeap)
 *   케이스 2: 멀티 서버 작업 스케줄러 — 마감순 실행, 긴급 처리, 취소, 서버 장애 시 대기열 이관 (MeldableFibonacciHeap)
 *   케이스 3: Prim — 캠퍼스 건물 광케이블 최소 비용 연결          (IndexedFibonacciHeap)
 *
 * 실행: javac -encoding UTF-8 -d out src/*.java && java -cp out FibonacciApplications
 */
public class FibonacciApplications {

    public static void main(String[] args) {
        deliveryRoutes();
        jobScheduler();
        campusNetwork();
    }

    // ------------------------------------------------------------------ 케이스 1

    static final String[] PLACES = {"물류센터", "시청", "역 광장", "대학가", "시장", "공단", "항구", "신도시"};

    static Graph deliveryMap() {
        Graph g = new Graph(PLACES.length);
        int[][] roads = {               // {from, to, 이동 시간(분)} — 일방통행 포함
                {0, 1, 7}, {0, 2, 9}, {0, 5, 14},
                {1, 2, 10}, {1, 3, 15},
                {2, 3, 11}, {2, 5, 2},
                {3, 4, 6}, {4, 6, 9}, {5, 4, 9},
                {5, 6, 20}, {6, 7, 4}, {4, 7, 18},
                {3, 1, 4}, {7, 6, 3},
        };
        for (int[] r : roads) g.addEdge(r[0], r[1], r[2]);
        return g;
    }

    static void deliveryRoutes() {
        System.out.println("=== 케이스 1. Dijkstra: 물류센터 → 배송지 최단 시간 ===");
        Dijkstra.Result r = Dijkstra.shortestPaths(deliveryMap(), 0);
        for (int t = 1; t < PLACES.length; t++) {
            List<String> names = new ArrayList<>();
            for (int v : r.path(t)) names.add(PLACES[v]);
            System.out.printf("  %-4s %3d분  %s%n", PLACES[t], r.dist[t], String.join(" → ", names));
        }
        System.out.println("  힙 연산: " + r.stats);
        System.out.println();
    }

    // ------------------------------------------------------------------ 케이스 2

    static void jobScheduler() {
        System.out.println("=== 케이스 2. 멀티 서버 작업 스케줄러 (마감 빠른 순) ===");
        JobScheduler s = new JobScheduler();
        s.addServer("server-A");
        s.addServer("server-B");

        JobScheduler.Job backup = s.submit("server-A", "DB 백업", JobScheduler.time(10, 0));
        JobScheduler.Job logs = s.submit("server-A", "로그 정리", JobScheduler.time(11, 30));
        s.submit("server-A", "일일 리포트", JobScheduler.time(9, 30));
        JobScheduler.Job mail = s.submit("server-A", "알림 메일", JobScheduler.time(12, 0));
        s.submit("server-B", "결제 정산", JobScheduler.time(9, 45));
        JobScheduler.Job image = s.submit("server-B", "이미지 변환", JobScheduler.time(13, 0));
        s.submit("server-B", "캐시 갱신", JobScheduler.time(10, 15));
        System.out.println("  제출: A " + s.queued("server-A") + "건, B " + s.queued("server-B") + "건");

        System.out.println("  A 실행          → " + s.runNext("server-A"));
        s.escalate(mail, JobScheduler.time(9, 40));
        System.out.println("  긴급 처리        : 알림 메일 마감 12:00 → 09:40  (decreaseKey)");
        s.postpone(backup, JobScheduler.time(14, 0));
        System.out.println("  마감 연기        : DB 백업 10:00 → 14:00  (delete + 재삽입)");
        s.cancel(logs);
        System.out.println("  취소            : 로그 정리  (delete)");

        s.failover("server-A", "server-B");
        System.out.println("  server-A 장애 → 대기열을 server-B 로 이관 (meld O(1)), 이제 B " + s.queued("server-B") + "건");
        System.out.println("  DB 백업 의 현재 서버: " + s.serverOf(backup));

        List<String> order = new ArrayList<>();
        for (JobScheduler.Job j = s.runNext("server-B"); j != null; j = s.runNext("server-B")) order.add(j.toString());
        System.out.println("  B 실행 순서      → " + String.join(", ", order));

        try {
            s.escalate(logs, JobScheduler.time(9, 0));
        } catch (IllegalArgumentException e) {
            System.out.println("  취소된 작업 긴급 처리 시도 → 거부: " + e.getMessage());
        }
        try {
            s.cancel(image);
        } catch (IllegalArgumentException e) {
            System.out.println("  실행 끝난 작업 취소 시도   → 거부: " + e.getMessage());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------ 케이스 3

    static final String[] BUILDINGS = {"본관", "도서관", "공학관", "학생회관", "기숙사", "체육관", "연구동"};

    static void campusNetwork() {
        System.out.println("=== 케이스 3. Prim: 캠퍼스 건물 광케이블 최소 비용 연결 ===");
        Graph g = new Graph(BUILDINGS.length);
        int[][] ducts = {               // {건물, 건물, 공사비(백만 원)}
                {0, 1, 4}, {0, 2, 8}, {1, 2, 11}, {1, 3, 8},
                {2, 4, 7}, {2, 5, 1}, {3, 4, 2}, {3, 6, 4},
                {4, 5, 6}, {5, 6, 2}, {4, 6, 14}, {0, 6, 9},
        };
        for (int[] d : ducts) g.addUndirectedEdge(d[0], d[1], d[2]);
        PrimMST.Result r = PrimMST.minimumSpanningForest(g);
        for (PrimMST.Edge e : r.edges) {
            System.out.printf("  %s — %s  %d%n", BUILDINGS[e.u], BUILDINGS[e.v], e.weight);
        }
        System.out.println("  총 공사비 " + r.totalWeight + " (간선 " + r.edges.size() + "개, 구성 요소 " + r.components + ")");
        System.out.println("  힙 연산: " + r.stats);
    }
}
