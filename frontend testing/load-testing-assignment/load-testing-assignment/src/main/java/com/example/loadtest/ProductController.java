package com.example.loadtest;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints with INTENTIONALLY different performance characteristics so the
 * load tests can surface them as distinct bottleneck patterns.
 *
 *   FAST PATH         : GET  /api/products/{id}       - PK lookup, indexed
 *   MEDIUM            : GET  /api/products            - list-all, small payload
 *   SLOW (LIKE)       : GET  /api/products/search     - unindexed LIKE query
 *   N+1 PROBLEM       : GET  /api/products/with-reviews
 *   N+1 FIXED         : GET  /api/products/with-reviews-optimized
 *   ARTIFICIAL SLOW   : GET  /api/slow                - Thread.sleep(500)
 *   WRITE PATH        : POST /api/products            - insert under contention
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    // ----- FAST: PK lookup -----
    @GetMapping("/products/{id}")
    public Product getOne(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found: " + id));
    }

    // ----- MEDIUM: list all (small dataset, no joins) -----
    @GetMapping("/products")
    public List<Product> listAll() {
        return repository.findAll();
    }

    // ----- SLOW: unindexed LIKE query -----
    @GetMapping("/products/search")
    public List<Product> search(@RequestParam String keyword) {
        return repository.findByNameContainingIgnoreCase(keyword);
    }

    // ----- N+1 BOTTLENECK: list products and force-load reviews -----
    @GetMapping("/products/with-reviews")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> withReviews_naive() {
        List<Product> products = repository.findAll();
        // Accessing reviews here triggers ONE additional SELECT per product → N+1
        return products.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("reviewCount", p.getReviews().size());
            return m;
        }).toList();
    }

    // ----- N+1 FIXED: same data, single query with JOIN FETCH -----
    @GetMapping("/products/with-reviews-optimized")
    public List<Map<String, Object>> withReviews_optimized() {
        return repository.findAllWithReviews().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("reviewCount", p.getReviews().size());
            return m;
        }).toList();
    }

    // ----- ARTIFICIAL SLOW: blocks a request-handling thread for 500ms -----
    // Used to demonstrate thread-pool starvation under concurrency.
    @GetMapping("/slow")
    public Map<String, String> slow() throws InterruptedException {
        Thread.sleep(500);
        return Map.of("status", "ok", "delay", "500ms");
    }

    // ----- WRITE PATH: insert under load -----
    @PostMapping("/products")
    public ResponseEntity<Product> create(@RequestBody Product product) {
        Product saved = repository.save(product);
        return ResponseEntity.ok(saved);
    }
}
