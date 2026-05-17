package com.flowguard.sdk.core.evaluator;

import com.flowguard.sdk.core.model.EvaluationResult;
import com.flowguard.sdk.core.model.Flag;
import com.flowguard.sdk.core.model.FlagRule;
import com.flowguard.sdk.core.util.MurmurHash3;

import java.util.Arrays;
import java.util.Map;

public class LocalEvaluator {

    /**
     * Evaluates flag with no user attributes.
     */
    public EvaluationResult evaluate(Flag flag, String userId) {
        return evaluate(flag, userId, Map.of());
    }

    /**
     * Evaluates flag with custom user attributes.
     * Order of operations:
     * 1. Enabled check.
     * 2. Rules check (AND logical gate).
     * 3. Rollout percentage check.
     */
    public EvaluationResult evaluate(Flag flag, String userId, Map<String, String> attributes) {
        // 1. Check globally enabled status
        if (!flag.enabled()) {
            return new EvaluationResult(false, "FLAG_DISABLED_GLOBALLY");
        }

        // 2. Rules check (AND logical gate)
        if (flag.rules() != null && !flag.rules().isEmpty()) {
            if (attributes == null) {
                return new EvaluationResult(false, "RULE_MISMATCH");
            }
            for (FlagRule rule : flag.rules()) {
                if (!evaluateRule(rule, attributes)) {
                    return new EvaluationResult(false, "RULE_MISMATCH");
                }
            }
        }

        // 3. Rollout percentage check
        int rolloutPercentage = flag.rolloutPercentage();
        if (rolloutPercentage <= 0) {
            return new EvaluationResult(false, "FLAG_DISABLED_FOR_ALL");
        }

        if (rolloutPercentage >= 100) {
            return new EvaluationResult(true, "FLAG_ENABLED_FOR_ALL");
        }

        int hash = MurmurHash3.hash32(flag.key() + userId);
        int bucket = (hash & Integer.MAX_VALUE) % 100;

        if (bucket < rolloutPercentage) {
            return new EvaluationResult(true, "USER_IN_ROLLOUT");
        } else {
            return new EvaluationResult(false, "USER_OUT_ROLLOUT");
        }
    }

    private boolean evaluateRule(FlagRule rule, Map<String, String> attributes) {
        String userValue = attributes.get(rule.attributeKey());
        if (userValue == null) {
            return false; // Missing attribute required by rule fails validation
        }

        String ruleValue = rule.attributeValue();
        if (ruleValue == null) {
            return false;
        }

        return switch (rule.operator()) {
            case EQUALS -> userValue.equalsIgnoreCase(ruleValue);
            case NOT_EQUALS -> !userValue.equalsIgnoreCase(ruleValue);
            case CONTAINS -> userValue.toLowerCase().contains(ruleValue.toLowerCase());
            case STARTS_WITH -> userValue.toLowerCase().startsWith(ruleValue.toLowerCase());
            case IN -> evaluateInOperator(userValue, ruleValue);
        };
    }

    private boolean evaluateInOperator(String userValue, String ruleValue) {
        return Arrays.stream(ruleValue.split(","))
                .map(String::trim)
                .anyMatch(val -> val.equalsIgnoreCase(userValue.trim()));
    }
}
