import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * Fibonacci Heap 구현 및 파일 기반 테스트 프로그램.
 *
 * <pre>
 * data.txt   : m n1 e1..en1 n2 e2..en2 ...  (공백/줄바꿈 구분 정수)
 * test.txt   : 한 줄에 명령어 하나
 *                push h x               h번 힙에 x 삽입
 *                pop h                  h번 힙의 최솟값 삭제 후 출력
 *                top h                  h번 힙의 최솟값 출력 (삭제 없음)
 *                decrease_key h old new h번 힙의 old 값(먼저 찾은 노드 하나)을 new 로 감소
 *                union a b              b번 힙을 a번 힙에 병합 (b번 힙은 빈 힙이 됨)
 * result.txt : 명령어 한 줄당 결과 한 줄
 *                push / decrease_key / union  -> OK
 *                pop / top                    -> 값, 빈 힙이면 EMPTY
 *                잘못된 명령어 / 실패           -> ERROR 사유
 * </pre>
 *
 * 실행: java Main [data.txt] [test.txt] [result.txt]
 * 자체 테스트: java Main --test
 */
public class Main {
    private static final String DATA_FILE = "data.txt";
    private static final String TEST_FILE = "test.txt";
    private static final String RESULT_FILE = "result.txt";

    /** 인덱스 0 ~ m-1 의 힙 목록 */
    private static final List<FibonacciHeap> heaps = new ArrayList<>();
    /** result.txt 에 기록할 결과 (명령어 한 줄당 한 줄) */
    private static final List<String> results = new ArrayList<>();

