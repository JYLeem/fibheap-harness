import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * [기법 2·3·7 공통] 연산 시퀀스 실행기.
 *
 * 여러 개의 실제 힙과 {@link ReferenceModel} 에 같은 연산을 적용하고,
 *   - 매 연산 결과를 기준 모델과 비교 (차등 테스트)
 *   - checkEvery 연산마다 모든 힙의 불변식 + 크기 + 최솟값 비교
 *   - fullCheckEvery 연산마다(그리고 마지막에) 노드 집합 · 멀티셋 전체 비교
 * 를 수행한다. 실패하면 단계 번호, 연산, 실패 서명(signature), 최근 실행 기록을 담은 {@link Failure} 를 던진다.
 *
 * signature 는 "연산종류:실패범주" 형식이며(예: DECREASE_NODE:violation:HEAP_ORDER),
 * 최소화기는 축소한 시퀀스가 같은 signature 로 실패하는지로 "같은 오류"를 판정한다.
 */
final class HeapHarness {

    static final class Config {
        final int checkEvery;
        final int fullCheckEvery;
        final int traceLimit;

        Config(int checkEvery, int fullCheckEvery, int traceLimit) {
            this.checkEvery = checkEvery;
            this.fullCheckEvery = fullCheckEvery;
            this.traceLimit = traceLimit;
        }

        /** 매 연산마다 모든 검사 (최소화·회귀·속성 테스트용) */
        static final Config STRICT = new Config(1, 1, 60);
        /** 매 연산 불변식, 50 연산마다 전체 비교 */
        static final Config DEFAULT = new Config(1, 50, 60);

        static Config sampled(int checkEvery, int fullCheckEvery) {
            return new Config(checkEvery, fullCheckEvery, 60);
        }
    }

    /** 결과 불일치 (기준 모델과 다름) */
    static final class Mismatch extends AssertionError {
        final String category;

        Mismatch(String category, String detail) {
            super("[" + category + "] " + detail);
            this.category = category;
        }
    }

    /** 시퀀스 실행 실패 */
    static final class Failure extends AssertionError {
        final int step;
        final HeapOp op;
        final String signature;
        final List<String> trace;

        Failure(int step, HeapOp op, String signature, Throwable cause, List<String> trace) {
            super("step " + step + " (" + (op == null ? "최종 검사" : op) + ") 실패 → " + cause, cause);
            this.step = step;
            this.op = op;
            this.signature = signature;
            this.trace = trace;
        }
    }

    static final class Result {
        final int executed;
        final int skipped;
        final int rejected;

        Result(int executed, int skipped, int rejected) {
            this.executed = executed;
            this.skipped = skipped;
            this.rejected = rejected;
        }
    }

    private final List<HeapSubject> heaps = new ArrayList<>();
    private final ReferenceModel model;
    private final Config cfg;
    private final Deque<String> trace = new ArrayDeque<>();
    private int skipped;
    private int rejected;

    private HeapHarness(int heapCount, Supplier<HeapSubject> factory, Config cfg) {
        for (int i = 0; i < heapCount; i++) heaps.add(factory.get());
        this.model = new ReferenceModel(heapCount);
        this.cfg = cfg;
    }

    static Result run(List<HeapOp> ops, int heapCount, Supplier<HeapSubject> factory) {
        return run(ops, heapCount, factory, Config.DEFAULT);
    }

    static Result run(List<HeapOp> ops, int heapCount, Supplier<HeapSubject> factory, Config cfg) {
        HeapHarness hh = new HeapHarness(heapCount, factory, cfg);
        for (int i = 0; i < ops.size(); i++) {
            HeapOp op = ops.get(i);
            try {
                String outcome = hh.apply(op);
                hh.log(i, op, outcome);
                if ((i + 1) % cfg.checkEvery == 0) hh.quickCheck();
                if ((i + 1) % cfg.fullCheckEvery == 0) hh.fullCheck();
            } catch (Throwable t) {
                hh.log(i, op, "실패: " + t);
                throw new Failure(i, op, signature(op, t), t, new ArrayList<>(hh.trace));
            }
        }
        try {
            hh.quickCheck();
            hh.fullCheck();
        } catch (Throwable t) {
            throw new Failure(ops.size(), null, signature(null, t), t, new ArrayList<>(hh.trace));
        }
        return new Result(ops.size(), hh.skipped, hh.rejected);
    }

    static String signature(HeapOp op, Throwable t) {
        String where = op == null ? "FINAL" : op.kind.name();
        String what;
        if (t instanceof HeapInvariantChecker.Violation) {
            what = "violation:" + ((HeapInvariantChecker.Violation) t).rule;
        } else if (t instanceof Mismatch) {
            what = "mismatch:" + ((Mismatch) t).category;
        } else {
            what = "exception:" + t.getClass().getSimpleName();
        }
        return where + ":" + what;
    }

    // ------------------------------------------------------------------ 연산 적용

