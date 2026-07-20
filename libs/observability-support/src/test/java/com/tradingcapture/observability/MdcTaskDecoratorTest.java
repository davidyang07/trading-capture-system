package com.tradingcapture.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decorator captures the caller's MDC when {@code decorate} is called and re-applies it
 * on the thread that eventually runs the task (an executor hop), then restores whatever that
 * thread previously had. All four capture/restore branches are exercised.
 */
class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void propagatesCapturedContextAndRestoresAnEmptyThread() {
        MDC.put("correlationId", "abc-123");
        Map<String, String> seen = new HashMap<>();
        Runnable decorated = decorator.decorate(() -> seen.putAll(currentContext()));

        // Simulate a fresh executor thread with no MDC of its own.
        MDC.clear();
        decorated.run();

        assertThat(seen).containsEntry("correlationId", "abc-123");
        // previous (on the running thread) was empty -> restored to empty.
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void clearsContextWhenCallerHadNoneAndRestoresThePreviousContext() {
        // Captured while the caller had no MDC -> context is null.
        MDC.clear();
        Map<String, String> seen = new HashMap<>();
        Runnable decorated = decorator.decorate(() -> seen.putAll(currentContext()));

        // The running thread already carries its own context (previous is non-null).
        MDC.put("correlationId", "thread-local");
        decorated.run();

        // Inside the task the captured (empty) context wins.
        assertThat(seen).doesNotContainKey("correlationId");
        // After the task the running thread's own context is restored.
        assertThat(MDC.get("correlationId")).isEqualTo("thread-local");
    }

    private static Map<String, String> currentContext() {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return ctx == null ? Map.of() : ctx;
    }
}
