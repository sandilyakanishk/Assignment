# Load Testing — JMeter & Gatling

A complete load-testing assignment in one Maven project:

1. A **Spring Boot target application** with endpoints exhibiting realistic, recognizable performance bottlenecks
2. A **JMeter test plan** (`.jmx`)
3. A **Gatling simulation** (Java DSL)
4. An **`ANALYSIS.md`** walkthrough explaining how to interpret results and pinpoint bottlenecks

> 📘 **Read `ANALYSIS.md` after running the tests** — it's the heart of this assignment.

---

## 📂 Project Structure

```
load-testing-assignment/
├── pom.xml
├── README.md
├── ANALYSIS.md
├── jmeter/
│   └── library-load-test.jmx              # JMeter test plan
├── src/
│   ├── main/java/com/example/loadtest/    # Target Spring Boot app
│   │   ├── LoadTestApplication.java
│   │   ├── Product.java
│   │   ├── Review.java
│   │   ├── ProductRepository.java
│   │   ├── ProductController.java         # Endpoints with varied perf profiles
│   │   └── DataLoader.java                # Seeds 100 products × 5 reviews
│   ├── main/resources/
│   │   └── application.properties
│   └── test/java/loadtest/
│       └── ProductLoadSimulation.java     # Gatling simulation (Java DSL)
```

---

## 🎯 Target Application — Endpoints Under Test

| Endpoint | Expected behavior | Bottleneck demonstrated |
|---|---|---|
| `GET /api/products/{id}` | Fast PK lookup, ~5 ms | Baseline — should stay fast |
| `GET /api/products` | List 100 products, ~20 ms | Payload size |
| `GET /api/products/search?keyword=x` | LIKE query, ~50–200 ms | Unindexed scan |
| `GET /api/products/with-reviews` | **N+1 problem** | 101 SQL queries per request |
| `GET /api/products/with-reviews-optimized` | Same data, single query | Fix for the above |
| `GET /api/slow` | `Thread.sleep(500)` | **Thread-pool saturation** |
| `POST /api/products` | Insert | Connection-pool / lock contention |

Tomcat's thread pool is intentionally configured small (`max=50`) so the slow endpoint surfaces saturation at a moderate concurrency level — exactly the kind of bug load testing is supposed to catch.

---

## 🔧 Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Apache JMeter 5.6+** — for the JMeter test plan ([download](https://jmeter.apache.org/download_jmeter.cgi))
- **Gatling**: no separate install — runs via the Maven plugin

---

## 🚀 Step 1 — Start the Target Application

```bash
mvn spring-boot:run
```

Wait for: `Seeded 100 products with reviews.` (printed by the data loader).

The app listens on `http://localhost:8080`. Smoke test it:
```bash
curl http://localhost:8080/api/products/1
curl "http://localhost:8080/api/products/search?keyword=Electronics"
curl http://localhost:8080/actuator/health
```

Leave the app running for the load tests.

---

## 🧪 Step 2 — Run JMeter

### Option A: GUI mode (good for editing, not for running)
```bash
jmeter -t jmeter/library-load-test.jmx
```
Then click ▶ in the toolbar.

### Option B: Headless mode (what you should actually use)
```bash
mkdir -p target
jmeter -n \
  -t jmeter/library-load-test.jmx \
  -l target/jmeter-results.jtl \
  -e -o target/jmeter-report
```

- `-n` non-GUI
- `-t` test plan
- `-l` raw results (JTL)
- `-e -o <dir>` generate the HTML dashboard

Open `target/jmeter-report/index.html` in a browser.

### Test profile
- 100 virtual users
- 30-second ramp-up
- 5 loops per user → ~3,000 total requests across the 6 endpoints
- Random think-time 300–1000 ms between requests

---

## 🧪 Step 3 — Run Gatling

```bash
mvn gatling:test
```

Gatling auto-detects `ProductLoadSimulation` in `src/test/java/loadtest/`. On completion it prints the result directory:

```
Reports generated, please open the following file:
file:///.../target/gatling/productloadsimulation-<timestamp>/index.html
```

### Test profile
- 4 parallel scenarios with different injection shapes
- ~2-minute run
- Built-in assertions: p95 < 2 s, error rate < 1%

---

## 📊 Step 4 — Analyze

See **`ANALYSIS.md`** for:

- How to read each report
- What the metrics mean (especially percentiles)
- Concrete bottleneck patterns this project's results will show
- Recommended optimizations for each pattern

---

## 🔁 Step 5 — Verify a Fix

After applying an optimization (e.g., switching the N+1 endpoint to the JOIN FETCH version), **re-run the same test** and compare:

- p95 / p99 response time
- Throughput
- Error rate

A successful optimization shows a measurable drop in latency at the same load level. If nothing changes, the bottleneck was elsewhere — keep digging.

---

## ⚠️ Notes on Honest Load Testing

- **Always close think time:** users don't hammer the API back-to-back. Gatling and the JMeter plan both include pauses.
- **Warm up the JVM:** the first few seconds will look slower than steady state (JIT compilation, connection-pool warmup). Discard them or look at steady-state numbers.
- **Don't load-test on a busy machine.** Run client + server on the same box only for demo purposes; for real benchmarks separate them.
- **Run the test plan multiple times.** Single runs lie — averages over 3–5 runs are far more trustworthy.
