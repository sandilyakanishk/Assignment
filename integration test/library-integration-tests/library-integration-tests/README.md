# Integration Testing — Book Library Service

A Spring Boot project demonstrating **five distinct integration testing patterns** against a sample REST API. Each test class targets a different layer of integration, showing how to set up the environment, what to mock vs. keep real, and which Spring Boot test slice to use.

---

## 🎯 What "Integration Test" Means Here

An **integration test** verifies that **two or more components work correctly together** — not in isolation (that's a unit test) and not all of them through a browser (that's E2E). The components could be:

- A service and its repository
- A controller and the HTTP / JSON serialization layer
- A client and an external HTTP API
- The full application stack from HTTP all the way down to the database

This project shows one example of each.

---

## 📂 Project Structure

```
library-integration-tests/
├── pom.xml
├── README.md
├── src/
│   ├── main/java/com/example/library/
│   │   ├── LibraryApplication.java     # Spring Boot entry point
│   │   ├── Book.java                   # JPA entity
│   │   ├── BookRepository.java         # Spring Data JPA repo
│   │   ├── PricingClient.java          # External HTTP collaborator
│   │   ├── BookService.java            # Business logic
│   │   └── BookController.java         # REST endpoints
│   ├── main/resources/
│   │   └── application.properties
│   └── test/java/com/example/library/
│       ├── BookRepositoryIT.java       # #1 — Persistence integration
│       ├── BookServiceIT.java          # #2 — Service ↔ Repo integration
│       ├── BookControllerIT.java       # #3 — Web layer integration
│       ├── LibraryE2EIT.java           # #4 — Full-stack HTTP integration
│       └── PricingClientWireMockIT.java# #5 — External API integration
```

The application has **five components**: an entity, a repository, an external pricing client, a service, and a controller. Each integration test focuses on the interaction between a specific subset of them.

---

## 🔧 Environment Setup

### Prerequisites
- **Java 17+**
- **Maven 3.8+**
- *(No Docker, no external database, no live external API)*

### Tools wired into the project
| Concern | Tool | Notes |
|---|---|---|
| Test framework | **JUnit 5** | Bundled with `spring-boot-starter-test` |
| Mocking | **Mockito** | Bundled; used via `@MockBean` |
| Assertions | **AssertJ** | Bundled; fluent `assertThat(...)` style |
| In-memory database | **H2** | Activated automatically in tests |
| Web-layer testing | **MockMvc** + **TestRestTemplate** | Two flavors covered |
| External HTTP stubbing | **WireMock** | Local in-process HTTP server |
| Plugin separation | **Surefire** (unit) + **Failsafe** (integration) | `*Test.java` vs `*IT.java` |

### Running

```bash
# Unit tests only (fast; none in this project, but kept available)
mvn test

# Integration tests (everything ending in *IT.java)
mvn verify

# Single integration test class
mvn verify -Dit.test=BookRepositoryIT
```

---

## 🧪 The Five Integration Test Patterns

### #1 — `BookRepositoryIT` — Persistence Integration

**Annotation:** `@DataJpaTest`

**What gets loaded:** Only the JPA slice — entities, repositories, Hibernate, an embedded H2 datasource. No web layer, no service beans.

**What is validated:**
- JPA entity-to-table mapping (column types, nullability, unique constraints)
- Spring Data derived queries (`findByIsbn`, `findByAuthorIgnoreCase`)
- Custom `@Query` JPQL (`findByPriceRange`)
- Unique-constraint enforcement at the DB level

**Key insight:** every test runs in a transaction that is rolled back at the end, so tests are isolated without needing manual cleanup.

```java
@DataJpaTest
class BookRepositoryIT {
    @Autowired BookRepository repository;
    @Autowired TestEntityManager em;
    // ... persist sample data, query via repository, assert results
}
```

---

### #2 — `BookServiceIT` — Service ↔ Repository (External Dep Mocked)

**Annotation:** `@SpringBootTest` + `@Transactional`

**What gets loaded:** Full Spring context. The real `BookRepository` and real H2 database. The `PricingClient` is replaced with a Mockito mock via `@MockBean`.

**What is validated:**
- Business rules (duplicate-ISBN rejection, "not found" exceptions) interact correctly with the database
- The service really commits / rolls back via JPA
- The interaction contract with `PricingClient` (`verify(pricingClient, times(1)).fetchPriceCents(...)`)

**Why mock the pricing client here?** We want to isolate THIS integration (service + repo). The pricing client's integration with its API has its own test (#5).

```java
@SpringBootTest
@Transactional
class BookServiceIT {
    @Autowired   BookService    service;
    @Autowired   BookRepository repository;
    @MockBean    PricingClient  pricingClient;  // ← isolated from this test
}
```

---

