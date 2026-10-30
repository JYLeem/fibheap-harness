import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;

/**
 * [기법 3·7·8] 무작위 테스트 실패 시 재현 정보를 남기는 도우미.
 *
 * 실패하면
 *   1) 실패 단계까지의 prefix 를 STRICT 설정으로 다시 실행해 signature 를 확정하고
 *   2) {@link SequenceMinimizer} 로 최소 재현 시퀀스를 찾은 뒤
 *   3) harness.failureDir (기본 target/harness-failures) 에
 *        &lt;이름&gt;-seed&lt;시드&gt;.min.ops   — 회귀 파일 형식. src/test/resources/regressions/ 로 복사하면 회귀 테스트가 됨
 *        &lt;이름&gt;-seed&lt;시드&gt;.full.txt  — 시드, 생성 조건, 원본 전체 시퀀스, 실행 기록, 스택 트레이스
 *      를 저장하고
 *   4) 시드·최소 시퀀스·원인을 담은 AssertionError 로 테스트를 실패시킨다.
 */
final class FailureReporter {

    private FailureReporter() {}

    static Path failureDir() {
        String dir = System.getProperty("harness.failureDir");
        return Paths.get(dir == null || dir.isBlank() ? "target/harness-failures" : dir);
    }

    static void runOrReport(String name, long seed, String generatorInfo, List<HeapOp> ops, int heapCount,
                            Supplier<HeapSubject> factory, HeapHarness.Config cfg) {
        try {
            HeapHarness.run(ops, heapCount, factory, cfg);
        } catch (HeapHarness.Failure f) {
            throw report(name, seed, generatorInfo, ops, heapCount, factory, f);
        }
    }

    static AssertionError report(String name, long seed, String generatorInfo, List<HeapOp> ops, int heapCount,
                                 Supplier<HeapSubject> factory, HeapHarness.Failure f) {
        List<HeapOp> prefix = ops.subList(0, Math.min(ops.size(), f.step + 1));
        // -Dharness.minimize=false : 축소 생략 (변이 테스트처럼 실패가 많이 예상되는 실행에서 시간 절약)
        boolean minimize = !"false".equals(System.getProperty("harness.minimize"));
        String strictSig = minimize ? SequenceMinimizer.strictSignature(prefix, heapCount, factory) : null;
        List<HeapOp> minimal = strictSig == null
                ? prefix
                : SequenceMinimizer.minimizeWithValues(prefix, heapCount, factory, strictSig);
        String sig = strictSig == null ? f.signature : strictSig;

        String base = sanitize(name) + "-seed" + seed;
        Path minFile = failureDir().resolve(base + ".min.ops");
        Path fullFile = failureDir().resolve(base + ".full.txt");
        String saveNote;
        try {
            Files.createDirectories(failureDir());
            Files.writeString(minFile, regressionFile(name, seed, sig, f, heapCount, minimal), StandardCharsets.UTF_8);
            Files.writeString(fullFile, fullReport(name, seed, generatorInfo, heapCount, ops, f, minimal, sig),
                    StandardCharsets.UTF_8);
            saveNote = "최소 재현: " + minFile.toAbsolutePath() + "\n전체 기록: " + fullFile.toAbsolutePath();
        } catch (IOException e) {
            saveNote = "실패 기록 저장 실패: " + e;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("무작위 테스트 실패: ").append(name).append('\n')
                .append("  seed           : ").append(seed).append("  (-Dharness.seed=").append(seed).append(" 로 재현)\n")
                .append("  생성 조건       : ").append(generatorInfo).append('\n')
                .append("  실패 단계       : ").append(f.step).append(" / ").append(ops.size())
                .append("  연산 ").append(f.op == null ? "(최종 검사)" : f.op).append('\n')
                .append("  실패 서명       : ").append(sig).append('\n')
                .append("  원인           : ").append(f.getCause()).append('\n')
                .append("  최소 재현 시퀀스 (").append(minimal.size()).append("개 연산, heaps ").append(heapCount).append("):\n");
        for (HeapOp op : minimal) msg.append("    ").append(op).append('\n');
        msg.append("  최근 실행 기록:\n");
        List<String> trace = f.trace;
        for (String line : trace.subList(Math.max(0, trace.size() - 8), trace.size())) {
            msg.append("    ").append(line).append('\n');
        }
        msg.append(saveNote);
        return new AssertionError(msg.toString(), f);
    }

    static String regressionFile(String name, long seed, String sig, Throwable cause, int heapCount,
                                 List<HeapOp> minimal) {
        return "# 자동 생성된 최소 재현 사례\n"
                + "# test: " + name + "\n"
                + "# seed: " + seed + "\n"
                + "# signature: " + sig + "\n"
                + "# cause: " + String.valueOf(cause.getCause()).replace('\n', ' ') + "\n"
                + "heaps " + heapCount + "\n"
                + HeapOp.format(minimal);
    }

    private static String fullReport(String name, long seed, String generatorInfo, int heapCount, List<HeapOp> ops,
                                     HeapHarness.Failure f, List<HeapOp> minimal, String sig) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("test      : " + name);
        pw.println("seed      : " + seed);
        pw.println("generator : " + generatorInfo);
        pw.println("heaps     : " + heapCount);
        pw.println("step      : " + f.step + " / " + ops.size() + "  op: " + f.op);
        pw.println("signature : " + sig);
        pw.println();
        pw.println("== 최소 재현 시퀀스 ==");
        pw.print(HeapOp.format(minimal));
        pw.println();
        pw.println("== 실행 기록 (최근) ==");
        f.trace.forEach(pw::println);
        pw.println();
        pw.println("== 원인 ==");
        f.getCause().printStackTrace(pw);
        pw.println();
        pw.println("== 원본 전체 시퀀스 (" + ops.size() + "개) ==");
        pw.print(HeapOp.format(ops));
        pw.flush();
        return sw.toString();
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
