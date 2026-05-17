package com.flowguard.sdk.core.model;

public record FlagRule(
    String attributeKey,
    RuleOperator operator,
    String attributeValue
) {}
