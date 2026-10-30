# Fibonacci Heap 테스트 하네스

![test](https://github.com/JYLeem/fibheap-harness/actions/workflows/test.yml/badge.svg)

고급알고리즘 수업에서 구현한 피보나치 힙(`src/main/java/Main.java`)이 정말 맞게 동작하는지 검증하려고 만든 테스트 하네스입니다.
힙 구현은 그대로 두고, 테스트만으로 버그를 얼마나 잡아낼 수 있는지에 집중했습니다.

| 지표 | 결과 |
|---|---|
| 테스트 | 87개 모두 통과 (힙 84개 + 하네스가 찾아낸 계약 결함 회귀 3개) |
| 표적 변이 테스트 | 29개 중 27개 탐지, 남은 2개는 동작이 같은 등가 변이 |
| 기준 모델 | `java.util.PriorityQueue`와 매 연산 결과 비교 |
| 실패 시 | 무작위 시퀀스를 ddmin으로 최소 재현 사례까지 줄여 파일로 저장 → 회귀 테스트로 재사용 |
| 찾아낸 결함 | 이미 꺼낸 노드, 다른 힙의 노드, 병합되어 사라진 힙을 통한 `decreaseKey`가 거부되지 않음 → 소속 힙 토큰(union-find)으로 수정 |
| 응용 | `fibheap-apps/`: Dijkstra, Prim, 작업 스케줄러에 적용하고 Bellman-Ford · Kruskal · binary heap과 비교 (13개 테스트) |

## CI

`.github/workflows/test.yml`

- **push / PR마다** 전체 테스트 실행 (외부 의존성 없이 `local-verify/run.sh`)
- **Maven 잡**에서 jqwik 속성 기반 테스트와 JaCoCo 커버리지 리포트 생성
- **매일 새벽** 큰 규모(`long`)로 퍼즈 테스트, 실패하면 최소 재현 파일을 아티팩트로 업로드
- **수동 실행** 시 표적 변이 29개를 돌려 테스트가 버그를 잡는지 확인

## 상세

`src/main/java/Main.java`(기존 구현)는 수정하지 않았다. 테스트는 모두 `src/test/java`에 있으며,
Main 이 기본 패키지라서 테스트도 기본 패키지에 둔다 (package-private `Main.FibonacciHeap` 접근).

## 구성

| 파일 | 기법 | 역할 |
|---|---|---|
| `HeapInvariantChecker` | 1 | 구현의 validate() 와 독립된 불변식 검사기 (13개 규칙, 손상 구조에서도 종료) |
| `InvariantCheckerTest` | 1 | 정상 구조 통과 + 의도적 손상을 규칙별로 탐지하는지 검사기 자체 검증 |
| `ReferenceModel`, `HeapHarness` | 2·3 | PriorityQueue 기준 모델 + 노드 핸들 표, 연산 실행·비교기 |
| `DifferentialTest` | 2 | PriorityQueue 와 직접 비교 + 무작위 시퀀스 비교 |
| `StatefulRandomTest`, `StatefulPropertyTest`(jqwik) | 3 | 여러 힙에 대한 무작위 연산 시퀀스, 매 연산 검사 |
| `EdgeCaseTest`, `KnownGapTest` | 4 | 경계·예외 계약, 사전조건 위반(@Tag known-gap) |
| `tools/MutantRunner.java`, `tools/mutants.txt`, pom 의 PIT | 5 | 표적 변이 29개 + PIT |
| `MetamorphicTest` | 6 | 메타모픽 관계 7개 |
| `HeapFuzzTest`, `FileParsingFuzzTest`, `FileParsingTest`, `CommandOracle` | 7 | 연산 퍼즈 / 파일 파싱 퍼즈 분리 |
| `SequenceMinimizer`, `FailureReporter`, `MinimizerTest`, `RegressionTest` | 8 | ddmin 축소, 재현 파일 저장, 회귀 재실행 |
| `BranchTargetedTest`, pom 의 JaCoCo | 9 | 분기별 표적 테스트 + 커버리지 리포트 |
| `perf-jmh/`, `PerfRunner` | 10 | JMH 벤치마크 / 의존성 없는 간이 측정기 |

## 실행

```bash
mvn test                                   # JUnit 5 + jqwik, JaCoCo 리포트 → target/site/jacoco/index.html
mvn test -Dharness.scale=quick|long        # 규모 조절 (long = 야간 퍼즈)
mvn test -Dharness.seed=20003              # 실패 메시지의 seed 하나만 재실행
mvn test -Dgroups=known-gap -Dharness.excludedGroups=perf   # 계약 결함 회귀 테스트만 실행
mvn test-compile org.pitest:pitest-maven:mutationCoverage   # PIT → target/pit-reports/index.html
java tools/MutantRunner.java --cmd "mvn -q -o test"         # 표적 변이 (Maven)
cd perf-jmh && mvn -q package && java -jar target/benchmarks.jar   # JMH

# Maven 없이 (JUnit API 최소 shim 사용, jqwik 제외)
./local-verify/run.sh
java tools/MutantRunner.java               # 기본 명령이 local-verify/run.sh
java -cp target/local-verify/classes PerfRunner 1000 10000 100000
```

무작위 테스트가 실패하면 `target/harness-failures/` 에 `<이름>-seed<N>.min.ops`(최소 재현) 와
`.full.txt`(전체 기록)가 생긴다. `.min.ops` 를 `src/test/resources/regressions/` 로 복사하면 회귀 테스트가 된다.
