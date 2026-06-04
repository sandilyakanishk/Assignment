package loadtest;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * ============================================================================
 * GATLING LOAD TEST SIMULATION (Java DSL)
 * ============================================================================
 *
 * Run: mvn gatling:test
 *
 * Generates an HTML report in target/gatling/<simulation-name>-<timestamp>/
 * index.html with full charts: response time over time, throughput, percentiles
 * per request, error breakdown.
 *
 * Scenarios (run in PARALLEL with different load shapes):
 *   1) browseScenario       — typical user: list → detail → search
 *   2) reviewsScenario      — hits the N+1 endpoint, then the optimized one
 *   3) slowEndpointScenario — pounds /api/slow to demonstrate thread saturation
 *   4) writeScenario        — POSTs new products
 */
public class ProductLoadSimulation extends Simulation {

    // ----------- Protocol -----------
    private static final HttpProtocolBuilder HTTP =
            http.baseUrl("http://localhost:8080")
                .acceptHeader("application/json")
                .contentTypeHeader("application/json")
                .userAgentHeader("Gatling/3.11");

    // ----------- Scenarios -----------

    private static final ScenarioBuilder BROWSE = scenario("Browse")
            .exec(http("List all products").get("/api/products")
                  .check(status().is(200)))
            .pause(1)
            .exec(http("Get product 1").get("/api/products/1")
                  .check(status().is(200)))
            .pause(Duration.ofMillis(500))
            .exec(http("Search 'phone'").get("/api/products/search?keyword=phone")
                  .check(status().is(200)));

    private static final ScenarioBuilder REVIEWS = scenario("Reviews (N+1 vs optimized)")
            .exec(http("Naive N+1 endpoint").get("/api/products/with-reviews")
                  .check(status().is(200)))
            .pause(1)
            .exec(http("Optimized endpoint").get("/api/products/with-reviews-optimized")
                  .check(status().is(200)));

    private static final ScenarioBuilder SLOW = scenario("Slow endpoint stress")
            .exec(http("Slow 500ms endpoint").get("/api/slow")
                  .check(status().is(200)));

    private static final ScenarioBuilder WRITES = scenario("Writes")
            .exec(http("Create product").post("/api/products")
                  .body(StringBody("""
                          {
                            "name": "LoadTest-${__counter}",
                            "category": "Test",
                            "priceCents": 999
                          }
                          """))
                  .check(status().is(200)));

    // ----------- Injection profile -----------
    {
        setUp(
                // Browse: ramps from 0 → 50 users over 30s, then stays at 50 for 1 min
                BROWSE.injectOpen(
                        rampUsers(50).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofMinutes(1))
                ),
                // Reviews: low rate, focused on the bottleneck
                REVIEWS.injectOpen(
                        constantUsersPerSec(2).during(Duration.ofMinutes(1))
                ),
                // Slow: this is the one designed to saturate the thread pool
                SLOW.injectOpen(
                        constantUsersPerSec(20).during(Duration.ofMinutes(1))
                ),
                // Writes: moderate, mixed in
                WRITES.injectOpen(
                        constantUsersPerSec(5).during(Duration.ofMinutes(1))
                )
        )
        .protocols(HTTP)
        .assertions(
                // Pass/fail criteria — surface failures in CI
                global().responseTime().percentile3().lt(2000),  // p95 < 2s
                global().successfulRequests().percent().gte(99.0)
        );
    }
}
