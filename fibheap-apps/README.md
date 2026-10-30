# Fibonacci heap 응용 사례와 맞춤 구현

| 케이스 | 응용 | 힙 구현 | Fibonacci heap 이 맞는 이유 |
|---|---|---|---|
| 1 | Dijkstra — 물류센터 → 배송지 최단 시간 (`Dijkstra.java`) | `IndexedFibonacciHeap` | 간선마다 decreaseKey 가 최대 1번 → O(1) 덕분에 O(m + n log n) |
| 2 | 멀티 서버 작업 스케줄러 — 마감순 실행·긴급 처리·취소·장애 이관 (`JobScheduler.java`) | `MeldableFibonacciHeap<T>` | 장애 시 대기열 병합 O(1), 마감 앞당김 O(1), 취소 O(log n) |
| 3 | Prim — 캠퍼스 건물 광케이블 최소 비용 연결 (`PrimMST.java`) | `IndexedFibonacciHeap` | Dijkstra 와 같은 decreaseKey 중심 구조 |

## 두 구현의 차이

- `IndexedFibonacciHeap`: 원소 = 정점 번호(0..n-1), 노드를 배열로 저장. 정점 번호가 곧 핸들이라 노드 검색 비용이 없고,
  정점 상태(NEW / IN_HEAP / REMOVED)로 "확정된 정점" 판정과 잘못된 decreaseKey 거부를 함께 처리한다.
- `MeldableFibonacciHeap<T>`: 원소 = 임의 객체, insert 가 Handle 을 돌려준다. 같은 키는 삽입 순서(FIFO)로 정렬.
  delete / updateKey 지원. meld 후에도 O(1)을 지키려고 소속 힙을 union-find 토큰으로 추적해서,
  꺼낸 핸들·다른 힙의 핸들을 넘기면 IllegalArgumentException 으로 거부한다.

## 실행 (JDK 11+, IntelliJ 에서는 src 를 소스 루트로 두고 각 main 실행)

```bash
javac -encoding UTF-8 -d out src/*.java
java -cp out FibonacciApplications     # 세 케이스 데모
java -cp out ApplicationsTest          # 검증 (기준 알고리즘 비교 + 불변식 검사)
java -Xmx2g -cp out ApplicationsBenchmark   # 간이 성능 비교
```
