import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Maven Central 에 접근할 수 없는 환경에서 JUnit 5 테스트 소스를 실행하기 위한 최소 러너.
 * @Test 메서드를 클래스마다 새 인스턴스로 실행하고, @Disabled / 제외 태그를 건너뛴다.
 * jqwik(@Property) 테스트는 실행하지 않는다.
 *
 *   -DexcludeTags=perf             (기본값)
 *   -DincludeTags=known-gap        지정 시 해당 태그가 붙은 테스트만 실행
 *   -Dclasses=EdgeCaseTest,...     지정 시 해당 클래스만 실행
 */
public class LocalTestRunner {

    public static void main(String[] args) throws Exception {
        Path classesDir = Paths.get(args[0]);
        Set<String> exclude = split(System.getProperty("excludeTags", "perf"));
        Set<String> include = split(System.getProperty("includeTags", ""));
        Set<String> onlyClasses = split(System.getProperty("classes", ""));

        List<String> classNames;
        try (Stream<Path> files = Files.list(classesDir)) {
            classNames = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .map(n -> n.substring(0, n.length() - 6))
                    .sorted()
                    .collect(Collectors.toList());
        }

        int passed = 0, failed = 0, skipped = 0;
        List<String> failures = new ArrayList<>();
        long start = System.nanoTime();
        for (String name : classNames) {
            if (!onlyClasses.isEmpty() && !onlyClasses.contains(name)) continue;
            Class<?> cls = Class.forName(name);
            List<Method> tests = Arrays.stream(cls.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(Test.class))
                    .sorted(Comparator.comparing(Method::getName))
                    .collect(Collectors.toList());
            if (tests.isEmpty() || Modifier.isAbstract(cls.getModifiers())) continue;
            for (Method m : tests) {
                String id = name + "." + m.getName();
                Set<String> tags = new HashSet<>();
                if (cls.getAnnotation(Tag.class) != null) tags.add(cls.getAnnotation(Tag.class).value());
                if (m.getAnnotation(Tag.class) != null) tags.add(m.getAnnotation(Tag.class).value());
                boolean disabled = cls.isAnnotationPresent(Disabled.class) || m.isAnnotationPresent(Disabled.class);
                boolean excluded = tags.stream().anyMatch(exclude::contains)
                        || (!include.isEmpty() && tags.stream().noneMatch(include::contains));
                if (disabled || excluded) {
                    skipped++;
                    continue;
                }
                long t0 = System.nanoTime();
                try {
                    var ctor = cls.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object instance = ctor.newInstance();
                    m.setAccessible(true);
                    m.invoke(instance);
                    passed++;
                    System.out.printf("[PASS] %-75s %6d ms%n", id, (System.nanoTime() - t0) / 1_000_000);
                } catch (InvocationTargetException e) {
                    failed++;
                    Throwable cause = e.getCause();
                    System.out.printf("[FAIL] %s%n       %s%n", id, indent(String.valueOf(cause)));
                    failures.add(id + " : " + firstLine(String.valueOf(cause)));
                }
            }
        }
        System.out.println("------------------------------------------------------------");
        System.out.printf("통과 %d, 실패 %d, 건너뜀 %d  (%.1f s)%n", passed, failed, skipped,
                (System.nanoTime() - start) / 1e9);
        failures.forEach(f -> System.out.println("  FAILED " + f));
        System.exit(failed == 0 ? 0 : 1);
    }

    private static Set<String> split(String s) {
        Set<String> set = new HashSet<>();
        for (String part : s.split(",")) if (!part.isBlank()) set.add(part.trim());
        return set;
    }

    private static String indent(String s) {
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 40); i++) {
            if (i > 0) sb.append("\n       ");
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
