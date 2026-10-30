import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * m개의 Fibonacci heap 생성을 위한 데이터셋을 data.txt에 저장한다.
 *
 * 파일 형식 (공백/줄바꿈으로 구분된 정수열):
 *   m
 *   n1
 *   e1_1 e1_2 ... e1_n1
 *   n2
 *   e2_1 e2_2 ... e2_n2
 *   ...
 *
 * 사용법:
 *   java FibHeapDataGenerator [m] [minSize] [maxSize] [minValue] [maxValue] [seed]
 *   (인자를 생략하면 기본값 사용)
 */
public class FibHeapDataGenerator {
    private static final String FILE_NAME = "data.txt";
    public static void main(String[] args) {
        int m        = args.length > 0 ? Integer.parseInt(args[0]) : 5;     // 힙 개수
        int minSize  = args.length > 1 ? Integer.parseInt(args[1]) : 5;     // 힙당 최소 원소 수
        int maxSize  = args.length > 2 ? Integer.parseInt(args[2]) : 15;    // 힙당 최대 원소 수
        int minValue = args.length > 3 ? Integer.parseInt(args[3]) : 1;     // 원소 최솟값
        int maxValue = args.length > 4 ? Integer.parseInt(args[4]) : 1000;  // 원소 최댓값
        long seed    = args.length > 5 ? Long.parseLong(args[5]) : System.currentTimeMillis();

        validate(m, minSize, maxSize, minValue, maxValue);

        Random random = new Random(seed);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME))) {
            // 첫 번째 자리: 힙의 개수 m
            writer.write(String.valueOf(m));
            writer.newLine();

            for (int i = 0; i < m; i++) {
                // 각 힙의 원소 수 n_i
                int n = minSize + random.nextInt(maxSize - minSize + 1);
                writer.write(String.valueOf(n));
                writer.newLine();

                // n_i개의 원소
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    int value = minValue + random.nextInt(maxValue - minValue + 1);
                    if (j > 0) sb.append(' ');
                    sb.append(value);
                }
                writer.write(sb.toString());
                writer.newLine();
            }

            System.out.printf("%s 생성 완료 (m=%d, seed=%d)%n", FILE_NAME, m, seed);
        } catch (IOException e) {
            System.err.println("파일 저장 실패: " + e.getMessage());
        }
    }

    private static void validate(int m, int minSize, int maxSize, int minValue, int maxValue) {
        if (m < 1) throw new IllegalArgumentException("m은 1 이상이어야 합니다.");
        if (minSize < 0 || minSize > maxSize)
            throw new IllegalArgumentException("0 <= minSize <= maxSize 이어야 합니다.");
        if (minValue > maxValue)
            throw new IllegalArgumentException("minValue <= maxValue 이어야 합니다.");
    }
}