import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * [기법 3·7] 재현 가능한 무작위 연산 시퀀스 생성기. 같은 (seed, length, heapCount, profile) 이면 항상 같은 시퀀스.
 *
 * 키 분포(profile)
 *   SMALL_RANGE : -20..20  → 중복 키가 매우 많음, 같은 차수 통합·동률 처리 경로 집중
 *   WIDE        : int 전체 범위
 *   EXTREMES    : MIN_VALUE, MAX_VALUE, ±1, 0 등 경계값 위주 + 일부 무작위
 *   GROWTH      : 앞쪽 40% 는 삽입 위주로 큰 트리를 만든 뒤, 감소·삭제를 몰아서 → cascading cut 유도
 *   CHAOS       : 모든 연산 균등 + 잘못된 요청(키 증가, 자기 병합, 빈 힙 삭제) 비율 높음
 */
final class OpSequenceGenerator {

    enum Profile { SMALL_RANGE, WIDE, EXTREMES, GROWTH, CHAOS }

    private static final int[] EXTREME_KEYS = {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
    };

    private OpSequenceGenerator() {}

    static List<HeapOp> generate(long seed, int length, int heapCount, Profile profile) {
        Random rnd = new Random(seed);
        List<HeapOp> ops = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            boolean growthPhase = profile == Profile.GROWTH && i < length * 0.4;
            ops.add(next(rnd, heapCount, profile, growthPhase));
        }
        return ops;
    }

    private static HeapOp next(Random rnd, int heapCount, Profile profile, boolean growthPhase) {
        int h = rnd.nextInt(heapCount);
        int[] weights = weights(profile, growthPhase); // insert, findMin, extract, decNode, decValue, meld
        int roll = rnd.nextInt(sum(weights));
        int kind = 0;
        while (roll >= weights[kind]) roll -= weights[kind++];
        switch (kind) {
            case 0: return HeapOp.insert(h, key(rnd, profile));
            case 1: return HeapOp.findMin(h);
            case 2: return HeapOp.extractMin(h);
            case 3: return HeapOp.decreaseNode(h, rnd.nextInt(1 << 20), delta(rnd, profile));
            case 4: return HeapOp.decreaseValue(h, rnd.nextInt(1 << 20), delta(rnd, profile));
            default: return HeapOp.meld(h, rnd.nextInt(heapCount));
        }
    }

    private static int[] weights(Profile profile, boolean growthPhase) {
        if (growthPhase) return new int[]{85, 2, 3, 6, 2, 2};
        switch (profile) {
            case GROWTH: return new int[]{10, 5, 20, 45, 15, 5};
            case CHAOS: return new int[]{20, 15, 20, 20, 15, 10};
            default: return new int[]{35, 10, 20, 15, 8, 7};
        }
    }

    static int key(Random rnd, Profile profile) {
        switch (profile) {
            case SMALL_RANGE: return rnd.nextInt(41) - 20;
            case EXTREMES: return rnd.nextInt(3) == 0 ? rnd.nextInt() : EXTREME_KEYS[rnd.nextInt(EXTREME_KEYS.length)];
            case WIDE: return rnd.nextInt();
            default: return rnd.nextInt(1_000_000);
        }
    }

    /** 감소폭. 대부분 작은 값, 일부 큰 값(포화 감소), 일부 음수(= 키 증가 요청, 거부되어야 함) */
    static int delta(Random rnd, Profile profile) {
        int invalidPercent = profile == Profile.CHAOS ? 15 : 3;
        int r = rnd.nextInt(100);
        if (r < invalidPercent) return -(1 + rnd.nextInt(10));
        if (r < invalidPercent + 5) return 0;
        if (r < invalidPercent + 12) return rnd.nextInt(Integer.MAX_VALUE);
        return 1 + rnd.nextInt(profile == Profile.SMALL_RANGE ? 10 : 1000);
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int x : a) s += x;
        return s;
    }
}
