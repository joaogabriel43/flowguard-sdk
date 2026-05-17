package com.flowguard.sdk.core.model;

import java.util.UUID;

public record Flag(
    UUID id,
    UUID tenantId,
    String key,
    String name,
    String description,
    boolean enabled,
    int rolloutPercentage
) {}
