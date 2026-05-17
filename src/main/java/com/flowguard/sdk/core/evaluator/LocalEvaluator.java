package com.flowguard.sdk.core.evaluator;

import com.flowguard.sdk.core.model.EvaluationResult;
import com.flowguard.sdk.core.model.Flag;
import com.flowguard.sdk.core.util.MurmurHash3;

public class LocalEvaluator {

    public EvaluationResult evaluate(Flag flag, String userId) {
        if (!flag.enabled()) {
            return new EvaluationResult(false, "FLAG_DISABLED_GLOBALLY");
        }

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
}
