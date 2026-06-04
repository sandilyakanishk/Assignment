package com.example.library;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * INTEGRATION TEST #2 — Service ↔ Repository (with the external HTTP call mocked)
 * ============================================================================
 *
 * What this validates:
 *   • BookService is correctly wired with a real BookRepository
 *   • Business rules (duplicate-ISBN rejection, "not found" exceptions) interact
 *     correctly with the persistence layer
 *   • Transactional behavior: changes are committed/rolled back properly
 *   • Integration with the external PricingClient — but the client itself is mocked
 *     because we don't want this test depending on a live external service.
 *
 * Environment setup:
 *   • @SpringBootTest loads the FULL Spring context
 *   • H2 in-memory database (from application.properties)
 *   • @MockBean replaces PricingClient with a Mockito mock — so we test the
 *     integration between BookService and BookRepository, but NOT the
 *     integration with the real pricing API (that's tested in #5).
 *   • @Transactional rolls back DB state between tests.
 */
@SpringBootTest
@Transactional   // each test rolled back at the end → isolation
class BookServiceIT {

    @Autowired
    private BookService service;

    @Autowired
    private BookRepository repository;

    @MockBean
    private PricingClient pricingClient;   // ← collaborator we DON'T integrate with here

    @Test
    @DisplayName("create() persists the book through the real repository")
    void create_persistsThroughRepository() {
        Book saved = service.create(new Book("Refactoring", "Fowler", "9780134757599", 5000));

        assertThat(saved.getId()).isNotNull();
        // Verify it really hit the database via an independent repository query.
        assertThat(repository.findByIsbn("9780134757599")).isPresent();
    }

    @Test
    @DisplayName("create() with a duplicate ISBN is rejected by the service after DB lookup")
    void create_duplicateIsbn_throws() {
        service.create(new Book("Refactoring", "Fowler", "9780134757599", 5000));

        assertThatThrownBy(() -> service.create(
                new Book("Refactoring 2", "Fowler", "9780134757599", 6000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("getById throws BookNotFoundException for an unknown id")
    void getById_unknown_throws() {
        assertThatThrownBy(() -> service.getById(99999L))
                .isInstanceOf(BookService.BookNotFoundException.class);
    }

    @Test
    @DisplayName("refreshPrice() pulls a new price from PricingClient and persists it")
    void refreshPrice_updatesPriceFromExternalService() {
        // Arrange — a book in the DB and a stubbed external response
        Book saved = service.create(new Book("DDD", "Evans", "9780321125217", 3000));
        when(pricingClient.fetchPriceCents("9780321125217")).thenReturn(7500);

        // Act
        Book updated = service.refreshPrice(saved.getId());

        // Assert — the new price was persisted
        assertThat(updated.getPriceCents()).isEqualTo(7500);
        assertThat(repository.findById(saved.getId()).get().getPriceCents()).isEqualTo(7500);

        // Verify the integration contract with PricingClient was used exactly once
        verify(pricingClient, times(1)).fetchPriceCents("9780321125217");
    }

    @Test
    @DisplayName("delete() removes the book through the repository")
    void delete_removesBook() {
        Book saved = service.create(new Book("X", "Y", "1111111111111", 100));
        service.delete(saved.getId());

        assertThat(repository.findById(saved.getId())).isEmpty();
    }
}
