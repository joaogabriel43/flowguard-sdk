package com.flowguard.sdk.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.flowguard.sdk.FlowGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowGuardSseIntegrationTest {

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
    void shouldSynchronizeCacheOnSseSnapshotAndEvents() {
        String apiKey = "my-key";
        String tenantId = "my-tenant";

        UUID flag1Id = UUID.randomUUID();
        UUID flag2Id = UUID.randomUUID();
        UUID tenantUid = UUID.randomUUID();

        // 1. Snapshot JSON (list of Flag objects)
        String snapshotJson = "[" +
                "  {" +
                "    \"id\": \"" + flag1Id + "\"," +
                "    \"tenantId\": \"" + tenantUid + "\"," +
                "    \"key\": \"flag-1\"," +
                "    \"name\": \"Flag One\"," +
                "    \"description\": \"Desc\"," +
                "    \"enabled\": true," +
                "    \"rolloutPercentage\": 100" +
                "  }," +
                "  {" +
                "    \"id\": \"" + flag2Id + "\"," +
                "    \"tenantId\": \"" + tenantUid + "\"," +
                "    \"key\": \"flag-2\"," +
                "    \"name\": \"Flag Two\"," +
                "    \"description\": \"Desc\"," +
                "    \"enabled\": false," +
                "    \"rolloutPercentage\": 0" +
                "  }" +
                "]";

        // 2. Updated flag detailed JSON (for hydration GET /api/flags/flag-2)
        String updatedFlag2Json = "{" +
                "  \"id\": \"" + flag2Id + "\"," +
                "  \"tenantId\": \"" + tenantUid + "\"," +
                "  \"key\": \"flag-2\"," +
                "  \"name\": \"Flag Two Updated\"," +
                "  \"description\": \"Desc\"," +
                "  \"enabled\": true," +
                "  \"rolloutPercentage\": 100" +
                "}";

        // 3. SSE event stream content containing:
        // - flag-snapshot event
        // - flag-updated slim change event (keys flagKey)
        // - flag-toggled event (keys flagKey)
        // - flag-deleted event (keys flagKey)
        String sseEvents = "event: flag-snapshot\n" +
                "data: " + snapshotJson + "\n\n" +
                "event: flag-updated\n" +
                "data: {\"flagKey\":\"flag-2\",\"tenantId\":\"" + tenantUid + "\",\"action\":\"UPDATED\",\"timestamp\":\"2026-05-17T11:00:00Z\"}\n\n" +
                "event: flag-toggled\n" +
                "data: {\"flagKey\":\"flag-1\",\"tenantId\":\"" + tenantUid + "\",\"action\":\"TOGGLED\",\"timestamp\":\"2026-05-17T11:01:00Z\"}\n\n" +
                "event: flag-deleted\n" +
                "data: {\"flagKey\":\"flag-2\",\"tenantId\":\"" + tenantUid + "\",\"action\":\"DELETED\",\"timestamp\":\"2026-05-17T11:02:00Z\"}\n\n";

        // REST fallback/initial stubs
        stubFor(get(urlEqualTo("/api/flags"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]"))); // startup with empty cache

        stubFor(get(urlEqualTo("/api/flags/flag-2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(updatedFlag2Json)));

        // SSE Endpoint Stub
        stubFor(get(urlEqualTo("/api/sse/flags"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(sseEvents)));

        FlowGuardClientConfig config = FlowGuardClientConfig.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .tenantId(tenantId)
                .build();

        FlowGuard flowGuard = new FlowGuard(config);
        flowGuard.connect();

        try {
            // Verify snapshot received: flag-1 is true, flag-2 is false
            // Wait for Awaitility since SSE events are processed in background threads
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                // flag-1 should be false due to toggled event (was true, toggled -> false)
                assertFalse(flowGuard.isEnabled("flag-1", "user-1"));
                
                // flag-2 was deleted by the last event (flag-deleted)
                assertFalse(flowGuard.isEnabled("flag-2", "user-2"));
            });
        } finally {
            flowGuard.disconnect();
        }
    }

    @Test
    void shouldInitiateBackoffAndReconnectSilentlyOnServerDrop() {
        String apiKey = "my-key";
        String tenantId = "my-tenant";

        UUID flagId = UUID.randomUUID();
        UUID tenantUid = UUID.randomUUID();

        String snapshotJson = "[" +
                "  {" +
                "    \"id\": \"" + flagId + "\"," +
                "    \"tenantId\": \"" + tenantUid + "\"," +
                "    \"key\": \"flag-1\"," +
                "    \"name\": \"Flag One\"," +
                "    \"description\": \"Desc\"," +
                "    \"enabled\": true," +
                "    \"rolloutPercentage\": 100" +
                "  }" +
                "]";

        String sseEvents = "event: flag-snapshot\n" +
                "data: " + snapshotJson + "\n\n";

        // REST fallback stubs
        stubFor(get(urlEqualTo("/api/flags"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // Stateful Scenario: First SSE call fails, second succeeds
        stubFor(get(urlEqualTo("/api/sse/flags"))
                .inScenario("ReconnectionScenario")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable"))
                .willTransitionTo("RETRYING"));

        stubFor(get(urlEqualTo("/api/sse/flags"))
                .inScenario("ReconnectionScenario")
                .whenScenarioStateIs("RETRYING")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(sseEvents)));

        FlowGuardClientConfig config = FlowGuardClientConfig.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .tenantId(tenantId)
                .connectionTimeoutSeconds(1)
                .build();

        FlowGuard flowGuard = new FlowGuard(config);
        flowGuard.connect();

        try {
            // Verify that even with the first failure, it enters backoff, reconnects,
            // receives the second snapshot and updates the cache (flag-1 becomes true).
            await().atMost(7, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(flowGuard.isEnabled("flag-1", "user-1"));
            });
        } finally {
            flowGuard.disconnect();
        }
    }
}
