package com.flowguard.sdk.core.util;

import com.flowguard.sdk.core.evaluator.LocalEvaluator;
import com.flowguard.sdk.core.model.Flag;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MurmurHash3ParityPropertyTest {

    private final LocalEvaluator evaluator = new LocalEvaluator();

    @Property
    void rolloutIsConsistent(@ForAll String flagKey, @ForAll String userId, @ForAll @IntRange(min = 0, max = 100) int percentage) {
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), flagKey, "Flag", "Desc", true, percentage);

        boolean firstRun = evaluator.evaluate(flag, userId).enabled();
        boolean secondRun = evaluator.evaluate(flag, userId).enabled();
        
        // Assert that same inputs always return the same result
        assertTrue(firstRun == secondRun);
    }

    @Property
    void zeroPercentAlwaysFalse(@ForAll String flagKey, @ForAll String userId) {
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), flagKey, "Flag", "Desc", true, 0);
        assertFalse(evaluator.evaluate(flag, userId).enabled());
    }

    @Property
    void hundredPercentAlwaysTrue(@ForAll String flagKey, @ForAll String userId) {
        Flag flag = new Flag(UUID.randomUUID(), UUID.randomUUID(), flagKey, "Flag", "Desc", true, 100);
        assertTrue(evaluator.evaluate(flag, userId).enabled());
    }

    @Property
    void bucketPercentageRelationship(@ForAll String flagKey, @ForAll String userId, @ForAll @IntRange(min = 0, max = 99) int percentage) {
        Flag flagLower = new Flag(UUID.randomUUID(), UUID.randomUUID(), flagKey, "Flag", "Desc", true, percentage);
        Flag flagHigher = new Flag(UUID.randomUUID(), UUID.randomUUID(), flagKey, "Flag", "Desc", true, percentage + 1);

        boolean inLowerRollout = evaluator.evaluate(flagLower, userId).enabled();
        boolean inHigherRollout = evaluator.evaluate(flagHigher, userId).enabled();

        // A user who falls into a rollout of percentage X should also fall into a rollout of percentage X + 1
        if (inLowerRollout) {
            assertTrue(inHigherRollout);
        }
    }
}
