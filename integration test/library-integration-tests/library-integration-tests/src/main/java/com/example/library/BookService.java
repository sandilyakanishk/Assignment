package com.example.library;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business-logic layer. Depends on TWO collaborators:
 *  - BookRepository  (persistence)
 *  - PricingClient   (external HTTP)
 *
 * Integration tests verify that the service interacts correctly with each.
 */
@Service
public class BookService {

    private final BookRepository repository;
    private final PricingClient pricingClient;

    public BookService(BookRepository repository, PricingClient pricingClient) {
        this.repository = repository;
        this.pricingClient = pricingClient;
    }

    @Transactional
    public Book create(Book book) {
        repository.findByIsbn(book.getIsbn()).ifPresent(b -> {
            throw new IllegalStateException("Book already exists with ISBN " + b.getIsbn());
        });
        return repository.save(book);
    }

    public Book getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException("Book not found: " + id));
    }

    public Book getByIsbn(String isbn) {
        return repository.findByIsbn(isbn)
                .orElseThrow(() -> new BookNotFoundException("Book not found: ISBN " + isbn));
    }

    public List<Book> listAll() {
        return repository.findAll();
    }

    /** Refresh a book's price by calling the external pricing service. */
    @Transactional
    public Book refreshPrice(Long id) {
        Book book = getById(id);
        int newPrice = pricingClient.fetchPriceCents(book.getIsbn());
        book.setPriceCents(newPrice);
        return repository.save(book);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BookNotFoundException("Book not found: " + id);
        }
        repository.deleteById(id);
    }

    public static class BookNotFoundException extends RuntimeException {
        public BookNotFoundException(String msg) { super(msg); }
    }
}
