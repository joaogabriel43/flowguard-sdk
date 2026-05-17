package com.flowguard.sdk.core.model;

public record EvaluationResult(
    boolean enabled,
    String reason
) {}
