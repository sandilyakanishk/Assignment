# Testing Assignment — Unit / Integration / E2E Tests

A self-contained Maven project demonstrating the **three layers of the testing pyramid** using industry-standard Java frameworks:

| Layer | Framework | Purpose |
|---|---|---|
| **Unit tests** | **JUnit 5** | Test single classes in isolation, no dependencies |
| **Integration tests** | **TestNG** | Test how multiple classes collaborate (service + repository) |
| **End-to-end tests** | **Selenium WebDriver** | Drive a real browser through a complete user journey |

---

## 📂 Project Structure

```
testing-assignment/
├── pom.xml                                       # Maven config + dependencies
├── README.md
└── src/
    ├── main/java/com/example/
    │   ├── Calculator.java                       # Sample app: pure math utility
    │   ├── StringUtils.java                      # Sample app: string utilities
    │   ├── User.java                             # Domain model
    │   ├── UserRepository.java                   # Repo interface + in-memory impl
    │   └── UserService.java                      # Business logic (uses repo)
    └── test/java/com/example/
        ├── unit/
        │   ├── CalculatorTest.java               # JUnit 5  — UNIT
        │   └── StringUtilsTest.java              # JUnit 5  — UNIT
        ├── integration/
        │   └── UserServiceIT.java                # TestNG   — INTEGRATION
        └── e2e/
            └── LoginE2ETest.java                 # Selenium — E2E
```

---

## 🔧 Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Google Chrome / Chromium** (for the Selenium E2E tests). The matching `chromedriver` is downloaded automatically by **WebDriverManager** — no manual setup needed.

---

## 🚀 Running the Tests

### Run only **unit tests** (fast — milliseconds)
```bash
mvn test
```
Surefire picks up classes ending in `*Test.java` that are NOT integration or E2E.

### Run **integration + E2E tests**
```bash
mvn verify
```
Failsafe picks up `*IT.java` and `*E2ETest.java`.

### Run **everything**
```bash
mvn clean verify
```

### Run a single test class
```bash
mvn test -Dtest=CalculatorTest
mvn verify -Dit.test=UserServiceIT
mvn verify -Dit.test=LoginE2ETest
```

---

## 📘 Layer 1 — Unit Tests (JUnit 5)

**File:** `CalculatorTest.java`, `StringUtilsTest.java`

A unit test exercises **one class in isolation** — no databases, no network, no filesystem. The goal is to verify the class's logic deterministically and quickly.

### Key JUnit 5 features used
- `@Test` — marks a test method
- `@BeforeEach` — fresh setup before every test (keeps tests independent)
- `@DisplayName` — human-readable test names in reports
- `@ParameterizedTest` with `@CsvSource` and `@ValueSource` — data-driven tests without duplication
- `assertEquals`, `assertTrue`, `assertThrows` — assertions

### Example
```java
@ParameterizedTest(name = "isEven({0}) → {1}")
@CsvSource({
    "0, true", "2, true", "4, true",
    "1, false", "7, false", "-3, false"
})
void isEven(int input, boolean expected) {
    assertEquals(expected, calculator.isEven(input));
}
```
One test method, six invocations.

---

## 🔗 Layer 2 — Integration Tests (TestNG)

**File:** `UserServiceIT.java`

Integration tests verify that **multiple components work correctly together**. Here, `UserService` is exercised with a **real** `InMemoryUserRepository` — nothing is mocked. In a production codebase this layer typically integrates against an in-memory or test database (H2, Testcontainers, etc.).

### Key TestNG features used
- `@Test(description = "...")` — labelled test
- `@BeforeMethod` — TestNG's equivalent of JUnit's `@BeforeEach`
- `@DataProvider` — data-driven tests with multiple invocations
- `assertThrows`, `assertEquals` from `org.testng.Assert`

