package com.flowguard.sdk.core.model;

import java.util.List;
import java.util.UUID;

public record Flag(
    UUID id,
    UUID tenantId,
    String key,
    String name,
    String description,
    boolean enabled,
    int rolloutPercentage,
    List<FlagRule> rules
) {

    /**
     * Canonical constructor with defensive handling of null rules list.
     * Prevents NullPointerException when deserializing older server responses.
     */
    public Flag(
        UUID id,
        UUID tenantId,
        String key,
        String name,
        String description,
        boolean enabled,
        int rolloutPercentage,
        List<FlagRule> rules
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.rules = rules != null ? List.copyOf(rules) : List.of();
    }
}
