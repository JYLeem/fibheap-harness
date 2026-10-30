import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * [기법 8] 실패 시퀀스 축소기.
 *
 * 1단계 ddmin(delta debugging): 연산 덩어리를 지워 가며 여전히 "같은 signature 로" 실패하는 가장 짧은 시퀀스를 찾는다.
 * 2단계 1-최소화: 연산을 하나씩 지워 보고, 지워도 실패하면 제거한다.
 * 3단계 값 단순화: 힙 번호 → 0, 키·선택자·감소폭 → 0, 1, 절반, 1 감소 등 "더 단순한" 값으로 바꿔 본다.
 * 위 과정을 크기가 더 줄지 않을 때까지 반복한다.
 *
 * HeapOp 가 선택자 기반이라 어떤 부분 시퀀스도 실행 가능하므로, 축소 중에 "잘못된 시퀀스" 가 생기지 않는다.
 */
final class SequenceMinimizer {

    private SequenceMinimizer() {}

    /** 같은 signature 로 실패하는 최소 시퀀스. 재현되지 않으면 입력을 그대로 반환 */
    static List<HeapOp> minimize(List<HeapOp> failing, int heapCount, Supplier<HeapSubject> factory, String signature) {
        return minimize(failing, candidate -> failsWith(candidate, heapCount, factory, signature));
    }

    /** STRICT 설정으로 실행했을 때의 실패 signature. 실패하지 않으면 null */
    static String strictSignature(List<HeapOp> ops, int heapCount, Supplier<HeapSubject> factory) {
        try {
            HeapHarness.run(ops, heapCount, factory, HeapHarness.Config.STRICT);
            return null;
        } catch (HeapHarness.Failure f) {
            return f.signature;
        }
    }

    static boolean failsWith(List<HeapOp> ops, int heapCount, Supplier<HeapSubject> factory, String signature) {
        return signature.equals(strictSignature(ops, heapCount, factory));
    }

    static <T> List<T> minimize(List<T> input, Predicate<List<T>> fails) {
        if (!fails.test(input)) return input;
        List<T> cur = new ArrayList<>(input);
        while (true) {
            int before = cur.size();
            cur = ddmin(cur, fails);
            cur = removeSingles(cur, fails);
            if (cur.size() >= before) break;
        }
        return cur;
    }

    static List<HeapOp> minimizeWithValues(List<HeapOp> input, Predicate<List<HeapOp>> fails) {
        List<HeapOp> cur = minimize(input, fails);
        while (true) {
            long before = cost(cur);
            cur = simplifyValues(cur, fails);
            cur = minimize(cur, fails);
            if (cost(cur) >= before) break;
        }
        return cur;
    }

    static List<HeapOp> minimizeWithValues(List<HeapOp> failing, int heapCount, Supplier<HeapSubject> factory,
                                           String signature) {
        return minimizeWithValues(failing, c -> failsWith(c, heapCount, factory, signature));
    }

    private static <T> List<T> ddmin(List<T> input, Predicate<List<T>> fails) {
        List<T> cur = input;
        int n = 2;
        while (cur.size() >= 2) {
            int chunk = (int) Math.ceil(cur.size() / (double) n);
            boolean reduced = false;
            for (int start = 0; start < cur.size(); start += chunk) {
                List<T> complement = new ArrayList<>(cur.subList(0, start));
                complement.addAll(cur.subList(Math.min(cur.size(), start + chunk), cur.size()));
                if (!complement.isEmpty() && fails.test(complement)) {
                    cur = complement;
                    n = Math.max(n - 1, 2);
                    reduced = true;
                    break;
                }
            }
            if (!reduced) {
                if (n >= cur.size()) break;
                n = Math.min(n * 2, cur.size());
            }
        }
        return cur;
    }

    private static <T> List<T> removeSingles(List<T> input, Predicate<List<T>> fails) {
        List<T> cur = new ArrayList<>(input);
        for (int i = cur.size() - 1; i >= 0 && cur.size() > 1; i--) {
            List<T> candidate = new ArrayList<>(cur);
            candidate.remove(i);
            if (fails.test(candidate)) cur = candidate;
        }
        return cur;
    }

    private static List<HeapOp> simplifyValues(List<HeapOp> input, Predicate<List<HeapOp>> fails) {
        List<HeapOp> best = new ArrayList<>(input);
        for (int i = 0; i < best.size(); i++) {
            for (HeapOp candidate : simplerVariants(best.get(i))) {
                List<HeapOp> trial = new ArrayList<>(best);
                trial.set(i, candidate);
                if (fails.test(trial)) {
                    best = trial;
                    i--;            // 같은 위치를 더 단순하게 만들 수 있는지 다시 시도
                    break;
                }
            }
        }
        return best;
    }

    /** cost 가 엄격히 작은 변형만 만든다 (종료 보장) */
    private static List<HeapOp> simplerVariants(HeapOp op) {
        List<HeapOp> out = new ArrayList<>();
        long base = cost(op);
        for (int h : simpler(op.heap)) add(out, op.with(h, op.a, op.b), base);
        if (op.kind.args >= 1) for (int a : simpler(op.a)) add(out, op.with(op.heap, a, op.b), base);
        if (op.kind.args >= 2) for (int b : simpler(op.b)) add(out, op.with(op.heap, op.a, b), base);
        return out;
    }

    private static void add(List<HeapOp> out, HeapOp candidate, long base) {
        if (cost(candidate) < base && !out.contains(candidate)) out.add(candidate);
    }

    private static int[] simpler(int v) {
        return new int[]{0, 1, -1, v / 2, v - Integer.signum(v)};
    }

    private static long cost(HeapOp op) {
        return Math.abs((long) op.heap) + Math.abs((long) op.a) + Math.abs((long) op.b);
    }

    static long cost(List<HeapOp> ops) {
        long c = (long) ops.size() << 40;
        for (HeapOp op : ops) c += Math.min(cost(op), 1L << 33);
        return c;
    }
}
