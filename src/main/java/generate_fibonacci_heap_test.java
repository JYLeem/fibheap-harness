import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * data.txt에 저장된 Fibonacci heap 데이터셋을 읽어,
 * 해당 힙들에 대해 수행할 테스트 연산을 test.txt에 저장한다.
 *
 * 연산 형식:
 *   push h x              : h번 힙에 x 삽입
 *   pop h                 : h번 힙에서 최솟값 제거 및 출력
 *   top h                 : h번 힙의 최솟값 출력
 *   decrease_key h old new: h번 힙의 old 값을 new(< old)로 변경
 *   union a b             : a번 힙과 b번 힙을 결합하여 a번에 저장 (b번은 빈 힙이 됨)
 *
 * push, pop, top, decrease_key, union을 각각 OPS_PER_TYPE(=3)번씩, 무작위 순서로 생성한다.
 * 내부적으로 힙 상태를 시뮬레이션하여 모든 연산이 유효하도록 보장한다.
 *   - pop / top / decrease_key 는 비어 있지 않은 힙에만 수행
 *   - decrease_key 의 old 값은 그 힙 안에서 유일한 값만 선택 (값으로 노드를 찾으므로)
 *   - decrease_key 의 new 값은 old 보다 작고 그 힙에 없는 값
 *   - push 하는 값은 그 힙에 없는 값
 *   - union 은 서로 다른 두 개의 비어 있지 않은 힙에 대해 수행
 *
 * 사용법 (IntelliJ: Run > Edit Configurations > Program arguments):
 *   [입력파일] [출력파일] [seed]   기본값: data.txt test.txt (현재 시각)
 * data.txt 는 프로젝트 루트(작업 디렉터리)에 두면 된다.
 */
public class generate_fibonacci_heap_test {

    private static final int OPS_PER_TYPE = 3;
    private static final int PUSH_MIN = 1;
    private static final int PUSH_MAX = 1000;

    private enum Op { PUSH, POP, TOP, DECREASE_KEY, UNION }

    /** 각 힙을 값 -> 개수 형태의 multiset으로 시뮬레이션 */
    private static List<TreeMap<Integer, Integer>> heaps;
    private static Random random;

