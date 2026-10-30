import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 하네스가 실행하는 연산 하나 (불변 값 객체).
 *
 * 노드·키를 직접 지정하지 않고 "선택자(selector)"로 지정하는 것이 핵심이다.
 * 실행 시점의 살아 있는 노드 목록에서 selector mod 개수로 대상을 고르므로,
 * 연산 일부를 지우거나 값을 바꿔도(최소화, jqwik 축소) 항상 실행 가능한 시퀀스로 남는다.
 *
 * 텍스트 형식 (회귀 파일과 실패 리포트에 그대로 사용):
 *   insert        h key
 *   findMin       h
 *   extractMin    h
 *   decreaseNode  h selector delta   살아있는 노드[selector] 의 키를 key - delta 로 (delta < 0 이면 '증가 요청' → 거부 기대)
 *   decreaseValue h selector delta   서로 다른 키 중 [selector] 번째 키를 값으로 지정해 감소
 *   meld          h other            other 를 h 에 병합 (h == other 이면 자기 자신과의 병합)
 */
final class HeapOp {

    enum Kind {
        INSERT("insert", 1), FIND_MIN("findMin", 0), EXTRACT_MIN("extractMin", 0),
        DECREASE_NODE("decreaseNode", 2), DECREASE_VALUE("decreaseValue", 2), MELD("meld", 1);

        final String text;
        final int args;

        Kind(String text, int args) {
            this.text = text;
            this.args = args;
        }

        static Kind of(String text) {
            for (Kind k : values()) if (k.text.equals(text)) return k;
            throw new IllegalArgumentException("알 수 없는 연산: " + text);
        }
    }

    final Kind kind;
    final int heap;
    final int a;
    final int b;

    private HeapOp(Kind kind, int heap, int a, int b) {
        this.kind = kind;
        this.heap = heap;
        this.a = a;
        this.b = b;
    }

    static HeapOp insert(int heap, int key) { return new HeapOp(Kind.INSERT, heap, key, 0); }
    static HeapOp findMin(int heap) { return new HeapOp(Kind.FIND_MIN, heap, 0, 0); }
    static HeapOp extractMin(int heap) { return new HeapOp(Kind.EXTRACT_MIN, heap, 0, 0); }
    static HeapOp decreaseNode(int heap, int selector, int delta) { return new HeapOp(Kind.DECREASE_NODE, heap, selector, delta); }
    static HeapOp decreaseValue(int heap, int selector, int delta) { return new HeapOp(Kind.DECREASE_VALUE, heap, selector, delta); }
    static HeapOp meld(int heap, int other) { return new HeapOp(Kind.MELD, heap, other, 0); }

    HeapOp with(int heap, int a, int b) {
        return new HeapOp(kind, heap, kind.args >= 1 ? a : 0, kind.args >= 2 ? b : 0);
    }

    static HeapOp parse(String line) {
        String[] t = line.trim().split("\\s+");
        Kind kind = Kind.of(t[0]);
        if (t.length != 2 + kind.args) {
            throw new IllegalArgumentException("'" + line + "': 인자 수가 " + (1 + kind.args) + "개여야 함");
        }
        int heap = Integer.parseInt(t[1]);
        int a = kind.args >= 1 ? Integer.parseInt(t[2]) : 0;
        int b = kind.args >= 2 ? Integer.parseInt(t[3]) : 0;
        return new HeapOp(kind, heap, a, b);
    }

    /** '#' 주석과 빈 줄을 무시하고, "heaps N" 헤더를 제외한 연산 줄을 파싱 */
    static List<HeapOp> parseAll(List<String> lines) {
        List<HeapOp> ops = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("heaps ")) continue;
            ops.add(parse(line));
        }
        return ops;
    }

    /** 회귀 파일의 "heaps N" 헤더 (없으면 기본값) */
    static int heapCount(List<String> lines, int defaultCount) {
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("heaps ")) return Integer.parseInt(line.substring(6).trim());
        }
        return defaultCount;
    }

    static String format(List<HeapOp> ops) {
        StringBuilder sb = new StringBuilder();
        for (HeapOp op : ops) sb.append(op).append('\n');
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(kind.text).append(' ').append(heap);
        if (kind.args >= 1) sb.append(' ').append(a);
        if (kind.args >= 2) sb.append(' ').append(b);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HeapOp)) return false;
        HeapOp other = (HeapOp) o;
        return kind == other.kind && heap == other.heap && a == other.a && b == other.b;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, heap, a, b);
    }
}
