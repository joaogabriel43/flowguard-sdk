package com.flowguard.sdk.core.evaluator;

import com.flowguard.sdk.core.model.Flag;
import com.flowguard.sdk.core.model.FlagRule;
import com.flowguard.sdk.core.model.RuleOperator;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SegmentationParityPropertyTest {

    private final LocalEvaluator evaluator = new LocalEvaluator();

    @Property
    void propertyEvaluationNeverThrowsNpe(
            @ForAll String flagKey,
            @ForAll String userId,
            @ForAll @IntRange(min = 0, max = 100) int rollout,
            @ForAll("ruleList") List<FlagRule> rules,
            @ForAll("attributesMap") Map<String, String> attributes
    ) {
        Flag flag = new Flag(
                UUID.randomUUID(),
                UUID.randomUUID(),
                flagKey,
                "Random Flag",
                "Desc",
                true,
                rollout,
                rules
        );

        // Ensure we never get a NullPointerException or any unexpected exception
        assertDoesNotThrow(() -> evaluator.evaluate(flag, userId, attributes));
    }

    @Property
    void missingRequiredAttributesAlwaysFails(
            @ForAll String flagKey,
            @ForAll String userId,
            @ForAll("ruleListNotEmpty") List<FlagRule> rules
    ) {
        Flag flag = new Flag(
                UUID.randomUUID(),
                UUID.randomUUID(),
                flagKey,
                "Random Flag",
                "Desc",
                true,
                100,
                rules
        );

        // Empty attributes
        assertFalse(evaluator.evaluate(flag, userId, Map.of()).enabled());

        // Null attributes
        assertFalse(evaluator.evaluate(flag, userId, null).enabled());
    }

    @Provide
    Arbitrary<List<FlagRule>> ruleList() {
        return randomRule().list().ofMaxSize(5);
    }

    @Provide
    Arbitrary<List<FlagRule>> ruleListNotEmpty() {
        return randomRule().list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Map<String, String>> attributesMap() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().numeric().ofLength(5);
        Arbitrary<String> values = Arbitraries.strings().alpha().numeric().ofLength(5);
        return Arbitraries.maps(keys, values).ofMaxSize(10);
    }

    private Arbitrary<FlagRule> randomRule() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().numeric().ofLength(5);
        Arbitrary<RuleOperator> operators = Arbitraries.of(RuleOperator.class);
        Arbitrary<String> values = Arbitraries.strings().alpha().numeric().ofLength(5);
        return Combinators.combine(keys, operators, values).as(FlagRule::new);
    }
}
