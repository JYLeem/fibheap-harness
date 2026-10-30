import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Main.input → execute → output 을 임시 디렉터리의 파일로 실행하는 도우미 (main() 은 System.exit 를 호출하므로 쓰지 않음) */
final class MainPipeline {

    private MainPipeline() {}

    static final class Run {
        final Path dir;
        final List<String> resultLines;      // result.txt 를 '\n' 로 나눈 줄 (마지막 빈 조각 제외)
        final byte[] resultBytes;

        Run(Path dir, List<String> resultLines, byte[] resultBytes) {
            this.dir = dir;
            this.resultLines = resultLines;
            this.resultBytes = resultBytes;
        }
    }

    static Path tempDir() throws IOException {
        return Files.createTempDirectory("fheap-pipeline");
    }

    static Run run(String data, String commands) throws Exception {
        return run(data.getBytes(StandardCharsets.UTF_8), commands.getBytes(StandardCharsets.UTF_8));
    }

    static Run run(byte[] data, byte[] commands) throws Exception {
        Path dir = tempDir();
        Path dataFile = dir.resolve("data.txt");
        Path testFile = dir.resolve("test.txt");
        Path resultFile = dir.resolve("result.txt");
        Files.write(dataFile, data);
        Files.write(testFile, commands);
        PrintStream err = System.err;
        if (!Boolean.getBoolean("harness.showMainStderr")) System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            Main.input(dataFile.toString());
            Main.execute(testFile.toString());     // 잘못된 명령마다 stderr 에 사유를 출력하므로 기본은 숨김
            Main.output(resultFile.toString());
        } finally {
            System.setErr(err);
        }
        byte[] bytes = Files.readAllBytes(resultFile);
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        return new Run(dir, lines, bytes);
    }

    /** data.txt 만 읽어 본다 */
    static void input(String data) throws Exception {
        Path dir = tempDir();
        Path dataFile = dir.resolve("data.txt");
        Files.writeString(dataFile, data, StandardCharsets.UTF_8);
        Main.input(dataFile.toString());
    }
}
