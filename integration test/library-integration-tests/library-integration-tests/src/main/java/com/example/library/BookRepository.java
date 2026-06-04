package com.example.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    /** Derived query — Spring Data builds the SQL from the method name. */
    Optional<Book> findByIsbn(String isbn);

    /** Derived query — case-insensitive author search. */
    List<Book> findByAuthorIgnoreCase(String author);

    /** Custom JPQL query — verified by the repository integration test. */
    @Query("SELECT b FROM Book b WHERE b.priceCents BETWEEN :min AND :max")
    List<Book> findByPriceRange(@Param("min") int min, @Param("max") int max);
}
