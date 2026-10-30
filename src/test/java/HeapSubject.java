import java.util.Set;

/**
 * 테스트 하네스가 다루는 "테스트 대상 힙"의 최소 인터페이스.
 *
 * 실제 구현(Main.FibonacciHeap)은 {@link FibHeapSubject}가 감싸고,
 * 최소화기(minimizer) 자체를 검증할 때는 의도적으로 결함을 넣은 래퍼를 끼울 수 있다.
 * 노드 핸들은 구현의 Node 객체를 그대로 Object 로 전달한다(동일성 비교).
 */
interface HeapSubject {

    /** 키 삽입 후 노드 핸들 반환 */
    Object insert(int key);

    /** 최솟값 조회 (빈 힙이면 null) */
    Integer findMin();

    /** 최솟값 삭제 (빈 힙이면 null) */
    Integer extractMin();

    /** extractMin 이 제거할 노드(현재 min 노드). 빈 힙이면 null */
    Object minHandle();

    /** 노드 핸들로 키 감소 */
    void decreaseKey(Object handle, int newKey);

    /** 값으로 키 감소 (구현 계약: oldKey 를 가진 노드 중 먼저 찾은 노드 하나) */
    void decreaseKeyByValue(int oldKey, int newKey);

    /** other 를 this 로 병합 (구현 계약: other 는 빈 힙이 됨, 자기 자신이면 무시) */
    void meld(HeapSubject other);

    int size();

    int keyOf(Object handle);

    /** 구조적 불변식 검사. 위반 시 AssertionError */
    void checkInvariants();

    /** 힙에서 도달 가능한 모든 노드 (identity set) */
    Set<Object> reachableHandles();
}
