package com.example.loadtest;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonBackReference
    private Product product;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    protected Review() {}

    public Review(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId()        { return id; }
    public int getRating()     { return rating; }
    public String getComment() { return comment; }
    public Product getProduct(){ return product; }

    public void setProduct(Product product) { this.product = product; }
}
