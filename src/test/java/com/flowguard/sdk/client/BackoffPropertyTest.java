package com.flowguard.sdk.client;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackoffPropertyTest {

    @Property
    void backoffShouldNeverExceedThirtySeconds(@ForAll @IntRange(min = 0, max = 1000000) int attempt) {
        long backoff = FlagSseListener.calculateBackoff(attempt);
        assertTrue(backoff >= 1, "Backoff must be at least 1 second");
        assertTrue(backoff <= 30, "Backoff must be capped at 30 seconds");
    }

    @Property
    void negativeAttemptShouldReturnOneSecond(@ForAll @IntRange(min = -100000, max = -1) int attempt) {
        long backoff = FlagSseListener.calculateBackoff(attempt);
        assertEquals(1, backoff, "Negative attempts must default to 1 second");
    }

    @Property
    void backoffShouldBeExponentialBeforeCap(@ForAll @IntRange(min = 0, max = 4) int attempt) {
        long backoff = FlagSseListener.calculateBackoff(attempt);
        long expected = (long) Math.pow(2, attempt);
        assertEquals(expected, backoff, "Backoff should be exactly 2^attempt before cap");
    }

    @Property
    void backoffShouldCapAtThirtySecondsFromAttemptFiveOnwards(@ForAll @IntRange(min = 5, max = 1000000) int attempt) {
        long backoff = FlagSseListener.calculateBackoff(attempt);
        assertEquals(30, backoff, "Backoff must be exactly 30 seconds for attempt >= 5");
    }
}
