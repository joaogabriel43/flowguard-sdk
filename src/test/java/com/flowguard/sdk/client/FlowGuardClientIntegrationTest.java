package com.flowguard.sdk.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.flowguard.sdk.FlowGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowGuardClientIntegrationTest {

    private WireMockServer wireMockServer;
    private String serverUrl;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        serverUrl = "http://localhost:" + wireMockServer.port();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void shouldLoadFlagsSuccessfullyWhenServerIsOnline() {
        // Given
        String apiKey = "my-api-key";
        String tenantId = "my-tenant";
        
        String flagsJson = "[" +
                "  {" +
                "    \"id\": \"c60fa236-4d2b-426b-9c76-5a4c5ea2ee1f\"," +
                "    \"tenantId\": \"d27fa536-4d2b-426b-9c76-5a4c5ea2ee2f\"," +
                "    \"key\": \"flag-prod-1\"," +
                "    \"name\": \"Prod Flag 1\"," +
                "    \"description\": \"Description\"," +
                "    \"enabled\": true," +
                "    \"rolloutPercentage\": 100" +
                "  }," +
                "  {" +
                "    \"id\": \"c60fa236-4d2b-426b-9c76-5a4c5ea2ee20\"," +
                "    \"tenantId\": \"d27fa536-4d2b-426b-9c76-5a4c5ea2ee2f\"," +
                "    \"key\": \"flag-prod-2\"," +
                "    \"name\": \"Prod Flag 2\"," +
                "    \"description\": \"Description\"," +
                "    \"enabled\": false," +
                "    \"rolloutPercentage\": 50" +
                "  }" +
                "]";

        stubFor(get(urlEqualTo("/api/flags"))
                .withHeader("Authorization", equalTo("Bearer " + apiKey))
                .withHeader("X-Tenant-ID", equalTo(tenantId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(flagsJson)));

        FlowGuardClientConfig config = FlowGuardClientConfig.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .tenantId(tenantId)
                .defaultFallback(false)
                .build();

        // When
        FlowGuard flowGuard = new FlowGuard(config);

        // Then
        assertTrue(flowGuard.isEnabled("flag-prod-1", "user-123"));
        assertFalse(flowGuard.isEnabled("flag-prod-2", "user-123")); // global disabled
        assertFalse(flowGuard.isEnabled("unknown-flag", "user-123")); // fallback false
    }

    @Test
    void shouldGracefullyFallbackWhenServerReturnsError() {
        // Given
        String apiKey = "my-api-key";
        String tenantId = "my-tenant";

        stubFor(get(urlEqualTo("/api/flags"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        FlowGuardClientConfig config = FlowGuardClientConfig.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .tenantId(tenantId)
                .defaultFallback(true) // fallback true
                .build();

        // When: Instantiation should succeed, log WARNING and use fallback strategy
        FlowGuard flowGuard = new FlowGuard(config);

        // Then
        assertTrue(flowGuard.isEnabled("any-flag", "user-123")); // falls back to true safely!
    }

    @Test
    void shouldGracefullyFallbackWhenServerTimesOut() {
        // Given
        String apiKey = "my-api-key";
        String tenantId = "my-tenant";

        stubFor(get(urlEqualTo("/api/flags"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000))); // 2 seconds delay

        FlowGuardClientConfig config = FlowGuardClientConfig.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .tenantId(tenantId)
                .connectionTimeoutSeconds(1) // 1 second connection/read timeout
                .readTimeoutSeconds(1)
                .defaultFallback(false) // fallback false
                .build();

        // When: Instantiation should succeed under 1s timeout, log WARNING and use fallback strategy
        FlowGuard flowGuard = new FlowGuard(config);

        // Then
        assertFalse(flowGuard.isEnabled("any-flag", "user-123")); // falls back to false safely!
    }
}
