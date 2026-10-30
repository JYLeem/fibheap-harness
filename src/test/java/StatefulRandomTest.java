import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [기법 3] 상태 기반 무작위 테스트 (JUnit 버전, 외부 라이브러리 없음).
 *
 * 여러 힙(3개)에 insert / findMin / extractMin / decreaseKey(노드·값) / meld 를 섞은 시퀀스를 실행하고
 * 매 연산 직후 불변식 + 기준 모델 비교를, 매 연산마다(STRICT) 노드 집합·멀티셋 비교까지 수행한다.
 * 연산 간 상호작용(예: meld 로 넘어온 노드를 decreaseKey, 통합 직후 cascading cut)을 겨냥한다.
 *
 * 재현: 실패 메시지의 seed 로 -Dharness.seed=<seed> 를 주면 해당 시퀀스만 다시 실행된다.
 * 같은 목적의 jqwik 버전은 {@code StatefulPropertyTest} (자동 축소 포함).
 */
@DisplayName("3. Stateful random sequences")
class StatefulRandomTest {

    private static final int HEAPS = 3;

    @Test
    void mixedOperationSequencesOnThreeHeaps() {
        for (OpSequenceGenerator.Profile p : OpSequenceGenerator.Profile.values()) {
            for (long seed : TestHeaps.seeds(20_000 + p.ordinal() * 1_000L, 4 * TestHeaps.scale())) {
                int length = 200 + (int) (seed % 7) * 150;
                List<HeapOp> ops = OpSequenceGenerator.generate(seed, length, HEAPS, p);
                FailureReporter.runOrReport("Stateful-" + p, seed,
                        "profile=" + p + ", length=" + length + ", heaps=" + HEAPS,
                        ops, HEAPS, FibHeapSubject::new, HeapHarness.Config.STRICT);
            }
        }
    }

    @Test
    void growThenCascadeOnSingleHeap() {
        // 한 힙에 큰 트리를 만든 뒤 감소·삭제를 집중: cut / cascadingCut / 재통합이 반복되는 경로
        for (long seed : TestHeaps.seeds(31_000, 3 * TestHeaps.scale())) {
            List<HeapOp> ops = OpSequenceGenerator.generate(seed, 1500, 1, OpSequenceGenerator.Profile.GROWTH);
            FailureReporter.runOrReport("Stateful-growth", seed, "profile=GROWTH, length=1500, heaps=1",
                    ops, 1, FibHeapSubject::new, HeapHarness.Config.DEFAULT);
        }
    }
}