### #3 — `BookControllerIT` — Web Layer Integration

**Annotation:** `@WebMvcTest(BookController.class)`

**What gets loaded:** Only the web slice — Spring MVC, JSON converters, the specified controller. No JPA, no real service.

**What is validated:**
- URL routing
- Jackson JSON serialization / deserialization
- HTTP status codes (201, 400, 404, 409, 204)
- Bean Validation on the request body (e.g. blank title → 400)
- `@ExceptionHandler` mapping (BookNotFoundException → 404, IllegalStateException → 409)

**Tool:** **MockMvc** — simulates HTTP requests without starting a real server, so it's fast.

```java
mockMvc.perform(post("/api/books")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(input)))
    .andExpect(status().isCreated())
    .andExpect(header().string("Location", "/api/books/1"))
    .andExpect(jsonPath("$.title").value("Test Driven Development"));
```

---

### #4 — `LibraryE2EIT` — Full-Stack HTTP Integration

**Annotation:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`

**What gets loaded:** EVERYTHING. A real embedded servlet container on a random port, the entire application stack from HTTP all the way down to H2.

**What is validated:**
- Real HTTP requests flow: HTTP server → Spring MVC → controller → service → repository → DB → and back out
- Every layer is real (only `PricingClient` is mocked to avoid live external dependency)
- State persists across test methods in this class — `@TestMethodOrder` keeps the sequence predictable

**Tool:** **TestRestTemplate** — sends real HTTP requests against the running app.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LibraryE2EIT {
    @Autowired TestRestTemplate http;

    ResponseEntity<Book> response = http.postForEntity("/api/books", input, Book.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
}
```

This is the slowest test in the project (~5 s startup) but gives the highest confidence that every layer is wired up correctly.

---

### #5 — `PricingClientWireMockIT` — External HTTP Integration via WireMock

**Annotation:** `@SpringBootTest` + `@DynamicPropertySource`

**What gets loaded:** Full Spring context, but with `pricing.api.url` rewired to point at a local WireMock server running on port 8089.

**What is validated:**
- The client builds the correct URL
- JSON response is correctly parsed
- 404 → `PricingNotFoundException`
- 500 → `PricingServiceException`
- Slow responses are still handled

**Tool:** **WireMock** — an in-process HTTP server you control with `stubFor(...)`. Lets you simulate any HTTP scenario (200, 404, 500, delays, malformed JSON, etc.) without ever touching a real external system.

```java
wireMock.stubFor(get(urlEqualTo("/prices/9780132350884"))
    .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"isbn\":\"9780132350884\",\"priceCents\":4250}")));

int price = pricingClient.fetchPriceCents("9780132350884");
assertThat(price).isEqualTo(4250);

wireMock.verify(getRequestedFor(urlEqualTo("/prices/9780132350884")));
```

This is the canonical pattern for "integration test against an external HTTP service without depending on the real one."

---

## 📊 Test Pattern Comparison

| # | Test class | Slice | DB | External API | Speed | Confidence |
|---|---|---|---|---|---|---|
| 1 | `BookRepositoryIT` | `@DataJpaTest` | Real H2 | — | Fast | Repo correctness |
| 2 | `BookServiceIT` | Full context | Real H2 | Mocked | Medium | Service + DB |
| 3 | `BookControllerIT` | `@WebMvcTest` | — | — | Fast | HTTP plumbing |
| 4 | `LibraryE2EIT` | Full context + server | Real H2 | Mocked | Slow | Whole stack |
| 5 | `PricingClientWireMockIT` | Full context | Real H2 | WireMock | Medium | External HTTP contract |

A real codebase typically has many of #1–3, a handful of #4, and one or two #5 per external integration. Together they trace every wire in the system.

---

## 🧠 Decisions an Engineer Has to Make

When you add an integration test, you choose two things explicitly:

1. **Which components stay real?**  
   Anything you replace with a mock is no longer being integration-tested by this test. That's fine, as long as another test covers it.

2. **What environment do those components need?**  
   - Need JPA? → use `@DataJpaTest` or `@SpringBootTest` with H2.  
   - Need MVC routing? → use `@WebMvcTest` or full `@SpringBootTest`.  
   - Need an external HTTP service? → stub it with WireMock.  
   - Need a real database for SQL features H2 doesn't support? → use **Testcontainers** with PostgreSQL (not covered here to keep the project Docker-free, but the migration is small).

Pick the minimum environment that supports the interaction you're validating. That keeps tests fast.

---

## ✅ Summary

The project demonstrates the full spectrum of integration test environments — from a narrow JPA slice with an in-memory DB, all the way to a real embedded server with WireMock stubs of external APIs. Each pattern is shown with realistic assertions and the corresponding setup boilerplate, so it can be lifted straight into a production codebase.
