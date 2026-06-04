# Performance Analysis — Reading Load Test Results

This document is the **assignment deliverable**: how to interpret JMeter and Gatling output and use it to pinpoint performance bottlenecks. It's organized around (1) the metrics, (2) reading the reports, (3) recognizing specific bottleneck patterns, (4) the concrete bottlenecks **this** project will surface, and (5) optimization recipes.

---

## 1. The Metrics That Matter

Before reading any chart, internalize what these mean:

### Response time
- **Mean / average** — useful, but hides outliers. **Don't trust it alone.**
- **Median (p50)** — the typical user's experience.
- **p95** — 95% of requests are faster than this. The headline SLA metric for most APIs.
- **p99** — the slow tail. If p99 ≫ p95, you have **tail latency** issues (GC pauses, lock contention, occasional slow queries).
- **Max** — usually a one-off; don't fixate on it.

### Throughput
- **Requests per second (RPS / TPS)** under sustained load.
- A healthy system: throughput rises linearly with concurrency until a resource saturates, then **plateaus**.
- After saturation, adding more users only makes latency worse, not throughput.

### Error rate
- Below ~0.1% is normal noise. Above 1% is a real problem.
- Watch for **timeouts**, **5xx**, and **connection refused** — each points at a different bottleneck.

### Concurrency
- "100 users" doesn't mean 100 RPS — it means 100 simultaneous sessions, each typically pausing between requests.
- For real SLA work, think in RPS not "users".

---

## 2. Reading the JMeter Report

After running headless mode with `-e -o`, open `target/jmeter-report/index.html`.

### Key sections

**APDEX (Application Performance Index)**
A score from 0 to 1. >0.94 excellent, 0.85–0.94 good, 0.7–0.85 fair, <0.7 poor. Calculated per request label using configurable thresholds.

**Requests Summary**
The headline table:

| Label | # Samples | KO | Error % | Average | Median | 90% line | 95% line | 99% line | Min | Max | Throughput |

What to look for:
- **Sort by 95% line** — the worst-percentile-at-load endpoints jump to the top
- **Compare Average vs 95% vs 99%** — a 3× gap between 95 and 99 means tail latency
- **Error % column** — anything > 0 demands explanation
- **Throughput column** — sum across labels = the system's overall capacity

**Response Times Over Time chart**
The most diagnostic chart in the report. Watch for:
- **Rising line** → contention growing as concurrency increases
- **Sawtooth pattern** → GC or periodic batch jobs
- **Sudden cliff** → a resource hit a limit (thread pool, connection pool, OS file handles)

**Active Threads Over Time + Hits per Second**
If active threads keep rising but hits/sec is flat → the server is the bottleneck, not the load generator.

---

## 3. Reading the Gatling Report

Open `target/gatling/productloadsimulation-<timestamp>/index.html`.

### Key sections

**Global Information panel**
- Stats per request name: count, min, p50, p75, p95, p99, max, mean, KO/OK count
- The **"Response time percentiles over time"** chart is gold — colored bands for each percentile across the run.

**Response time distribution**
Histogram. A healthy endpoint shows a tight cluster near zero. A bimodal distribution (two humps) usually means a cache hit/miss split or two code paths.

**Active users over time**
Confirms your injection profile worked as intended.

**Detailed view per request**
Click any request name. You get: response-time chart, percentile bands, error breakdown, and a status-code table.

### Assertions panel
Defined in the simulation:
```java
global().responseTime().percentile3().lt(2000)   // p95 < 2s
global().successfulRequests().percent().gte(99.0)
```
Pass/fail at the top — these become the CI gate.

---

## 4. Recognizing Bottleneck Patterns

| Pattern in the report | Likely cause | Where to look |
|---|---|---|
| **p99 ≫ p95** (long tail) | GC pauses, lock contention, occasional slow queries | JVM GC logs, slow-query log |
| **Latency rises linearly with concurrency** | CPU-bound code or single-threaded section | CPU profile, thread dump under load |
| **Latency stays flat but throughput plateaus** | Resource saturation (DB pool, thread pool) | Connection-pool metrics, thread-pool gauge |
| **Sudden errors at a specific RPS** | Hard limit hit — pool exhaustion, OS limit | Connection refused, "pool empty" logs |
| **Memory grows unbounded** | Memory leak | Heap dump, eligible objects |
| **One endpoint dominates total time** | That endpoint is the bottleneck — focus there first | Sort by total time, profile that handler |
| **All endpoints slow together when one is hit hard** | Shared resource saturated (thread pool, DB) | Check which pool/queue is full |

---

## 5. Bottlenecks This Project Will Surface

When you run the load test as configured, expect to see these **three distinct patterns**:

### Pattern A — Thread-pool saturation (`/api/slow`)

**Symptom:**
- `/api/slow` p95 ≈ 500 ms when idle, but rises to several seconds under load
- Other endpoints' latency rises in lockstep with `/api/slow`'s load
- Active threads chart pegs at the configured max (50)

**Why:** `Thread.sleep(500)` holds a Tomcat worker thread for the full duration. With 20 RPS hitting it and 50 threads available, every thread is constantly tied up — new requests queue waiting for a worker.