### Why TestNG here?
The assignment explicitly asked for both frameworks. TestNG and JUnit overlap heavily; the difference shows up most in `@DataProvider`, group execution, parallel test config, and dependency-between-tests features.

### Example
```java
@Test(description = "Registering a user with invalid email throws")
public void registerUser_withInvalidEmail_throws() {
    User bad = new User(2, "Bob", "not-an-email");
    assertThrows(IllegalArgumentException.class, () -> service.register(bad));
    assertTrue(repository.findAll().isEmpty());
}
```
We verify both the exception **and** that the repository wasn't accidentally mutated — that's the integration aspect.

---

## 🌐 Layer 3 — End-to-End Tests (Selenium)

**File:** `LoginE2ETest.java`

E2E tests drive a **real browser** through the application as a user would. No internals are mocked — we click buttons, fill forms, and assert on what the user actually sees.

We target [`https://the-internet.herokuapp.com/login`](https://the-internet.herokuapp.com/login), a public site maintained explicitly for Selenium practice. The four scenarios covered:

| Test | Scenario | Expected |
|---|---|---|
| `successfulLogin` | Valid username + password | Redirect to `/secure`, success banner |
| `loginWithInvalidUsername` | Wrong username | Stays on `/login`, error banner |
| `loginWithInvalidPassword` | Wrong password | Stays on `/login`, error banner |
| `logoutAfterLogin` | Log in, then log out | Returns to `/login`, logout banner |

### Key Selenium concepts demonstrated
- **`WebDriverManager.chromedriver().setup()`** — auto-downloads matching ChromeDriver
- **Headless Chrome** via `ChromeOptions` (`--headless=new`) — runs without a visible window, works in CI
- **`WebDriverWait` + `ExpectedConditions`** — explicit waits, far more reliable than `Thread.sleep()`
- **Locators**: `By.id(...)`, `By.cssSelector(...)`
- **Lifecycle**: `@BeforeEach` creates a fresh browser per test, `@AfterEach` quits it (`driver.quit()` is critical — `close()` alone leaks processes)

### Watching the tests run
Comment out this line in `LoginE2ETest.java` to see the browser:
```java
options.addArguments("--headless=new");
```

---

## 🧪 The Testing Pyramid

```
              ▲      Few    ── E2E (slow, expensive, brittle)
             ╱│╲
            ╱ │ ╲
           ╱  │  ╲   Some   ── Integration
          ╱   │   ╲
         ╱    │    ╲
        ╱     │     ╲ Many  ── Unit (fast, cheap, reliable)
       ─────────────
```

- Write **lots of unit tests** — they're cheap, fast, and pinpoint bugs precisely.
- Write **some integration tests** — they catch issues unit tests can't (wiring, contracts).
- Write **a few E2E tests** — they catch real user-visible regressions but are slow and flaky.

This project demonstrates one or two examples at each level; in a real codebase the ratio would tilt heavily toward unit tests.

---

## 📊 Sample Output (abridged)

```
$ mvn clean verify
...
[INFO] -------------------------------------------------------
[INFO]  T E S T S   (Surefire — Unit)
[INFO] -------------------------------------------------------
[INFO] Running com.example.unit.CalculatorTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.unit.StringUtilsTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] -------------------------------------------------------
[INFO]  T E S T S   (Failsafe — Integration + E2E)
[INFO] -------------------------------------------------------
[INFO] Running com.example.integration.UserServiceIT
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.e2e.LoginE2ETest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

---

## ✅ Summary

| File | Layer | Framework | What it tests |
|---|---|---|---|
| `CalculatorTest.java` | Unit | JUnit 5 | `Calculator` arithmetic & edge cases |
| `StringUtilsTest.java` | Unit | JUnit 5 | `StringUtils` reverse / palindrome / vowels |
| `UserServiceIT.java` | Integration | TestNG | `UserService` + `UserRepository` together |
| `LoginE2ETest.java` | E2E | Selenium | Full login/logout flow in a real browser |
