package com.example.library;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Book> create(@Valid @RequestBody Book book) {
        Book saved = service.create(book);
        return ResponseEntity
                .created(URI.create("/api/books/" + saved.getId()))
                .body(saved);
    }

    @GetMapping
    public List<Book> listAll() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    public Book getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/by-isbn/{isbn}")
    public Book getByIsbn(@PathVariable String isbn) {
        return service.getByIsbn(isbn);
    }

    @PostMapping("/{id}/refresh-price")
    public Book refreshPrice(@PathVariable Long id) {
        return service.refreshPrice(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ------------------------------------------------------------------
    // Exception → HTTP status mapping
    // ------------------------------------------------------------------

    @ExceptionHandler(BookService.BookNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(BookService.BookNotFoundException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(IllegalStateException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(PricingClient.PricingNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handlePricingMissing(PricingClient.PricingNotFoundException e) {
        return Map.of("error", e.getMessage());
    }
}
