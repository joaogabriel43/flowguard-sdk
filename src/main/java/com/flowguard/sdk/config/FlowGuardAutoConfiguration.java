package com.flowguard.sdk.config;

import com.flowguard.sdk.FlowGuard;
import com.flowguard.sdk.client.FlowGuardClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "flowguard", name = {"api-key", "tenant-id"})
public class FlowGuardAutoConfiguration {

    @Value("${flowguard.server-url:http://localhost:8080}")
    private String serverUrl;

    @Value("${flowguard.api-key}")
    private String apiKey;

    @Value("${flowguard.tenant-id}")
    private String tenantId;

    @Value("${flowguard.default-fallback:false}")
    private boolean defaultFallback;

    @Bean
    @ConditionalOnMissingBean
    public FlowGuard flowGuard() {
        FlowGuardClientConfig config = FlowGuardClientConfig.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .tenantId(tenantId)
                .defaultFallback(defaultFallback)
                .build();
        return new FlowGuard(config);
    }
}