    // =====================================================================
    // main / fheap / input / execute / output
    // =====================================================================

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--test")) {
            int failed = SelfTest.runAll();
            System.exit(failed == 0 ? 0 : 1);
            return;
        }

        String dataFile = args.length > 0 ? args[0] : DATA_FILE;
        String testFile = args.length > 1 ? args[1] : TEST_FILE;
        String resultFile = args.length > 2 ? args[2] : RESULT_FILE;

        try {
            input(dataFile);
            execute(testFile);
            output(resultFile);
            System.out.println(resultFile + " 저장 완료 (힙 " + heaps.size() + "개, 결과 " + results.size() + "줄)");
        } catch (NoSuchFileException e) {
            System.err.println("입력 파일을 찾을 수 없습니다: " + Paths.get(e.getFile()).toAbsolutePath());
            System.exit(1);
        } catch (InputFormatException e) {
            System.err.println("data.txt 형식 오류: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("파일 입출력 오류: " + e.getMessage());
            System.exit(1);
        }
    }

    /** 새 Fibonacci Heap 을 생성하여 힙 목록 끝에 등록하고 반환한다. */
    static FibonacciHeap fheap() {
        FibonacciHeap heap = new FibonacciHeap();
        heaps.add(heap);
        return heap;
    }

    /** index 번 힙을 반환한다. 범위를 벗어나면 CommandException. */
    static FibonacciHeap fheap(int index) throws CommandException {
        if (index < 0 || index >= heaps.size()) {
            throw new CommandException("힙 번호 " + index + " 가 범위(0~" + (heaps.size() - 1) + ")를 벗어났습니다");
        }
        return heaps.get(index);
    }

    /** data.txt 를 읽어 m 개의 힙을 구성한다. */
    static void input(String fileName) throws IOException, InputFormatException {
        heaps.clear();
        Iterator<String> it = tokenize(Files.readAllLines(Paths.get(fileName), StandardCharsets.UTF_8)).iterator();

        int m = nextInt(it, "힙의 개수 m");
        if (m < 0) throw new InputFormatException("m 은 0 이상이어야 합니다: " + m);

        for (int i = 0; i < m; i++) {
            FibonacciHeap heap = fheap();
            int n = nextInt(it, i + "번 힙의 원소 수");
            if (n < 0) throw new InputFormatException(i + "번 힙의 원소 수는 0 이상이어야 합니다: " + n);
            for (int j = 0; j < n; j++) {
                heap.insert(nextInt(it, i + "번 힙의 " + (j + 1) + "번째 원소"));
            }
        }
        if (it.hasNext()) {
            throw new InputFormatException("명세보다 많은 데이터가 있습니다 (첫 초과 값: " + it.next() + ")");
        }
    }

    /** test.txt 의 명령어를 한 줄씩 수행하고 결과를 results 에 모은다. */
    static void execute(String fileName) throws IOException {
        results.clear();
        List<String> lines = Files.readAllLines(Paths.get(fileName), StandardCharsets.UTF_8);
        for (int lineNo = 1; lineNo <= lines.size(); lineNo++) {
            String line = lines.get(lineNo - 1).replace("﻿", "").trim();
            if (line.isEmpty()) continue;
            try {
                results.add(run(line.split("\\s+")));
            } catch (CommandException e) {
                results.add("ERROR " + e.getMessage());
                System.err.println(fileName + " " + lineNo + "행 [" + line + "]: " + e.getMessage());
            }
        }
    }

    /** results 를 result.txt 에 UTF-8, 줄바꿈 '\n' 으로 저장한다. */
    static void output(String fileName) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8)) {
            for (String line : results) {
                writer.write(line);
                writer.write('\n');
            }
        }
    }

    // =====================================================================
    // 명령어 해석
    // =====================================================================

    private static String run(String[] t) throws CommandException {
        switch (t[0]) {
            case "push": {
                expectArgs(t, 2);
                fheap(parseInt(t[1], "힙 번호")).insert(parseInt(t[2], "키"));
                return "OK";
            }
            case "pop": {
                expectArgs(t, 1);
                Integer v = fheap(parseInt(t[1], "힙 번호")).extractMin();
                return v == null ? "EMPTY" : String.valueOf(v);
            }
            case "top": {
                expectArgs(t, 1);
                Integer v = fheap(parseInt(t[1], "힙 번호")).minimum();
                return v == null ? "EMPTY" : String.valueOf(v);
            }
            case "decrease_key": {
                expectArgs(t, 3);
                FibonacciHeap heap = fheap(parseInt(t[1], "힙 번호"));
                try {
                    heap.decreaseKey(parseInt(t[2], "기존 키"), parseInt(t[3], "새 키"));
                } catch (NoSuchElementException | IllegalArgumentException e) {
                    throw new CommandException(e.getMessage());
                }
                return "OK";
            }
            case "union": {
                expectArgs(t, 2);
                int a = parseInt(t[1], "힙 번호");
                int b = parseInt(t[2], "힙 번호");
                if (a == b) throw new CommandException("같은 힙끼리는 union 할 수 없습니다: " + a);
                fheap(a).union(fheap(b));
                return "OK";
            }
            default:
                throw new CommandException("알 수 없는 명령어: " + t[0]);
        }
    }

    private static void expectArgs(String[] t, int count) throws CommandException {
        if (t.length - 1 != count) {
            throw new CommandException(t[0] + " 의 인자는 " + count + "개여야 합니다 (입력: " + (t.length - 1) + "개)");
        }
    }

    private static int parseInt(String s, String what) throws CommandException {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new CommandException(what + " 가 정수가 아닙니다: " + s);
        }
    }

    private static List<String> tokenize(List<String> lines) {
        List<String> tokens = new ArrayList<>();
        for (String line : lines) {
            for (String tok : line.replace("﻿", "").trim().split("\\s+")) {
                if (!tok.isEmpty()) tokens.add(tok);
            }
        }
        return tokens;
    }

    private static int nextInt(Iterator<String> it, String what) throws InputFormatException {
        if (!it.hasNext()) throw new InputFormatException(what + " 가 없습니다 (데이터가 부족합니다)");
        String tok = it.next();
        try {
            return Integer.parseInt(tok);
        } catch (NumberFormatException e) {
            throw new InputFormatException(what + " 가 정수가 아닙니다: " + tok);
        }
    }

    static final class CommandException extends Exception {
        CommandException(String message) { super(message); }
    }

    static final class InputFormatException extends Exception {
        InputFormatException(String message) { super(message); }
    }

    // =====================================================================
    // Fibonacci Heap
    // =====================================================================

    static final class FibonacciHeap {

        static final class Node {
            int key;
            int degree;
            boolean mark;
            Node parent;
            Node child;   // 자식 원형 리스트 중 임의의 한 노드
            Node left;    // 원형 이중 연결 리스트
            Node right;
            Owner owner;  // 소속 힙 토큰 (꺼낸 노드는 null)

            Node(int key) {
                this.key = key;
                this.left = this;
                this.right = this;
            }
        }

        private Node min;   // 루트 리스트의 최소 노드 (빈 힙이면 null)
        private int size;
        /** 키 -> 해당 키를 가진 노드들 (삽입/변경 순서). decrease_key 의 노드 식별용 */
        private final Map<Integer, List<Node>> index = new HashMap<>();

        /**
         * 소속 힙 토큰 (union-find). union 시 상대 힙의 토큰을 이 힙 토큰 아래로 연결하므로
         * 병합된 노드들을 하나씩 갱신하지 않아도 O(1) 을 유지하며 소속을 판정할 수 있다.
         */
        static final class Owner {
            Owner parent;
        }

        private Owner owner = new Owner();

        boolean isEmpty() { return min == null; }

        int size() { return size; }

        Node minNode() { return min; }

        /** 최솟값 (빈 힙이면 null) — O(1) */
        Integer minimum() {
            return min == null ? null : min.key;
        }

        /** 키 삽입: 새 노드를 루트 리스트에 추가 — O(1) */
        Node insert(int key) {
            Node x = new Node(key);
            x.owner = owner;
            addRoot(x);
            size++;
            addToIndex(x);
            return x;
        }

        /** 두 힙 병합: other 의 루트 리스트를 이어 붙이고 other 는 빈 힙이 된다 — O(1) (+ 인덱스 병합) */
        void union(FibonacciHeap other) {
            if (other == this || other.min == null) return;
            if (min == null) {
                min = other.min;
            } else {
                Node a = min, b = other.min;
                Node aRight = a.right, bLeft = b.left;
                a.right = b;
                b.left = a;
                bLeft.right = aRight;
                aRight.left = bLeft;
                if (b.key < a.key) min = b;
            }
            size += other.size;
            for (Map.Entry<Integer, List<Node>> e : other.index.entrySet()) {
                index.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
            }
            other.min = null;
            other.size = 0;
            other.index.clear();
            // other 의 노드는 이제 이 힙 소속, other 는 새 토큰으로 빈 힙이 된다
            other.owner.parent = owner;
            other.owner = new Owner();
        }

        /** 최솟값 삭제 및 반환 (빈 힙이면 null) — 분할상환 O(log n) */
        Integer extractMin() {
            Node z = min;
            if (z == null) return null;

            // z 의 자식들을 루트 리스트로 올린다
            if (z.child != null) {
                for (Node c : siblings(z.child)) {
                    c.parent = null;
                    c.mark = false;
                    c.left = c;
                    c.right = c;
                    insertAfter(z, c);
                }
                z.child = null;
            }

            if (z.right == z) {
                min = null;
            } else {
                Node next = z.right;
                removeFromList(z);
                min = next;
                consolidate();
            }
            size--;
            removeFromIndex(z);
            z.owner = null;
            z.degree = 0;
            return z.key;
        }

        /** oldKey 값을 가진 노드 중 먼저 찾은 노드 하나의 키를 newKey 로 감소 */
        void decreaseKey(int oldKey, int newKey) {
            List<Node> nodes = index.get(oldKey);
            if (nodes == null || nodes.isEmpty()) {
                throw new NoSuchElementException("키 " + oldKey + " 가 힙에 없습니다");
            }
            decreaseKey(nodes.get(0), newKey);
        }

        /** 노드 x 의 키를 k 로 감소 — 분할상환 O(1) */
        void decreaseKey(Node x, int k) {
            if (!contains(x)) {
                throw new IllegalArgumentException("이 힙에 없는 노드입니다 (이미 꺼냈거나 다른 힙의 노드)");
            }
            if (k > x.key) {
                throw new IllegalArgumentException("새 키 " + k + " 가 기존 키 " + x.key + " 보다 큽니다");
            }
            if (k == x.key) return;

            removeFromIndex(x);
            x.key = k;
            addToIndex(x);

            Node y = x.parent;
            if (y != null && x.key < y.key) {
                cut(x, y);
                cascadingCut(y);
            }
            if (x.key < min.key) min = x;
        }

        /** x 가 이 힙에 살아 있는 노드인지 — 경로 압축으로 분할상환 거의 O(1) */
        boolean contains(Node x) {
            if (x == null || x.owner == null) return false;
            Owner root = x.owner;
            while (root.parent != null) root = root.parent;
            for (Owner o = x.owner; o != root; ) {
                Owner next = o.parent;
                o.parent = root;
                o = next;
            }
            x.owner = root;
            return root == owner;
        }

        // ---------------------------------------------------------- 내부 연산

        /** 같은 차수의 루트들을 연결하여 루트 리스트의 트리 수를 줄이고 min 을 다시 찾는다. */
        private void consolidate() {
            List<Node> byDegree = new ArrayList<>();
            for (Node w : siblings(min)) {
                Node x = w;
                int d = x.degree;
                while (d < byDegree.size() && byDegree.get(d) != null) {
                    Node y = byDegree.get(d);
                    if (y.key < x.key) {
                        Node tmp = x;
                        x = y;
                        y = tmp;
                    }
                    link(y, x);
                    byDegree.set(d, null);
                    d++;
                }
                while (byDegree.size() <= d) byDegree.add(null);
                byDegree.set(d, x);
            }
            min = null;
            for (Node r : byDegree) {
                if (r != null && (min == null || r.key < min.key)) min = r;
            }
        }

        /** 루트 y 를 루트 x 의 자식으로 만든다. */
        private void link(Node y, Node x) {
            removeFromList(y);
            y.parent = x;
            if (x.child == null) {
                x.child = y;
            } else {
                insertAfter(x.child, y);
            }
            x.degree++;
            y.mark = false;
        }

        /** x 를 부모 y 에서 잘라 루트 리스트로 옮긴다. */
        private void cut(Node x, Node y) {
            if (y.child == x) y.child = (x.right == x) ? null : x.right;
            removeFromList(x);
            y.degree--;
            addRoot(x);
        }

        /** 이미 자식을 잃은(marked) 조상을 따라 올라가며 연쇄적으로 자른다. */
        private void cascadingCut(Node y) {
            Node z = y.parent;
            if (z != null) {
                if (!y.mark) {
                    y.mark = true;
                } else {
                    cut(y, z);
                    cascadingCut(z);
                }
            }
        }

        private void addRoot(Node x) {
            x.parent = null;
            x.mark = false;
            if (min == null) {
                x.left = x;
                x.right = x;
                min = x;
            } else {
                insertAfter(min, x);
                if (x.key < min.key) min = x;
            }
        }

        private static void insertAfter(Node anchor, Node x) {
            x.left = anchor;
            x.right = anchor.right;
            anchor.right.left = x;
            anchor.right = x;
        }

        private static void removeFromList(Node x) {
            x.left.right = x.right;
            x.right.left = x.left;
            x.left = x;
            x.right = x;
        }

        /** start 가 속한 원형 리스트의 노드들을 순서대로 복사해 반환 (순회 중 수정 대비) */
        static List<Node> siblings(Node start) {
            List<Node> list = new ArrayList<>();
            if (start == null) return list;
            Node x = start;
            do {
                list.add(x);
                x = x.right;
            } while (x != start);
            return list;
        }

        private void addToIndex(Node x) {
            index.computeIfAbsent(x.key, k -> new ArrayList<>()).add(x);
        }

        private void removeFromIndex(Node x) {
            List<Node> nodes = index.get(x.key);
            if (nodes == null) return;
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i) == x) {
                    nodes.remove(i);
                    break;
                }
            }
            if (nodes.isEmpty()) index.remove(x.key);
        }

        // ---------------------------------------------------------- 불변식 검사

        /** 구조적 불변식을 검사하고, 위반 시 IllegalStateException 을 던진다. */
        void validate() {
            if (min == null) {
                check(size == 0, "빈 힙인데 size=" + size);
                check(index.isEmpty(), "빈 힙인데 인덱스가 비어 있지 않음");
                return;
            }
            int[] count = {0};
            boolean minInRoots = false;
            int minRootKey = Integer.MAX_VALUE;
            for (Node r : siblings(min)) {
                checkLinks(r);
                check(r.parent == null, "루트 " + r.key + " 의 parent 가 null 이 아님");
                check(!r.mark, "루트 " + r.key + " 가 marked 상태");
                if (r == min) minInRoots = true;
                minRootKey = Math.min(minRootKey, r.key);
                validateSubtree(r, count);
            }
            check(minInRoots, "min 이 루트 리스트에 없음");
            check(min.key == minRootKey, "min(" + min.key + ")이 루트 최솟값(" + minRootKey + ")이 아님");
            check(count[0] == size, "노드 수(" + count[0] + ")와 size(" + size + ") 불일치");

            int indexed = 0;
            for (List<Node> nodes : index.values()) indexed += nodes.size();
            check(indexed == size, "인덱스 노드 수(" + indexed + ")와 size(" + size + ") 불일치");

            double bound = Math.log(size) / Math.log((1 + Math.sqrt(5)) / 2) + 1;
            for (Node r : siblings(min)) checkDegreeBound(r, bound);
        }

        private void validateSubtree(Node x, int[] count) {
            count[0]++;
            List<Node> nodes = index.get(x.key);
            check(nodes != null && nodes.contains(x), "노드 " + x.key + " 가 인덱스에 없음");
            List<Node> children = siblings(x.child);
            check(children.size() == x.degree,
                    "노드 " + x.key + " 의 degree(" + x.degree + ")와 자식 수(" + children.size() + ") 불일치");
            for (Node c : children) {
                checkLinks(c);
                check(c.parent == x, "노드 " + c.key + " 의 parent 가 " + x.key + " 가 아님");
                check(c.key >= x.key, "힙 순서 위반: 부모 " + x.key + " > 자식 " + c.key);
                validateSubtree(c, count);
            }
        }

        private void checkDegreeBound(Node x, double bound) {
            check(x.degree <= bound, "노드 " + x.key + " 의 degree(" + x.degree + ")가 상한을 초과");
            for (Node c : siblings(x.child)) checkDegreeBound(c, bound);
        }

        private static void checkLinks(Node x) {
            check(x.right.left == x && x.left.right == x, "노드 " + x.key + " 의 left/right 링크 불일치");
        }

        private static void check(boolean cond, String message) {
            if (!cond) throw new IllegalStateException(message);
        }
    }

    // =====================================================================
    // 자체 테스트 (java Main --test)
    // =====================================================================

    static final class SelfTest {

        private interface TestCase { void run() throws Exception; }

        private static int passed, failed;

        static int runAll() {
            passed = 0;
            failed = 0;
            test("push / top", SelfTest::testPushTop);
            test("pop 오름차순", SelfTest::testPopOrder);
            test("빈 힙 pop / top", SelfTest::testEmpty);
            test("decrease_key: cut", SelfTest::testDecreaseKeyCut);
            test("decrease_key: cascading cut", SelfTest::testCascadingCut);
            test("decrease_key: 오류 처리", SelfTest::testDecreaseKeyErrors);
            test("decrease_key: 중복 키", SelfTest::testDuplicateKeys);
            test("union", SelfTest::testUnion);
            test("union: 빈 힙", SelfTest::testUnionEmpty);
            test("무작위 혼합 연산 (불변식 + 기준 구현 비교)", SelfTest::testRandomized);
            test("파일 입출력 통합 (input/execute/output)", SelfTest::testFilePipeline);
            test("잘못된 data.txt 처리", SelfTest::testBadData);
            System.out.println("------------------------------------");
            System.out.println("통과 " + passed + " / 실패 " + failed);
            return failed;
        }

        private static void test(String name, TestCase tc) {
            try {
                tc.run();
                passed++;
                System.out.println("[PASS] " + name);
            } catch (Throwable e) {
                failed++;
                System.out.println("[FAIL] " + name + " : " + e);
            }
        }

        private static void assertEq(Object expected, Object actual, String what) {
            if (expected == null ? actual != null : !expected.equals(actual)) {
                throw new AssertionError(what + " - 기대값 " + expected + ", 실제값 " + actual);
            }
        }

        private static void assertTrue(boolean cond, String what) {
            if (!cond) throw new AssertionError(what);
        }

        private static void testPushTop() {
            FibonacciHeap h = new FibonacciHeap();
            int[] keys = {5, 3, 8, 3, 10};
            int expectedMin = Integer.MAX_VALUE;
            for (int k : keys) {
                h.insert(k);
                expectedMin = Math.min(expectedMin, k);
                h.validate();
                assertEq(expectedMin, h.minimum(), "top");
            }
            assertEq(keys.length, h.size(), "size");
            assertEq(3, h.minimum(), "top 은 삭제하지 않음");
            assertEq(keys.length, h.size(), "top 이후 size");
        }

        private static void testPopOrder() {
            Random rnd = new Random(1);
            FibonacciHeap h = new FibonacciHeap();
            List<Integer> ref = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                int k = rnd.nextInt(1000);
                h.insert(k);
                ref.add(k);
            }
            Collections.sort(ref);
            for (int expected : ref) {
                assertEq(expected, h.extractMin(), "pop");
                h.validate();
            }
            assertTrue(h.isEmpty(), "모두 pop 후 빈 힙");
        }

        private static void testEmpty() {
            FibonacciHeap h = new FibonacciHeap();
            assertEq(null, h.minimum(), "빈 힙 top");
            assertEq(null, h.extractMin(), "빈 힙 pop");
            h.insert(1);
            assertEq(1, h.extractMin(), "pop");
            assertEq(null, h.extractMin(), "다시 빈 힙 pop");
            h.validate();
        }

        /** 1..9 삽입 후 pop → 8개 노드가 하나의 B3 트리로 통합된다. */
        private static FibonacciHeap buildB3() {
            FibonacciHeap h = new FibonacciHeap();
            for (int k = 1; k <= 9; k++) h.insert(k * 10);
            h.extractMin();
            h.validate();
            assertEq(1, FibonacciHeap.siblings(h.minNode()).size(), "통합 후 루트 수");
            assertEq(3, h.minNode().degree, "루트 차수");
            return h;
        }

        private static FibonacciHeap.Node childWithDegree(FibonacciHeap.Node p, int degree) {
            for (FibonacciHeap.Node c : FibonacciHeap.siblings(p.child)) {
                if (c.degree == degree) return c;
            }
            throw new AssertionError("차수 " + degree + " 인 자식이 없음");
        }

        private static void testDecreaseKeyCut() {
            FibonacciHeap h = buildB3();
            FibonacciHeap.Node root = h.minNode();
            FibonacciHeap.Node p = childWithDegree(root, 2);
            FibonacciHeap.Node x = childWithDegree(p, 0);

            h.decreaseKey(x, 1);
            h.validate();
            assertEq(null, x.parent, "잘린 노드는 루트");
            assertEq(1, h.minimum(), "min 갱신");
            assertTrue(p.mark, "자식을 잃은 부모는 marked");
            assertEq(1, p.degree, "부모 차수 감소");

            // 힙 순서를 깨지 않는 감소는 자르지 않는다
            FibonacciHeap.Node q = childWithDegree(root, 0);
            h.decreaseKey(q, q.key - 1);
            h.validate();
            assertTrue(q.parent == root, "순서 유지 시 cut 없음");
        }

        private static void testCascadingCut() {
            FibonacciHeap h = buildB3();
            FibonacciHeap.Node root = h.minNode();
            FibonacciHeap.Node p = childWithDegree(root, 2);
            FibonacciHeap.Node c0 = childWithDegree(p, 0);
            FibonacciHeap.Node c1 = childWithDegree(p, 1);

            h.decreaseKey(c0, 1);     // p 가 marked
            h.decreaseKey(c1, 2);     // p 가 이미 marked → p 도 잘림 (연쇄 절단)
            h.validate();
            assertEq(null, p.parent, "연쇄 절단으로 p 가 루트가 됨");
            assertTrue(!p.mark, "루트가 된 p 는 unmarked");
            assertEq(2, root.degree, "원래 루트의 차수 감소");
            assertTrue(!root.mark, "루트는 mark 되지 않음");
            assertEq(4, FibonacciHeap.siblings(h.minNode()).size(), "루트 수");

            int[] expected = {1, 2, 20, 30, 40, 50, 60, 80};
            List<Integer> popped = new ArrayList<>();
            while (!h.isEmpty()) {
                popped.add(h.extractMin());
                h.validate();
            }
            List<Integer> sorted = new ArrayList<>(popped);
            Collections.sort(sorted);
            assertEq(sorted, popped, "pop 순서");
            assertEq(expected.length, popped.size(), "pop 개수");
        }

        private static void testDecreaseKeyErrors() {
            FibonacciHeap h = new FibonacciHeap();
            h.insert(10);
            try {
                h.decreaseKey(99, 1);
                throw new AssertionError("없는 키에 예외가 발생해야 함");
            } catch (NoSuchElementException expected) { /* ok */ }
            try {
                h.decreaseKey(10, 20);
                throw new AssertionError("더 큰 키에 예외가 발생해야 함");
            } catch (IllegalArgumentException expected) { /* ok */ }
            h.decreaseKey(10, 10);
            h.validate();
            assertEq(10, h.minimum(), "같은 키로 변경 시 변화 없음");
        }

        private static void testDuplicateKeys() {
            FibonacciHeap h = new FibonacciHeap();
            FibonacciHeap.Node first = h.insert(7);
            h.insert(7);
            h.insert(7);
            h.decreaseKey(7, 1);
            h.validate();
            assertEq(1, first.key, "먼저 삽입된 노드가 변경됨");
            assertEq(1, h.extractMin(), "pop");
            assertEq(7, h.extractMin(), "pop");
            assertEq(7, h.extractMin(), "pop");
            assertTrue(h.isEmpty(), "빈 힙");
        }

        private static void testUnion() {
            FibonacciHeap a = new FibonacciHeap();
            FibonacciHeap b = new FibonacciHeap();
            for (int k : new int[]{5, 1, 9}) a.insert(k);
            for (int k : new int[]{3, 0}) b.insert(k);
            a.extractMin();               // a 를 통합된 상태로 만든 뒤 병합
            a.union(b);
            a.validate();
            b.validate();
            assertEq(4, a.size(), "병합 후 size");
            assertEq(0, a.minimum(), "병합 후 min");
            assertTrue(b.isEmpty(), "병합된 힙은 빈 힙");
            a.decreaseKey(3, -1);         // b 에서 온 노드도 a 에서 식별 가능
            a.validate();
            int[] expected = {-1, 0, 5, 9};
            for (int e : expected) assertEq(e, a.extractMin(), "pop");
        }

        private static void testUnionEmpty() {
            FibonacciHeap a = new FibonacciHeap();
            FibonacciHeap b = new FibonacciHeap();
            a.union(b);
            a.validate();
            assertTrue(a.isEmpty(), "빈 + 빈");
            b.insert(4);
            a.union(b);
            a.validate();
            assertEq(4, a.minimum(), "빈 + 비어있지 않음");
            a.union(b);
            a.validate();
            assertEq(1, a.size(), "비어있지 않음 + 빈");
        }

        /** 기준 구현(정렬 없는 리스트)과 결과를 비교하며 매 연산 후 불변식을 검사한다. */
        private static void testRandomized() {
            Random rnd = new Random(2026);
            FibonacciHeap[] h = {new FibonacciHeap(), new FibonacciHeap()};
            List<List<Integer>> ref = List.of(new ArrayList<>(), new ArrayList<>());

            for (int step = 0; step < 5000; step++) {
                int i = rnd.nextInt(2);
                int op = rnd.nextInt(10);
                if (op < 4) {                                   // push
                    int k = rnd.nextInt(10000);
                    h[i].insert(k);
                    ref.get(i).add(k);
                } else if (op < 6) {                            // pop
                    Integer expected = ref.get(i).isEmpty() ? null : Collections.min(ref.get(i));
                    if (expected != null) ref.get(i).remove(expected);
                    assertEq(expected, h[i].extractMin(), "step " + step + " pop");
                } else if (op < 7) {                            // top
                    Integer expected = ref.get(i).isEmpty() ? null : Collections.min(ref.get(i));
                    assertEq(expected, h[i].minimum(), "step " + step + " top");
                } else if (op < 9) {                            // decrease_key
                    if (ref.get(i).isEmpty()) continue;
                    Integer old = ref.get(i).get(rnd.nextInt(ref.get(i).size()));
                    int nk = old - rnd.nextInt(500);
                    h[i].decreaseKey(old, nk);
                    ref.get(i).remove(old);
                    ref.get(i).add(nk);
                } else {                                        // union
                    h[i].union(h[1 - i]);
                    ref.get(i).addAll(ref.get(1 - i));
                    ref.get(1 - i).clear();
                }
                h[0].validate();
                h[1].validate();
                assertEq(ref.get(0).size(), h[0].size(), "step " + step + " size0");
                assertEq(ref.get(1).size(), h[1].size(), "step " + step + " size1");
            }
        }

        private static void testFilePipeline() throws Exception {
            Path dir = Files.createTempDirectory("fheap-test");
            Path data = dir.resolve("data.txt");
            Path test = dir.resolve("test.txt");
            Path result = dir.resolve("result.txt");
            Files.write(data, List.of("3", "3", "5 1 9", "2", "3 7", "0"), StandardCharsets.UTF_8);
            Files.write(test, List.of(
                    "top 0", "pop 0", "push 2 4", "decrease_key 1 7 2", "union 0 1",
                    "top 0", "pop 1", "decrease_key 0 100 1", "union 0 0", "jump 0",
                    "push 9 1", "pop 0", "pop 0", "pop 0", "pop 0", "pop 0"), StandardCharsets.UTF_8);

            input(data.toString());
            execute(test.toString());
            output(result.toString());

            List<String> expected = List.of(
                    "1", "1", "OK", "OK", "OK",
                    "2", "EMPTY",
                    "ERROR 키 100 가 힙에 없습니다",
                    "ERROR 같은 힙끼리는 union 할 수 없습니다: 0",
                    "ERROR 알 수 없는 명령어: jump",
                    "ERROR 힙 번호 9 가 범위(0~2)를 벗어났습니다",
                    "2", "3", "5", "9", "EMPTY");
            assertEq(expected, Files.readAllLines(result, StandardCharsets.UTF_8), "result.txt");
            for (FibonacciHeap heap : heaps) heap.validate();
        }

        private static void testBadData() throws Exception {
            Path dir = Files.createTempDirectory("fheap-bad");
            Path data = dir.resolve("data.txt");
            Files.write(data, List.of("2", "3", "1 2"), StandardCharsets.UTF_8);
            try {
                input(data.toString());
                throw new AssertionError("데이터 부족 시 예외가 발생해야 함");
            } catch (InputFormatException expected) { /* ok */ }

            Files.write(data, List.of("1", "2", "1 x"), StandardCharsets.UTF_8);
            try {
                input(data.toString());
                throw new AssertionError("정수가 아닌 값에 예외가 발생해야 함");
            } catch (InputFormatException expected) { /* ok */ }

            try {
                input(dir.resolve("none.txt").toString());
                throw new AssertionError("없는 파일에 예외가 발생해야 함");
            } catch (NoSuchFileException expected) { /* ok */ }
        }
    }
}