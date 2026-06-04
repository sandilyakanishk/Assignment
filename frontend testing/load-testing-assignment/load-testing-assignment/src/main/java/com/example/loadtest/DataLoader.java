package com.example.loadtest;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * Populates the in-memory DB on startup with enough data to make the
 * bottlenecks observable under load.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private final ProductRepository repository;

    public DataLoader(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;

        List<String> categories = List.of("Electronics", "Books", "Clothing", "Home", "Sports");
        Random rnd = new Random(42);

        for (int i = 1; i <= 100; i++) {
            Product p = new Product(
                    "Product-" + i + "-" + categories.get(rnd.nextInt(categories.size())),
                    categories.get(rnd.nextInt(categories.size())),
                    100 + rnd.nextInt(10_000)
            );
            // 5 reviews each → 500 reviews total
            for (int r = 0; r < 5; r++) {
                p.addReview(new Review(
                        1 + rnd.nextInt(5),
                        "Review " + r + " for product " + i
                ));
            }
            repository.save(p);
        }
        System.out.println("Seeded " + repository.count() + " products with reviews.");
    }
}
