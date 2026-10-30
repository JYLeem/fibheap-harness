import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 7-a] 자료구조 연산 퍼즈 테스트 (파일 파싱 퍼즈와 분리).
 *
 * 상태 기반 테스트보다 훨씬 긴 시퀀스와 극단적인 분포(경계값 위주, 잘못된 요청 다수)를 사용한다.
 * 힙이 수천 개 노드로 커지므로 비용을 줄이려고 불변식은 7 연산마다, 전체 노드 집합 비교는 500 연산마다 수행한다
 * (실패하면 FailureReporter 가 STRICT 설정으로 다시 실행해 정확한 실패 지점을 찾는다).
 * 실패 시 seed · 생성 조건 · 전체 시퀀스 · 최소 재현 시퀀스가 target/harness-failures 에 저장된다.
 *
 * 규모: -Dharness.scale=quick|default|long  (long 은 야간 실행용)
 */
@DisplayName("7a. Fuzz: heap operations")
class HeapFuzzTest {

    @Test
    void longSequencesAcrossAllProfiles() {
        int length = 1_500 * TestHeaps.scale();
        for (OpSequenceGenerator.Profile p : OpSequenceGenerator.Profile.values()) {
            for (long seed : TestHeaps.seeds(500_000 + p.ordinal() * 10_000L, 2 * TestHeaps.scale())) {
                int heaps = 1 + (int) (seed % 4);
                List<HeapOp> ops = OpSequenceGenerator.generate(seed, length, heaps, p);
                FailureReporter.runOrReport("Fuzz-" + p, seed,
                        "profile=" + p + ", length=" + length + ", heaps=" + heaps,
                        ops, heaps, FibHeapSubject::new, HeapHarness.Config.sampled(7, 500));
            }
        }
    }

    @Test
    void chaosWithManyHeapsAndInvalidRequests() {
        for (long seed : TestHeaps.seeds(900_000, 5 * TestHeaps.scale())) {
            List<HeapOp> ops = OpSequenceGenerator.generate(seed, 3_000, 8, OpSequenceGenerator.Profile.CHAOS);
            FailureReporter.runOrReport("Fuzz-chaos8", seed, "profile=CHAOS, length=3000, heaps=8",
                    ops, 8, FibHeapSubject::new, HeapHarness.Config.sampled(1, 100));
        }
    }
}
