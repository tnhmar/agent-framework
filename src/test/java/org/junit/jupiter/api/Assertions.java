package org.junit.jupiter.api;



/**
 * Minimal JUnit 5 Assertions shim — pure JDK, no external dependencies.
 * Throws AssertionError on failure, matching JUnit 5 semantics exactly.
 */
public final class Assertions {

    private Assertions() {}

    public static void assertEquals(Object expected, Object actual) {
        if (!equal(expected, actual))
            fail("expected: <" + expected + "> but was: <" + actual + ">");
    }
    public static void assertEquals(long expected, long actual) {
        if (expected != actual) fail("expected: <" + expected + "> but was: <" + actual + ">");
    }
    public static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta)
            fail("expected: <" + expected + "> but was: <" + actual + "> (delta=" + delta + ")");
    }
    public static void assertEquals(int expected, int actual) {
        if (expected != actual) fail("expected: <" + expected + "> but was: <" + actual + ">");
    }
    public static void assertEquals(Object expected, Object actual, String msg) {
        if (!equal(expected, actual))
            fail(msg + " ==> expected: <" + expected + "> but was: <" + actual + ">");
    }
    public static void assertEquals(long expected, long actual, String msg) {
        if (expected != actual) fail(msg + " ==> expected: <" + expected + "> but was: <" + actual + ">");
    }
    public static void assertNotEquals(Object unexpected, Object actual) {
        if (equal(unexpected, actual)) fail("expected: not <" + unexpected + ">");
    }
    public static void assertTrue(boolean condition) {
        if (!condition) fail("expected: true");
    }
    public static void assertTrue(boolean condition, String msg) {
        if (!condition) fail(msg);
    }
    public static void assertFalse(boolean condition) {
        if (condition) fail("expected: false");
    }
    public static void assertFalse(boolean condition, String msg) {
        if (condition) fail(msg);
    }
    public static void assertNotNull(Object obj) {
        if (obj == null) fail("expected: not null");
    }
    public static void assertNotNull(Object obj, String msg) {
        if (obj == null) fail(msg);
    }
    public static void assertNull(Object obj) {
        if (obj != null) fail("expected: null but was: <" + obj + ">");
    }
    public static void assertDoesNotThrow(Executable exec) {
        try { exec.execute(); }
        catch (Throwable t) { fail("Unexpected exception: " + t.getClass().getName() + ": " + t.getMessage()); }
    }
    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable exec) {
        try {
            exec.execute();
            fail("Expected exception of type " + expectedType.getName() + " but nothing was thrown");
            return null;
        } catch (Throwable t) {
            if (!expectedType.isInstance(t))
                fail("Expected: " + expectedType.getName() + " but was: " + t.getClass().getName());
            return expectedType.cast(t);
        }
    }
    public static void assertSame(Object expected, Object actual) {
        if (expected != actual) fail("expected: same <" + expected + "> but was: <" + actual + ">");
    }
    public static void fail(String msg) { throw new AssertionError(msg); }

    public static void assertEquals(double expected, double actual, double delta, String msg) {
        if (Math.abs(expected - actual) > delta)
            fail(msg + " ==> expected: <" + expected + "> but was: <" + actual + "> (delta=" + delta + ")");
    }
    @SuppressWarnings("unchecked")
    public static void assertThrows(Class<?> expectedType, Executable exec, String msg) {
        assertThrows((Class<Throwable>)expectedType, exec);
    }

    private static boolean equal(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}
