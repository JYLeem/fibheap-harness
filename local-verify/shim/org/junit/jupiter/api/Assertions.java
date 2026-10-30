package org.junit.jupiter.api;

import java.util.Objects;
import org.junit.jupiter.api.function.Executable;

/**
 * 로컬 검증용 최소 shim. 실제 JUnit 5 Assertions 에 있는 시그니처만, 같은 의미로 구현한다.
 * (테스트 코드는 실제 JUnit 5 로도 그대로 컴파일된다)
 */
public final class Assertions {

    private Assertions() {}

    public static void assertEquals(Object expected, Object actual) { assertEquals(expected, actual, null); }
    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) fail(format(message, expected, actual));
    }
    public static void assertEquals(int expected, int actual) { assertEquals(expected, actual, (String) null); }
    public static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) fail(format(message, expected, actual));
    }
    public static void assertEquals(int expected, Integer actual) { assertEquals((Object) expected, (Object) actual, null); }
    public static void assertEquals(int expected, Integer actual, String message) { assertEquals((Object) expected, (Object) actual, message); }
    public static void assertEquals(Integer expected, int actual) { assertEquals((Object) expected, (Object) actual, null); }
    public static void assertEquals(Integer expected, int actual, String message) { assertEquals((Object) expected, (Object) actual, message); }
    public static void assertEquals(Integer expected, Integer actual) { assertEquals((Object) expected, (Object) actual, null); }
    public static void assertEquals(Integer expected, Integer actual, String message) { assertEquals((Object) expected, (Object) actual, message); }
    public static void assertEquals(long expected, long actual) { assertEquals(expected, actual, (String) null); }
    public static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) fail(format(message, expected, actual));
    }

    public static void assertTrue(boolean condition) { assertTrue(condition, null); }
    public static void assertTrue(boolean condition, String message) {
        if (!condition) fail(message == null ? "expected: <true> but was: <false>" : message);
    }
    public static void assertFalse(boolean condition) { assertFalse(condition, null); }
    public static void assertFalse(boolean condition, String message) {
        if (condition) fail(message == null ? "expected: <false> but was: <true>" : message);
    }

    public static void assertNull(Object actual) { assertNull(actual, null); }
    public static void assertNull(Object actual, String message) {
        if (actual != null) fail(format(message, null, actual));
    }
    public static void assertNotNull(Object actual) { assertNotNull(actual, null); }
    public static void assertNotNull(Object actual, String message) {
        if (actual == null) fail(message == null ? "expected: not <null>" : message);
    }

    public static void assertSame(Object expected, Object actual) { assertSame(expected, actual, null); }
    public static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) fail(format(message, expected, actual) + " (same instance)");
    }

    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {
        return assertThrows(expectedType, executable, null);
    }
    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable, String message) {
        try {
            executable.execute();
        } catch (Throwable actual) {
            if (expectedType.isInstance(actual)) return expectedType.cast(actual);
            AssertionError e = new AssertionError(prefix(message) + "Unexpected exception type thrown, expected: <"
                    + expectedType.getName() + "> but was: <" + actual.getClass().getName() + ">");
            e.initCause(actual);
            throw e;
        }
        throw new AssertionError(prefix(message) + "Expected " + expectedType.getName() + " to be thrown, but nothing was thrown.");
    }

    public static void assertDoesNotThrow(Executable executable) { assertDoesNotThrow(executable, null); }
    public static void assertDoesNotThrow(Executable executable, String message) {
        try {
            executable.execute();
        } catch (Throwable t) {
            AssertionError e = new AssertionError(prefix(message) + "Unexpected exception thrown: " + t);
            e.initCause(t);
            throw e;
        }
    }

    public static <V> V fail(String message) {
        throw new AssertionError(message);
    }

    private static String prefix(String message) {
        return message == null || message.isEmpty() ? "" : message + " ==> ";
    }

    private static String format(String message, Object expected, Object actual) {
        return prefix(message) + "expected: <" + expected + "> but was: <" + actual + ">";
    }
}
