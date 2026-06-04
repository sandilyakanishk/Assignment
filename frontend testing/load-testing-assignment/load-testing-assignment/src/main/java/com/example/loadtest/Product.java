package com.example.loadtest;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private int priceCents;

    /**
     * LAZY association. Iterating products and accessing reviews on each one
     * triggers the classic N+1 query problem — a key bottleneck the load test
     * is designed to surface.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    private List<Review> reviews = new ArrayList<>();

    protected Product() {}

    public Product(String name, String category, int priceCents) {
        this.name = name;
        this.category = category;
        this.priceCents = priceCents;
    }

    public void addReview(Review r) {
        reviews.add(r);
        r.setProduct(this);
    }

    public Long getId()              { return id; }
    public String getName()          { return name; }
    public String getCategory()      { return category; }
    public int getPriceCents()       { return priceCents; }
    public List<Review> getReviews() { return reviews; }
}
