import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * [기법 5] 표적 소스 변이 실행기 (PIT 를 보완: 요구된 6가지 결함 유형을 정확히 겨냥).
 *
 * tools/mutants.txt 의 각 변이를 Main.java 복사본에 적용하고, 프로젝트 복사본에서 테스트 명령을 실행한다.
 *   테스트 명령 실패(종료 코드 ≠ 0) 또는 시간 초과 → KILLED (테스트가 결함을 탐지)
 *   테스트 명령 성공                               → SURVIVED (탐지 실패 → 원인 분석 필요)
 *
 * 실행 (프로젝트 루트에서, JDK 11+ 단일 파일 실행):
 *   java tools/MutantRunner.java                                  # 기본: local-verify/run.sh (Maven 없이)
 *   java tools/MutantRunner.java --cmd "mvn -q -o test"           # Maven 으로
 *   java tools/MutantRunner.java --only M17 --timeout 300
 * 결과: target/mutants/report.md, 변이별 로그 target/mutants/<id>.log
 */
public class MutantRunner {

    static final class Mutant {
        final Map<String, String> f = new LinkedHashMap<>();

        String get(String k) { return f.getOrDefault(k, ""); }
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        String cmd = "local-verify/run.sh";
        Path mutantsFile = root.resolve("tools/mutants.txt");
        long timeoutSec = 600;
        String only = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cmd": cmd = args[++i]; break;
                case "--mutants": mutantsFile = Paths.get(args[++i]); break;
                case "--timeout": timeoutSec = Long.parseLong(args[++i]); break;
                case "--only": only = args[++i]; break;
                default: throw new IllegalArgumentException("알 수 없는 옵션: " + args[i]);
            }
        }
        Path mainFile = root.resolve("src/main/java/Main.java");
        String original = Files.readString(mainFile, StandardCharsets.UTF_8);
        List<Mutant> mutants = parse(Files.readAllLines(mutantsFile, StandardCharsets.UTF_8));
        Path outDir = root.resolve("target/mutants");
        Files.createDirectories(outDir);

        System.out.println("기준 실행(변이 없음) 확인 중...");
        Result baseline = runIn(root, original, cmd, timeoutSec, outDir.resolve("baseline.log"));
        if (baseline.exit != 0) {
            System.out.println("기준 실행이 실패했습니다 (exit " + baseline.exit + "). 먼저 테스트를 통과시키세요.");
            System.exit(2);
        }

        List<String[]> rows = new ArrayList<>();
        int killed = 0, survived = 0, invalid = 0;
        for (Mutant m : mutants) {
            String id = m.get("id");
            if (only != null && !only.equals(id)) continue;
            String find = unescape(m.get("find"));
            String replace = unescape(m.get("replace"));
            int count = occurrences(original, find);
            String status, detail;
            if (count != 1) {
                status = "INVALID";
                detail = "find 문자열이 " + count + "번 나타남";
                invalid++;
            } else {
                String mutated = original.replace(find, replace);
                Result r = runIn(root, mutated, cmd, timeoutSec, outDir.resolve(id + ".log"));
                if (r.timedOut) {
                    status = "KILLED";
                    detail = "시간 초과 (" + timeoutSec + "s)";
                    killed++;
                } else if (r.exit != 0) {
                    status = "KILLED";
                    detail = r.firstFailure;
                    killed++;
                } else {
                    status = "SURVIVED";
                    detail = "모든 테스트 통과";
                    survived++;
                }
            }
            String expect = m.get("expect");
            boolean surprise = ("killed".equals(expect) && !"KILLED".equals(status))
                    || ("equivalent".equals(expect) && "KILLED".equals(status));
            rows.add(new String[]{id, m.get("category"), m.get("desc"), status + (surprise ? " (!)" : ""), expect, detail});
            System.out.printf("%-4s %-9s %-11s %s%n", id, status, "[" + expect + "]", detail);
        }

        StringBuilder md = new StringBuilder("# 표적 변이 결과\n\n명령: `" + cmd + "`\n\n");
        md.append("| ID | 유형 | 변이 | 결과 | 예상 | 탐지한 테스트 / 비고 |\n|---|---|---|---|---|---|\n");
        for (String[] r : rows) md.append("| ").append(String.join(" | ", r).replace("\n", " ")).append(" |\n");
        int total = killed + survived;
        md.append("\nkilled ").append(killed).append(" / ").append(total)
                .append(", survived ").append(survived).append(", invalid ").append(invalid).append('\n');
        Files.writeString(outDir.resolve("report.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.printf("%nkilled %d / %d, survived %d, invalid %d → %s%n", killed, total, survived, invalid,
                outDir.resolve("report.md"));
    }

    static final class Result {
        int exit;
        boolean timedOut;
        String firstFailure = "";
    }

    private static Result runIn(Path root, String mainSource, String cmd, long timeoutSec, Path log) throws Exception {
        Path work = Files.createTempDirectory("mutant");
        try {
            copyProject(root, work);
            Files.writeString(work.resolve("src/main/java/Main.java"), mainSource, StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd).directory(work.toFile())
                    .redirectErrorStream(true).redirectOutput(log.toFile());
            pb.environment().putIfAbsent("HARNESS_SCALE", "quick");
            pb.environment().putIfAbsent("JAVA_OPTS", "-Dharness.minimize=false");
            Process p = pb.start();
            Result r = new Result();
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                r.timedOut = true;
                r.exit = -1;
                return r;
            }
            r.exit = p.exitValue();
            for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("[FAIL]") || t.startsWith("FAILED") || t.contains("<<< FAIL")
                        || t.startsWith("[ERROR] Failures") || t.startsWith("error:")) {
                    r.firstFailure = r.firstFailure.isEmpty() ? t : r.firstFailure + "; " + t;
                    if (r.firstFailure.length() > 160) break;
                }
            }
            if (r.firstFailure.isEmpty() && r.exit != 0) r.firstFailure = "종료 코드 " + r.exit;
            return r;
        } finally {
            deleteTree(work);
        }
    }

    private static void copyProject(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(from) && (name.equals("target") || name.equals(".git") || name.equals(".jqwik-database"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static List<Mutant> parse(List<String> lines) {
        List<Mutant> out = new ArrayList<>();
        Mutant cur = null;
        for (String line : lines) {
            if (line.startsWith("#")) continue;
            if (line.isBlank()) {
                if (cur != null) out.add(cur);
                cur = null;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            if (cur == null) cur = new Mutant();
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            cur.f.put(key, value);
        }
        if (cur != null) out.add(cur);
        return out;
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t");
    }

    private static int occurrences(String text, String find) {
        if (find.isEmpty()) return 0;
        int count = 0;
        for (int i = text.indexOf(find); i >= 0; i = text.indexOf(find, i + 1)) count++;
        return count;
    }
}
