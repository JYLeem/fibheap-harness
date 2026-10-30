import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 케이스 2: 멀티 서버 작업 스케줄러 — 마감 시각이 빠른 작업부터 실행.
 *
 * 서버마다 대기열 하나({@link MeldableFibonacciHeap}, 키 = 마감 시각(분))를 둔다.
 *   submit    → insert                    O(1)
 *   runNext   → extractMin                O(log n)
 *   escalate  → decreaseKey (마감 앞당김)  O(1)
 *   postpone  → updateKey (마감 늦춤)      O(log n)
 *   cancel    → delete                    O(log n)
 *   failover  → meld (장애 서버의 대기열 전체를 다른 서버로) O(1)
 * binary heap 이라면 failover 가 O(n log n) 또는 재구성 O(n), cancel 은 위치 추적 없이는 O(n) 이다.
 * 같은 마감이면 먼저 제출된 작업이 먼저 실행된다.
 */
public final class JobScheduler {

    public enum Status { QUEUED, DONE, CANCELLED }

    public static final class Job {
        public final int id;
        public final String name;
        private MeldableFibonacciHeap.Handle<Job> handle;
        private Status status = Status.QUEUED;
        private String ranOn;

        private Job(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public long deadline() { return handle.key(); }

        public Status status() { return status; }

        /** 실행된 서버 (실행 전이면 null) */
        public String ranOn() { return ranOn; }

        @Override public String toString() { return name + "(" + formatTime(deadline()) + ")"; }
    }

    private final Map<String, MeldableFibonacciHeap<Job>> servers = new LinkedHashMap<>();
    private int nextId;

    public void addServer(String name) {
        if (servers.containsKey(name)) throw new IllegalArgumentException("이미 있는 서버: " + name);
        servers.put(name, new MeldableFibonacciHeap<>(name));
    }

    public Job submit(String server, String name, long deadline) {
        Job job = new Job(nextId++, name);
        job.handle = queue(server).insert(job, deadline);
        return job;
    }

    /** 다음에 실행할 작업 (대기열이 비면 null) */
    public Job peek(String server) {
        MeldableFibonacciHeap.Handle<Job> h = queue(server).peek();
        return h == null ? null : h.value();
    }

    /** 마감이 가장 빠른 작업을 꺼내 실행 처리 (대기열이 비면 null) */
    public Job runNext(String server) {
        MeldableFibonacciHeap.Handle<Job> h = queue(server).extractMin();
        if (h == null) return null;
        Job job = h.value();
        job.status = Status.DONE;
        job.ranOn = server;
        return job;
    }

    /** 마감을 앞당긴다. 이미 실행·취소된 작업이거나 마감을 늦추려 하면 IllegalArgumentException */
    public void escalate(Job job, long newDeadline) {
        queueOf(job).decreaseKey(job.handle, newDeadline);
    }

    /** 마감을 늦춘다 (같은 마감 작업들 중에서는 맨 뒤로 간다) */
    public void postpone(Job job, long newDeadline) {
        if (newDeadline < job.deadline()) {
            throw new IllegalArgumentException("postpone 은 마감을 늦출 때만 씁니다: " + job);
        }
        queueOf(job).updateKey(job.handle, newDeadline);
    }

    public void cancel(Job job) {
        queueOf(job).delete(job.handle);
        job.status = Status.CANCELLED;
    }

    /** 장애 서버의 대기열 전체를 target 으로 옮기고 장애 서버를 제거한다 (O(1)) */
    public void failover(String failed, String target) {
        if (failed.equals(target)) throw new IllegalArgumentException("같은 서버로 넘길 수 없습니다: " + failed);
        MeldableFibonacciHeap<Job> from = queue(failed);
        queue(target).meld(from);
        servers.remove(failed);
    }

    /** 작업이 현재 대기 중인 서버 (대기 중이 아니면 null) */
    public String serverOf(Job job) {
        MeldableFibonacciHeap<Job> heap = MeldableFibonacciHeap.heapOf(job.handle);
        return heap == null ? null : heap.name();
    }

    public int queued(String server) { return queue(server).size(); }

    public Iterable<String> serverNames() { return servers.keySet(); }

    private MeldableFibonacciHeap<Job> queue(String server) {
        MeldableFibonacciHeap<Job> q = servers.get(server);
        if (q == null) throw new NoSuchElementException("없는 서버: " + server);
        return q;
    }

    /** 작업이 들어 있는 대기열. 대기 중이 아니면 IllegalArgumentException */
    private MeldableFibonacciHeap<Job> queueOf(Job job) {
        MeldableFibonacciHeap<Job> heap = MeldableFibonacciHeap.heapOf(job.handle);
        if (heap == null) {
            throw new IllegalArgumentException(job.name + " 는 대기 중이 아닙니다 (" + job.status + ")");
        }
        return heap;
    }

    static String formatTime(long minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    static long time(int hour, int minute) { return hour * 60L + minute; }
}
