package com.example.library;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;

/**
 * ============================================================================
 * INTEGRATION TEST #5 — PricingClient ↔ External HTTP API (via WireMock)
 * ============================================================================
 *
 * What this validates:
 *   • The PricingClient correctly builds the URL and parses the JSON response
 *   • HTTP error codes (404, 500) are mapped to the right Java exceptions
 *   • No live external dependency is required — WireMock acts as a fake
 *     pricing service running in-process on a local port.
 *
 * Environment setup:
 *   • A WireMockServer is started on a fixed port (8089) before tests
 *   • @DynamicPropertySource rewires the `pricing.api.url` property so
 *     PricingClient calls http://localhost:8089 instead of the real API
 *   • Each test sets up its own stubs (stubFor) and verifies the requests
 *
 * This is the canonical pattern for "integration testing an external HTTP
 * collaborator without depending on the real service."
 */
@SpringBootTest
class PricingClientWireMockIT {

    private static final WireMockServer wireMock = new WireMockServer(options().port(8089));

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();   // clean slate between tests
    }

    /**
     * Re-route the pricing.api.url at test-context creation time so PricingClient
     * is built pointing at our local WireMock instance.
     */
    @DynamicPropertySource
    static void overridePricingUrl(DynamicPropertyRegistry registry) {
        registry.add("pricing.api.url", () -> "http://localhost:8089");
    }

    @Autowired
    private PricingClient pricingClient;

    @Test
    @DisplayName("Successful 200 response → returns parsed priceCents")
    void successfulResponse_returnsParsedPrice() {
        wireMock.stubFor(get(urlEqualTo("/prices/9780132350884"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "isbn":"9780132350884",
                                  "priceCents": 4250
                                }
                                """)));

        int price = pricingClient.fetchPriceCents("9780132350884");
        assertThat(price).isEqualTo(4250);

        // Verify the client really hit the expected URL
        wireMock.verify(getRequestedFor(urlEqualTo("/prices/9780132350884")));
    }

    @Test
    @DisplayName("404 response → throws PricingNotFoundException")
    void notFoundResponse_throwsPricingNotFound() {
        wireMock.stubFor(get(urlEqualTo("/prices/0000000000000"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> pricingClient.fetchPriceCents("0000000000000"))
                .isInstanceOf(PricingClient.PricingNotFoundException.class)
                .hasMessageContaining("0000000000000");
    }

    @Test
    @DisplayName("500 response → throws generic PricingServiceException")
    void serverError_throwsPricingServiceException() {
        wireMock.stubFor(get(urlEqualTo("/prices/9780132350884"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> pricingClient.fetchPriceCents("9780132350884"))
                .isInstanceOf(PricingClient.PricingServiceException.class);
    }

    @Test
    @DisplayName("Slow response is still handled (no built-in timeout in this demo)")
    void slowResponse_eventuallyReturns() {
        wireMock.stubFor(get(urlEqualTo("/prices/9780321125217"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(500)   // simulate slow upstream
                        .withBody("{\"isbn\":\"9780321125217\",\"priceCents\":1234}")));

        int price = pricingClient.fetchPriceCents("9780321125217");
        assertThat(price).isEqualTo(1234);
    }
}
