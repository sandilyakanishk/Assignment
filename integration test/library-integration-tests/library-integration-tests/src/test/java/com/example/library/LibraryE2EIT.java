package com.example.library;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * ============================================================================
 * INTEGRATION TEST #4 — Full-stack HTTP test (FULL Spring context, real server)
 * ============================================================================
 *
 * What this validates:
 *   • A real embedded servlet container is started on a random port
 *   • Real HTTP requests travel through: HTTP server → Spring MVC →
 *     controller → service → repository → H2 database, then back out
 *   • Every layer is the REAL component (except PricingClient, which we
 *     still mock to avoid hitting a real external API)
 *
 * Environment setup:
 *   • @SpringBootTest(webEnvironment = RANDOM_PORT) launches the full app
 *   • TestRestTemplate sends real HTTP requests
 *   • H2 in-memory DB persists between test methods within this class
 *     (we order tests to keep their effects coherent)
 *
 * This is the closest thing to an "end-to-end" integration test without
 * driving a browser. Slow (~3-5s startup) but high confidence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LibraryE2EIT {

    @Autowired
    private TestRestTemplate http;

    @MockBean
    private PricingClient pricingClient;   // mock only the external dependency

    private static Long createdId;

    @Test
    @Order(1)
    @DisplayName("POST /api/books creates a book and persists it")
    void postBook_createsAndPersists() {
        Book input = new Book("The Pragmatic Programmer", "Hunt & Thomas",
                              "9780135957059", 4000);

        ResponseEntity<Book> response = http.postForEntity("/api/books", input, Book.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        Book body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getTitle()).isEqualTo("The Pragmatic Programmer");

        createdId = body.getId();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/books/{id} retrieves the persisted book")
    void getBook_retrievesPersisted() {
        ResponseEntity<Book> response =
                http.getForEntity("/api/books/" + createdId, Book.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getIsbn()).isEqualTo("9780135957059");
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/books/{id}/refresh-price updates the price using the mocked pricing client")
    void refreshPrice_updatesPrice() {
        when(pricingClient.fetchPriceCents("9780135957059")).thenReturn(9999);

        ResponseEntity<Book> response = http.postForEntity(
                "/api/books/" + createdId + "/refresh-price", null, Book.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPriceCents()).isEqualTo(9999);

        // Re-fetch to confirm it was persisted
        Book refreshed = http.getForObject("/api/books/" + createdId, Book.class);
        assertThat(refreshed.getPriceCents()).isEqualTo(9999);
    }

    @Test
    @Order(4)
    @DisplayName("Duplicate ISBN POST returns 409 Conflict end-to-end")
    void duplicateIsbn_returns409() {
        Book duplicate = new Book("Duplicate", "Nobody", "9780135957059", 100);

        ResponseEntity<String> response =
                http.postForEntity("/api/books", duplicate, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already exists");
    }

    @Test
    @Order(5)
    @DisplayName("DELETE /api/books/{id} removes the book; subsequent GET returns 404")
    void deleteThenGet_returns404() {
        http.delete("/api/books/" + createdId);

        ResponseEntity<String> after =
                http.getForEntity("/api/books/" + createdId, String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