    public static void main(String[] args) {
        String inputFile  = args.length > 0 ? args[0] : "data.txt";
        String outputFile = args.length > 1 ? args[1] : "test.txt";
        long seed         = args.length > 2 ? Long.parseLong(args[2]) : System.currentTimeMillis();

        random = new Random(seed);

        try {
            heaps = readHeaps(inputFile);
        } catch (FileNotFoundException e) {
            System.err.println("입력 파일을 찾을 수 없습니다: " + new File(inputFile).getAbsolutePath());
            return;
        }

        List<String> operations;
        try {
            operations = generateOperations();
        } catch (IllegalStateException e) {
            System.err.println("연산 생성 실패: " + e.getMessage());
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (String op : operations) {
                writer.write(op);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("파일 저장 실패: " + e.getMessage());
            return;
        }

        System.out.println(outputFile + " 생성 완료 (연산 " + operations.size() + "개, seed=" + seed + ")");
        operations.forEach(System.out::println);
    }

    // ------------------------------------------------------------------ 입력

    private static List<TreeMap<Integer, Integer>> readHeaps(String fileName) throws FileNotFoundException {
        List<TreeMap<Integer, Integer>> result = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(fileName))) {
            int m = sc.nextInt();
            for (int i = 0; i < m; i++) {
                int n = sc.nextInt();
                TreeMap<Integer, Integer> heap = new TreeMap<>();
                for (int j = 0; j < n; j++) {
                    heap.merge(sc.nextInt(), 1, Integer::sum);
                }
                result.add(heap);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 연산 생성

    private static List<String> generateOperations() {
        Map<Op, Integer> remaining = new EnumMap<>(Op.class);
        for (Op op : Op.values()) remaining.put(op, OPS_PER_TYPE);

        List<String> operations = new ArrayList<>();
        int total = Op.values().length * OPS_PER_TYPE;

        while (operations.size() < total) {
            // 아직 횟수가 남았고, 현재 상태에서 수행 가능한 연산 중 하나를 무작위로 선택
            List<Op> candidates = new ArrayList<>();
            for (Op op : Op.values()) {
                if (remaining.get(op) > 0 && isFeasible(op)) candidates.add(op);
            }
            if (candidates.isEmpty()) {
                throw new IllegalStateException("남은 연산 " + remaining
                        + " 을 수행할 수 있는 힙이 없습니다. data.txt의 힙 개수/크기를 늘려 주세요.");
            }
            Op op = candidates.get(random.nextInt(candidates.size()));
            operations.add(apply(op));
            remaining.merge(op, -1, Integer::sum);
        }
        return operations;
    }

    private static boolean isFeasible(Op op) {
        switch (op) {
            case PUSH:         return !heaps.isEmpty();
            case POP:
            case TOP:          return !nonEmptyHeaps().isEmpty();
            case DECREASE_KEY: return !decreasableHeaps().isEmpty();
            case UNION:        return nonEmptyHeaps().size() >= 2;
            default:           return false;
        }
    }

    private static String apply(Op op) {
        switch (op) {
            case PUSH: {
                int h = random.nextInt(heaps.size());
                int x = randomAbsentValue(heaps.get(h), PUSH_MIN, PUSH_MAX);
                heaps.get(h).merge(x, 1, Integer::sum);
                return "push " + h + " " + x;
            }
            case POP: {
                int h = pick(nonEmptyHeaps());
                removeOne(heaps.get(h), heaps.get(h).firstKey());
                return "pop " + h;
            }
            case TOP: {
                int h = pick(nonEmptyHeaps());
                return "top " + h;
            }
            case DECREASE_KEY: {
                int h = pick(decreasableHeaps());
                TreeMap<Integer, Integer> heap = heaps.get(h);
                int oldKey = pick(uniqueKeys(heap));
                int newKey = smallerAbsentValue(heap, oldKey);
                removeOne(heap, oldKey);
                heap.merge(newKey, 1, Integer::sum);
                return "decrease_key " + h + " " + oldKey + " " + newKey;
            }
            case UNION: {
                List<Integer> candidates = nonEmptyHeaps();
                int a = candidates.remove(random.nextInt(candidates.size()));
                int b = pick(candidates);
                heaps.get(b).forEach((k, c) -> heaps.get(a).merge(k, c, Integer::sum));
                heaps.get(b).clear();
                return "union " + a + " " + b;
            }
            default:
                throw new IllegalArgumentException("알 수 없는 연산: " + op);
        }
    }

    // ------------------------------------------------------------------ 보조 메서드

    private static List<Integer> nonEmptyHeaps() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < heaps.size(); i++) {
            if (!heaps.get(i).isEmpty()) list.add(i);
        }
        return list;
    }

    /** 유일한 값을 하나 이상 가진 힙 (decrease_key 대상으로 쓸 수 있는 힙) */
    private static List<Integer> decreasableHeaps() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < heaps.size(); i++) {
            if (!uniqueKeys(heaps.get(i)).isEmpty()) list.add(i);
        }
        return list;
    }

    private static List<Integer> uniqueKeys(TreeMap<Integer, Integer> heap) {
        List<Integer> keys = new ArrayList<>();
        heap.forEach((k, c) -> { if (c == 1) keys.add(k); });
        return keys;
    }

    private static int randomAbsentValue(TreeMap<Integer, Integer> heap, int min, int max) {
        int x;
        do {
            x = min + random.nextInt(max - min + 1);
        } while (heap.containsKey(x));
        return x;
    }

    /** oldKey 보다 작고 heap 에 없는 값 (대략 oldKey의 절반 범위 안에서 선택) */
    private static int smallerAbsentValue(TreeMap<Integer, Integer> heap, int oldKey) {
        int range = Math.max(1, oldKey / 2);
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = oldKey - 1 - random.nextInt(range);
            if (!heap.containsKey(x)) return x;
        }
        // 범위 안이 모두 차 있으면 아래로 내려가며 빈 값을 찾는다
        int x = oldKey - 1;
        while (heap.containsKey(x)) x--;
        return x;
    }

    private static void removeOne(TreeMap<Integer, Integer> heap, int key) {
        int c = heap.get(key);
        if (c == 1) heap.remove(key); else heap.put(key, c - 1);
    }

    private static int pick(List<Integer> list) {
        return list.get(random.nextInt(list.size()));
    }
}