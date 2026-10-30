import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * [기법 7-b] 파일 명령어 퍼즈 테스트의 기준 해석기.
 *
 * data.txt / test.txt 명세를 PriorityQueue 로 독립 구현한다 (Main 코드를 호출하지 않음).
 *   push h x / pop h / top h / decrease_key h old new / union a b
 *   결과: OK | 값 | EMPTY | ERROR (사유 문자열은 비교하지 않고 "ERROR " 접두어만 비교)
 */
final class CommandOracle {

    private final List<PriorityQueue<Integer>> heaps = new ArrayList<>();

    CommandOracle(List<List<Integer>> initial) {
        for (List<Integer> keys : initial) heaps.add(new PriorityQueue<>(keys));
    }

    /** data.txt 토큰 해석. 명세 위반이면 null */
    static List<List<Integer>> parseData(String content) {
        List<String> tokens = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            for (String tok : line.replace("﻿", "").trim().split("\\s+")) {
                if (!tok.isEmpty()) tokens.add(tok);
            }
        }
        int[] pos = {0};
        Integer m = next(tokens, pos);
        if (m == null || m < 0) return null;
        List<List<Integer>> heaps = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            Integer n = next(tokens, pos);
            if (n == null || n < 0) return null;
            List<Integer> keys = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                Integer k = next(tokens, pos);
                if (k == null) return null;
                keys.add(k);
            }
            heaps.add(keys);
        }
        return pos[0] == tokens.size() ? heaps : null;
    }

    private static Integer next(List<String> tokens, int[] pos) {
        if (pos[0] >= tokens.size()) return null;
        try {
            return Integer.parseInt(tokens.get(pos[0]++));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 명령 한 줄의 기대 결과. 빈 줄이면 null (결과 줄이 생기지 않아야 함) */
    String apply(String raw) {
        String line = raw.replace("﻿", "").trim();
        if (line.isEmpty()) return null;
        String[] t = line.split("\\s+");
        try {
            switch (t[0]) {
                case "push":
                    args(t, 2);
                    heap(t[1]).add(Integer.parseInt(t[2]));
                    return "OK";
                case "pop": {
                    args(t, 1);
                    Integer v = heap(t[1]).poll();
                    return v == null ? "EMPTY" : String.valueOf(v);
                }
                case "top": {
                    args(t, 1);
                    Integer v = heap(t[1]).peek();
                    return v == null ? "EMPTY" : String.valueOf(v);
                }
                case "decrease_key": {
                    args(t, 3);
                    PriorityQueue<Integer> q = heap(t[1]);
                    int oldKey = Integer.parseInt(t[2]);
                    int newKey = Integer.parseInt(t[3]);
                    if (!q.contains(oldKey) || newKey > oldKey) return "ERROR";
                    q.remove(oldKey);
                    q.add(newKey);
                    return "OK";
                }
                case "union": {
                    args(t, 2);
                    int a = Integer.parseInt(t[1]);
                    int b = Integer.parseInt(t[2]);
                    if (a == b) return "ERROR";
                    PriorityQueue<Integer> qa = heap(t[1]);
                    PriorityQueue<Integer> qb = heap(t[2]);
                    qa.addAll(qb);
                    qb.clear();
                    return "OK";
                }
                default:
                    return "ERROR";
            }
        } catch (RuntimeException e) {           // 인자 수, 정수 아님, 힙 번호 범위
            return "ERROR";
        }
    }

    private static void args(String[] t, int n) {
        if (t.length - 1 != n) throw new IllegalArgumentException("args");
    }

    private PriorityQueue<Integer> heap(String s) {
        int h = Integer.parseInt(s);
        if (h < 0 || h >= heaps.size()) throw new IndexOutOfBoundsException("heap");
        return heaps.get(h);
    }

    static boolean matches(String expected, String actual) {
        if (expected.equals("ERROR")) return actual.startsWith("ERROR ");
        return expected.equals(actual);
    }
}
