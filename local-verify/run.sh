#!/usr/bin/env bash
# Maven 없이 JUnit 5 테스트 소스를 실행하는 로컬 검증 스크립트 (JUnit API 최소 shim 사용).
# jqwik 테스트(net.jqwik 를 import 하는 파일)는 컴파일·실행 대상에서 제외된다.
#   HARNESS_SCALE=quick|default|long   JAVA_OPTS="-DincludeTags=known-gap"
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${LOCAL_OUT:-$ROOT/target/local-verify}"
rm -rf "$OUT"
mkdir -p "$OUT/classes"
{
  echo "$ROOT/src/main/java/Main.java"
  find "$ROOT/local-verify/shim" -name '*.java'
  echo "$ROOT/local-verify/LocalTestRunner.java"
  grep -L 'net\.jqwik' $(find "$ROOT/src/test/java" -name '*.java')
} > "$OUT/sources.txt"
javac -encoding UTF-8 -nowarn -d "$OUT/classes" @"$OUT/sources.txt"
exec java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -Dharness.failureDir="$OUT/failures" -Dharness.scale="${HARNESS_SCALE:-default}" ${JAVA_OPTS:-} \
  -cp "$OUT/classes:$ROOT/src/test/resources" LocalTestRunner "$OUT/classes"
