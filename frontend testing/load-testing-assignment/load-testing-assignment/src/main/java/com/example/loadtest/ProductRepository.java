package com.example.loadtest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * LIKE search without a database index → demonstrates a slow query bottleneck
     * as the table grows. Used by /api/products/search.
     */
    List<Product> findByNameContainingIgnoreCase(String keyword);

    /**
     * Optimized fetch using JOIN FETCH — avoids the N+1 problem.
     * Used by /api/products/with-reviews-optimized.
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.reviews")
    List<Product> findAllWithReviews();
}
