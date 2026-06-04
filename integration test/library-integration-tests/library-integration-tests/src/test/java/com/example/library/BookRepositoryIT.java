package com.example.library;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * ============================================================================
 * INTEGRATION TEST #1 — Repository layer (Persistence integration)
 * ============================================================================
 *
 * What this validates:
 *   • JPA entity mappings (column types, constraints, unique indexes)
 *   • Spring Data JPA derived queries (findByIsbn, findByAuthorIgnoreCase)
 *   • Custom @Query JPQL
 *   • The interaction between BookRepository ↔ Hibernate ↔ H2 database
 *
 * Environment setup:
 *   • @DataJpaTest spins up ONLY the JPA slice (no web layer, no service beans).
 *   • An embedded H2 database is configured automatically.
 *   • Each test runs in a transaction that is ROLLED BACK at the end →
 *     tests do not pollute each other.
 *
 * Speed: ~1 second per class (fast, because no full app context).
 */
@DataJpaTest
@AutoConfigureTestDatabase  // explicit — use embedded H2, ignore any DB on the classpath
class BookRepositoryIT {

    @Autowired
    private BookRepository repository;

    @Autowired
    private TestEntityManager em;   // helper for setting up DB state in a test-friendly way

    @BeforeEach
    void seed() {
        em.persist(new Book("Effective Java",        "Joshua Bloch",   "9780134685991", 4500));
        em.persist(new Book("Clean Code",            "Robert C Martin","9780132350884", 3800));
        em.persist(new Book("Clean Architecture",    "Robert C Martin","9780134494166", 4200));
        em.flush();
    }

    @Test
    @DisplayName("findByIsbn returns the matching book")
    void findByIsbn_returnsMatchingBook() {
        Optional<Book> found = repository.findByIsbn("9780134685991");

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Effective Java");
        assertThat(found.get().getPriceCents()).isEqualTo(4500);
    }

    @Test
    @DisplayName("findByIsbn returns empty when not found")
    void findByIsbn_returnsEmpty_whenUnknown() {
        assertThat(repository.findByIsbn("0000000000000")).isEmpty();
    }

    @Test
    @DisplayName("findByAuthorIgnoreCase matches case-insensitively")
    void findByAuthorIgnoreCase() {
        List<Book> books = repository.findByAuthorIgnoreCase("robert c martin");

        assertThat(books).hasSize(2)
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
    }

    @Test
    @DisplayName("Custom @Query findByPriceRange returns books in the range, inclusive")
    void findByPriceRange() {
        List<Book> mid = repository.findByPriceRange(4000, 4500);

        assertThat(mid).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Clean Architecture");
    }

    @Test
    @DisplayName("Unique constraint on ISBN is enforced at the database level")
    void duplicateIsbn_violatesUniqueConstraint() {
        Book duplicate = new Book("Dupe", "Someone", "9780132350884", 1000);

        assertThatThrownBy(() -> {
            repository.saveAndFlush(duplicate);   // flush forces the INSERT now
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
