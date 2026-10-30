import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.Tuple;

/**
 * [기법 3·8] jqwik 상태 기반 속성 테스트.
 *
 * 연산 목록 자체를 jqwik 가 생성하고, {@link HeapHarness} 가 매 연산마다 불변식과 기준 모델을 검사한다.
 * HeapOp 는 선택자 기반이라 jqwik 가 목록을 줄이거나 값을 줄여도 항상 실행 가능한 시퀀스가 되므로,
 * 실패하면 jqwik 의 축소(shrinking)가 그대로 최소 재현 시퀀스를 만들어 준다.
 * 실패 시 jqwik 가 seed 를 출력하고 .jqwik-database 에 저장한다. @Property(seed = "...") 로 재실행할 수 있다.
 *
 * ※ 이 작업 환경에서는 jqwik 를 내려받을 수 없어 컴파일·실행하지 않았다 (Maven 빌드에서 실행).
 */
class StatefulPropertyTest {

    private static final int HEAPS = 3;

    @Property(tries = 500, shrinking = ShrinkingMode.FULL)
    void anySequenceKeepsInvariantsAndMatchesModel(@ForAll("operations") List<HeapOp> ops) {
        HeapHarness.run(ops, HEAPS, FibHeapSubject::new, HeapHarness.Config.STRICT);
    }

    @Property(tries = 200)
    void sequencesOnDuplicateHeavyKeysMatchModel(@ForAll("duplicateHeavyOperations") List<HeapOp> ops) {
        HeapHarness.run(ops, HEAPS, FibHeapSubject::new, HeapHarness.Config.STRICT);
    }

    @Provide
    Arbitrary<List<HeapOp>> operations() {
        Arbitrary<Integer> key = Arbitraries.oneOf(
                Arbitraries.integers().between(-50, 50),
                Arbitraries.integers(),
                Arbitraries.of(Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1, Integer.MAX_VALUE));
        return operationList(key, 300);
    }

    @Provide
    Arbitrary<List<HeapOp>> duplicateHeavyOperations() {
        return operationList(Arbitraries.integers().between(0, 5), 200);
    }

    private static Arbitrary<List<HeapOp>> operationList(Arbitrary<Integer> key, int maxSize) {
        Arbitrary<Integer> heap = Arbitraries.integers().between(0, HEAPS - 1);
        Arbitrary<Integer> selector = Arbitraries.integers().between(0, 1_000);
        Arbitrary<Integer> delta = Arbitraries.oneOf(
                Arbitraries.integers().between(0, 50),
                Arbitraries.integers().between(0, Integer.MAX_VALUE),
                Arbitraries.integers().between(-5, -1));            // 키 증가 요청 → 거부되어야 함

        Arbitrary<HeapOp> op = Arbitraries.frequencyOf(
                Tuple.of(35, Combinators.combine(heap, key).as(HeapOp::insert)),
                Tuple.of(10, heap.map(HeapOp::findMin)),
                Tuple.of(20, heap.map(HeapOp::extractMin)),
                Tuple.of(15, Combinators.combine(heap, selector, delta).as(HeapOp::decreaseNode)),
                Tuple.of(8, Combinators.combine(heap, selector, delta).as(HeapOp::decreaseValue)),
                Tuple.of(7, Combinators.combine(heap, heap).as(HeapOp::meld)));
        return op.list().ofMinSize(1).ofMaxSize(maxSize);
    }
}
