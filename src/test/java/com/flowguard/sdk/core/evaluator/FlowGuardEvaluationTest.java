package com.flowguard.sdk.core.evaluator;

import com.flowguard.sdk.FlowGuard;
import com.flowguard.sdk.cache.FallbackStrategy;
import com.flowguard.sdk.cache.FlagCache;
import com.flowguard.sdk.client.FlowGuardClient;
import com.flowguard.sdk.core.model.Flag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowGuardEvaluationTest {

    private FlagCache cache;
    private LocalEvaluator evaluator;
    private FallbackStrategy fallbackStrategy;

    @Mock
    private FlowGuardClient client;

    private FlowGuard flowGuard;

    @BeforeEach
    void setUp() {
        cache = new FlagCache();
        evaluator = new LocalEvaluator();
        fallbackStrategy = new FallbackStrategy(false); // default fallback false

        flowGuard = new FlowGuard(cache, evaluator, fallbackStrategy, client);
    }

    @Test
    void shouldReturnFallbackValueWhenFlagIsNotFound() {
        assertFalse(flowGuard.isEnabled("non-existent-flag", "user-1"));
        
        // Let's create one with fallback true to test
        FlowGuard flowGuardFallbackTrue = new FlowGuard(cache, evaluator, new FallbackStrategy(true), client);
        assertTrue(flowGuardFallbackTrue.isEnabled("non-existent-flag", "user-1"));
    }

    @Test
    void shouldReturnFalseWhenFlagIsDisabledGlobally() {
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), "flag-disabled", "Flag", "Desc", false, 100);
        cache.put("flag-disabled", flag);

        assertFalse(flowGuard.isEnabled("flag-disabled", "user-1"));
        assertFalse(flowGuard.isEnabled("flag-disabled", "user-2"));
    }

    @Test
    void shouldReturnFalseWhenRolloutIsZeroPercent() {
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), "flag-zero", "Flag", "Desc", true, 0);
        cache.put("flag-zero", flag);

        assertFalse(flowGuard.isEnabled("flag-zero", "user-1"));
        assertFalse(flowGuard.isEnabled("flag-zero", "user-2"));
    }

    @Test
    void shouldReturnTrueWhenRolloutIsHundredPercent() {
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), "flag-hundred", "Flag", "Desc", true, 100);
        cache.put("flag-hundred", flag);

        assertTrue(flowGuard.isEnabled("flag-hundred", "user-1"));
        assertTrue(flowGuard.isEnabled("flag-hundred", "user-2"));
    }

    @Test
    void shouldPerformIntermediateRolloutCorrectly() {
        // Rollout at 50%
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), "flag-half", "Flag", "Desc", true, 50);
        cache.put("flag-half", flag);

        // Under 50%, some users will be true, some will be false depending on the hash.
        // We will evaluate a few users to ensure it evaluates consistently.
        boolean resUser1 = flowGuard.isEnabled("flag-half", "user-abc");
        boolean resUser2 = flowGuard.isEnabled("flag-half", "user-xyz");
        
        // Assert that calling it again with the same parameters gives the exact same result (consistência)
        assertEquals(resUser1, flowGuard.isEnabled("flag-half", "user-abc"));
        assertEquals(resUser2, flowGuard.isEnabled("flag-half", "user-xyz"));
    }

    private void assertEquals(boolean expected, boolean actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