**Confirm:**
- Look at Tomcat's `tomcat.threads.busy` actuator metric while the test runs
- Or: `curl http://localhost:8080/actuator/metrics/tomcat.threads.busy`

**Fix:**
- Make the endpoint async (`@Async` or reactive)
- Or raise `server.tomcat.threads.max` (treats the symptom, not the cause)
- Or remove the artificial sleep — typically this represents a synchronous external call that should be made non-blocking

---

### Pattern B — N+1 query problem (`/api/products/with-reviews`)

**Symptom:**
- `/api/products/with-reviews` p95 is 5–10× higher than `/api/products/with-reviews-optimized`
- Both return the same JSON shape and size — but one is much slower
- DB CPU is high when this endpoint is hit

**Why:** The naive implementation runs:
1. `SELECT * FROM products` — 1 query
2. For each of 100 products: `SELECT * FROM reviews WHERE product_id = ?` — 100 queries

That's 101 queries instead of one. The optimized endpoint uses `JOIN FETCH`:
```sql
SELECT p.*, r.*
FROM products p
LEFT JOIN reviews r ON r.product_id = p.id
```
One query, same data.

**Confirm:**
- Enable SQL logging: `spring.jpa.show-sql=true` and hit each endpoint once — you'll literally see 101 SELECTs in the log for the naive version.

**Fix:**
- `@Query("SELECT p FROM Product p JOIN FETCH p.reviews")` — already implemented as the `optimized` endpoint
- Or `@EntityGraph` annotation on the repository method

This is the **#1 most common Java performance bug**. Catching it via load testing is exactly why load testing exists.

---

### Pattern C — Unindexed search (`/api/products/search`)

**Symptom:**
- Latency on the search endpoint grows with the **size of the data set**, not just concurrency
- Acceptable with 100 products, painful with 100,000

**Why:** `findByNameContainingIgnoreCase` translates to `SELECT * FROM products WHERE LOWER(name) LIKE LOWER('%keyword%')`. A leading wildcard prevents any index from being used → full table scan every request.

**Fix:**
- Add a database **full-text index** (PostgreSQL `tsvector`, MySQL `FULLTEXT`)
- Or push search to **Elasticsearch / OpenSearch**
- Or, for prefix-only search, add a regular index and use `LIKE 'keyword%'` (no leading wildcard)

---

## 6. Sample Results Walkthrough

Here's what the JMeter summary looks like on a typical laptop running this project (your numbers will vary):

```
Label                                  | Samples | Avg(ms) | p95   | p99   | Errors
---------------------------------------+---------+---------+-------+-------+--------
GET /api/products/{id}                 |  3000   |    8    |   25  |   55  |  0.00%
GET /api/products                      |  3000   |   24    |   65  |  120  |  0.00%
GET /api/products/search               |  3000   |   45    |  140  |  220  |  0.00%
GET /api/products/with-reviews         |  3000   |  185    |  650  | 1100  |  0.00%   ← N+1
GET /api/products/with-reviews-optimized| 3000   |   38    |  110  |  190  |  0.00%
GET /api/slow                          |  3000   | 1850    | 4500  | 7200  |  2.10%   ← thread pool saturated
```

**Reading it:**

- `/api/products/{id}` and `/api/products` are healthy — single-digit-to-low-double-digit averages, clean tails.
- `/api/products/with-reviews` p95 of **650 ms** vs the optimized endpoint's **110 ms** — that's the **N+1 bug** showing up clearly.
- `/api/slow` p99 of **7.2 seconds** and **2.1% errors** — Tomcat workers exhausted, requests timing out. The thread pool is the bottleneck.
- The 2.1% error rate on `/api/slow` should match (in number) the requests that exceeded the 10s response timeout configured in the test plan.

The fix priorities, in order of impact:
1. **Fix the thread-pool saturation** (highest impact: it dragged everything down). Make `/api/slow` async or remove the blocking call.
2. **Fix the N+1**: swap to the JOIN FETCH endpoint, or update the naive endpoint to use it.
3. **Index the search column** — won't matter much at 100 rows, will matter a lot at 1M.

---

## 7. Going Further

The setup here is minimal but realistic. Real-world extensions:

- **Run from a separate machine.** Co-locating generator and server skews numbers — both compete for CPU.
- **Multi-node load generation.** Both JMeter (remote slaves) and Gatling (Gatling Enterprise) support distributed generators for high RPS.
- **Correlate with server-side metrics.** Pipe Spring Boot Actuator → Prometheus → Grafana while running tests. Latency spikes line up with CPU / GC / pool-usage spikes — that's where the real diagnosis happens.
- **CI integration.** Both tools fail their assertions on regressions, so they can run nightly and block deploys.
- **Soak tests.** A 30-minute load test catches things a 2-minute one doesn't — memory leaks, slow GC cycles, connection-pool churn.

---

## ✅ Summary

Load testing isn't about hitting a server hard. It's about producing **observable evidence** of where a system slows down under realistic concurrency. The output of this assignment is:

1. The numbers (response times, percentiles, throughput, errors)
2. The **interpretation** — which numbers indicate which bottleneck
3. The **fix** — a code or config change that, when retested, shows measurable improvement

That loop — measure, diagnose, fix, re-measure — is the entire performance-engineering discipline. The tools (JMeter, Gatling, anything else) are just the measurement step.
