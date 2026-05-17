package com.flowguard.sdk.core.evaluator;

import com.flowguard.sdk.core.model.EvaluationResult;
import com.flowguard.sdk.core.model.Flag;
import com.flowguard.sdk.core.model.FlagRule;
import com.flowguard.sdk.core.model.RuleOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalEvaluatorTest {

    private LocalEvaluator evaluator;
    private UUID flagId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        evaluator = new LocalEvaluator();
        flagId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
    }

    private Flag createFlagWithRules(List<FlagRule> rules) {
        return new Flag(
                flagId,
                tenantId,
                "test-flag",
                "Test Flag",
                "Description",
                true,
                100, // 100% rollout to bypass hash checks during rule testing
                rules
        );
    }

    @Test
    void shouldEvaluateEqualsOperator() {
        FlagRule rule = new FlagRule("plan", RuleOperator.EQUALS, "premium");
        Flag flag = createFlagWithRules(List.of(rule));

        // Positive case (match)
        EvaluationResult res1 = evaluator.evaluate(flag, "user-1", Map.of("plan", "premium"));
        assertTrue(res1.enabled());

        // Positive case (case insensitivity)
        EvaluationResult res2 = evaluator.evaluate(flag, "user-1", Map.of("plan", "PREMIUM"));
        assertTrue(res2.enabled());

        // Negative case (mismatch)
        EvaluationResult res3 = evaluator.evaluate(flag, "user-1", Map.of("plan", "free"));
        assertFalse(res3.enabled());
    }

    @Test
    void shouldEvaluateNotEqualsOperator() {
        FlagRule rule = new FlagRule("plan", RuleOperator.NOT_EQUALS, "free");
        Flag flag = createFlagWithRules(List.of(rule));

        // Positive case (match)
        EvaluationResult res1 = evaluator.evaluate(flag, "user-1", Map.of("plan", "premium"));
        assertTrue(res1.enabled());

        // Negative case (mismatch)
        EvaluationResult res2 = evaluator.evaluate(flag, "user-1", Map.of("plan", "free"));
        assertFalse(res2.enabled());

        // Negative case (case insensitivity mismatch)
        EvaluationResult res3 = evaluator.evaluate(flag, "user-1", Map.of("plan", "FREE"));
        assertFalse(res3.enabled());
    }

    @Test
    void shouldEvaluateContainsOperator() {
        FlagRule rule = new FlagRule("email", RuleOperator.CONTAINS, "@flowguard.com");
        Flag flag = createFlagWithRules(List.of(rule));

        // Positive case
        EvaluationResult res1 = evaluator.evaluate(flag, "user-1", Map.of("email", "admin@flowguard.com"));
        assertTrue(res1.enabled());

        // Positive case (case insensitivity)
        EvaluationResult res2 = evaluator.evaluate(flag, "user-1", Map.of("email", "ADMIN@FLOWGUARD.COM"));
        assertTrue(res2.enabled());

        // Negative case
        EvaluationResult res3 = evaluator.evaluate(flag, "user-1", Map.of("email", "user@gmail.com"));
        assertFalse(res3.enabled());
    }

    @Test
    void shouldEvaluateStartsWithOperator() {
        FlagRule rule = new FlagRule("region", RuleOperator.STARTS_WITH, "us-");
        Flag flag = createFlagWithRules(List.of(rule));

        // Positive case
        EvaluationResult res1 = evaluator.evaluate(flag, "user-1", Map.of("region", "us-east-1"));
        assertTrue(res1.enabled());

        // Positive case (case insensitivity)
        EvaluationResult res2 = evaluator.evaluate(flag, "user-1", Map.of("region", "US-west-2"));
        assertTrue(res2.enabled());

        // Negative case
        EvaluationResult res3 = evaluator.evaluate(flag, "user-1", Map.of("region", "br-south-1"));
        assertFalse(res3.enabled());
    }

    @Test
    void shouldEvaluateInOperator() {
        FlagRule rule = new FlagRule("plan", RuleOperator.IN, "premium, enterprise, partner");
        Flag flag = createFlagWithRules(List.of(rule));

        // Positive cases with trim
        assertTrue(evaluator.evaluate(flag, "user-1", Map.of("plan", "premium")).enabled());
        assertTrue(evaluator.evaluate(flag, "user-1", Map.of("plan", "enterprise")).enabled());
        assertTrue(evaluator.evaluate(flag, "user-1", Map.of("plan", "  partner  ")).enabled()); // trim check
        assertTrue(evaluator.evaluate(flag, "user-1", Map.of("plan", "PREMIUM")).enabled()); // case-insensitive check

        // Negative case
        assertFalse(evaluator.evaluate(flag, "user-1", Map.of("plan", "free")).enabled());
    }

    @Test
    void shouldFailWhenRequiredAttributeIsMissing() {
        FlagRule rule = new FlagRule("plan", RuleOperator.EQUALS, "premium");
        Flag flag = createFlagWithRules(List.of(rule));

        // Attributes map empty
        assertFalse(evaluator.evaluate(flag, "user-1", Map.of()).enabled());

        // Attributes map null
        assertFalse(evaluator.evaluate(flag, "user-1", null).enabled());
    }

    @Test
    void shouldEvaluateMultipleRulesUsingAndGateLogic() {
        FlagRule rule1 = new FlagRule("plan", RuleOperator.EQUALS, "premium");
        FlagRule rule2 = new FlagRule("region", RuleOperator.STARTS_WITH, "br-");
        Flag flag = createFlagWithRules(List.of(rule1, rule2));

        // Positive: both satisfied
        assertTrue(evaluator.evaluate(flag, "user-1", Map.of("plan", "premium", "region", "br-south")).enabled());

        // Negative: plan satisfies but region does not
        assertFalse(evaluator.evaluate(flag, "user-1", Map.of("plan", "premium", "region", "us-east")).enabled());

        // Negative: region satisfies but plan does not
        assertFalse(evaluator.evaluate(flag, "user-1", Map.of("plan", "free", "region", "br-south")).enabled());
    }
}