    private String apply(HeapOp op) {
        int h = Math.floorMod(op.heap, heaps.size());
        HeapSubject s = heaps.get(h);
        switch (op.kind) {
            case INSERT: {
                Object node = s.insert(op.a);
                expect(node != null, "insert-handle", "insert 가 null 핸들을 반환");
                ReferenceModel.Handle hd = model.insert(h, node, op.a);
                return "핸들 " + hd;
            }
            case FIND_MIN: {
                Integer actual = s.findMin();
                expectEq("findMin", model.min(h), actual);
                return String.valueOf(actual);
            }
            case EXTRACT_MIN: {
                Integer expected = model.min(h);
                Object minNode = s.minHandle();
                Integer actual = s.extractMin();
                expectEq("extractMin", expected, actual);
                if (expected != null) {
                    ReferenceModel.Handle hd = model.handleOf(minNode);
                    expect(hd != null && hd.live && hd.owner == h, "extractMin-node",
                            "제거된 노드 " + hd + " 가 " + h + "번 힙의 살아 있는 노드가 아님");
                    expect(hd.key == expected, "extractMin-node",
                            "제거된 노드 " + hd + " 의 키가 최솟값 " + expected + " 가 아님");
                    model.extract(hd);
                }
                return String.valueOf(actual);
            }
            case DECREASE_NODE: {
                List<ReferenceModel.Handle> live = model.liveHandles(h);
                if (live.isEmpty()) {
                    skipped++;
                    return "건너뜀(빈 힙)";
                }
                ReferenceModel.Handle hd = live.get(Math.floorMod(op.a, live.size()));
                int newKey = shift(hd.key, op.b);
                return decrease(s, hd, newKey, () -> s.decreaseKey(hd.node, newKey));
            }
            case DECREASE_VALUE: {
                List<Integer> keys = model.distinctKeysSorted(h);
                if (keys.isEmpty()) {
                    skipped++;
                    return "건너뜀(빈 힙)";
                }
                int oldKey = keys.get(Math.floorMod(op.a, keys.size()));
                ReferenceModel.Handle hd = model.firstWithKey(h, oldKey);
                int newKey = shift(oldKey, op.b);
                return decrease(s, hd, newKey, () -> s.decreaseKeyByValue(oldKey, newKey));
            }
            case MELD: {
                int other = Math.floorMod(op.a, heaps.size());
                if (other == h) {
                    int before = s.size();
                    Integer min = s.findMin();
                    s.meld(s);
                    expectEq("selfMeld-size", before, s.size());
                    expectEq("selfMeld-min", min, s.findMin());
                    return "자기 자신과 병합 → 변화 없음";
                }
                s.meld(heaps.get(other));
                model.meld(h, other);
                return "병합 후 size=" + s.size();
            }
            default:
                throw new IllegalStateException("알 수 없는 연산 " + op.kind);
        }
    }

    private String decrease(HeapSubject s, ReferenceModel.Handle hd, int newKey, Runnable call) {
        if (newKey > hd.key) {
            int before = s.size();
            Integer min = s.findMin();
            try {
                call.run();
            } catch (IllegalArgumentException expected) {
                expectEq("decrease-reject-size", before, s.size());
                expectEq("decrease-reject-min", min, s.findMin());
                expect(s.keyOf(hd.node) == hd.key, "decrease-reject-key", "거부된 요청인데 " + hd + " 의 키가 바뀜");
                rejected++;
                return "거부됨(키 증가 " + hd.key + "→" + newKey + ")";
            }
            throw new Mismatch("decrease-increase", "키 증가 요청 " + hd + " → " + newKey + " 이 거부되지 않음");
        }
        int oldKey = hd.key;
        call.run();
        expect(s.keyOf(hd.node) == newKey, "decrease-target",
                "기대한 대상 " + hd + " 의 키가 " + newKey + " 가 아님 (실제 " + s.keyOf(hd.node)
                        + ") — 다른 노드가 바뀌었을 수 있음");
        model.decrease(hd, newKey);
        return hd + " 키 " + oldKey + "→" + newKey;
    }

    static int shift(int key, int delta) {
        long r = (long) key - delta;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, r));
    }

    // ------------------------------------------------------------------ 검사

    private void quickCheck() {
        for (int i = 0; i < heaps.size(); i++) {
            HeapSubject s = heaps.get(i);
            s.checkInvariants();
            expectEqAt("size", i, model.size(i), s.size());
            expectEqAt("min", i, model.min(i), s.findMin());
        }
    }

    private void fullCheck() {
        for (int i = 0; i < heaps.size(); i++) {
            HeapSubject s = heaps.get(i);
            Set<Object> reachable = s.reachableHandles();
            List<ReferenceModel.Handle> live = model.liveHandles(i);
            expect(reachable.size() == live.size(), "nodes",
                    i + "번 힙: 도달 가능한 노드 " + reachable.size() + "개, 기대 " + live.size() + "개");
            List<Integer> actualKeys = new ArrayList<>();
            for (ReferenceModel.Handle hd : live) {
                expect(reachable.contains(hd.node), "nodes",
                        hd + " 가 " + i + "번 힙에서 도달 불가능 (병합·삭제 오류)");
                expect(s.keyOf(hd.node) == hd.key, "key",
                        i + "번 힙: " + hd + " 의 실제 키 " + s.keyOf(hd.node));
                actualKeys.add(s.keyOf(hd.node));
            }
            actualKeys.sort(null);
            expectEqAt("multiset", i, model.sortedContents(i), actualKeys);
        }
    }

    private static void expect(boolean cond, String category, String detail) {
        if (!cond) throw new Mismatch(category, detail);
    }

    private static void expectEq(String category, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new Mismatch(category, "기대 " + abbreviate(expected) + ", 실제 " + abbreviate(actual));
        }
    }

    private static void expectEqAt(String category, int heap, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new Mismatch(category, heap + "번 힙: 기대 " + abbreviate(expected) + ", 실제 " + abbreviate(actual));
        }
    }

    private static String abbreviate(Object o) {
        String s = String.valueOf(o);
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private void log(int step, HeapOp op, String outcome) {
        trace.addLast(step + ": " + op + "  →  " + outcome);
        while (trace.size() > cfg.traceLimit) trace.removeFirst();
    }
}
