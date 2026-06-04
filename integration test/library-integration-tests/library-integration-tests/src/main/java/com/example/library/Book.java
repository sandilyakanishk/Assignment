package com.example.library;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(name = "books",
       uniqueConstraints = @UniqueConstraint(columnNames = "isbn"))
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Column(nullable = false)
    private String author;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String isbn;

    /** Stored in cents to avoid floating-point money bugs. */
    @PositiveOrZero
    @Column(nullable = false)
    private int priceCents;

    protected Book() { /* required by JPA */ }

    public Book(String title, String author, String isbn, int priceCents) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.priceCents = priceCents;
    }

    public Long getId()         { return id; }
    public String getTitle()    { return title; }
    public String getAuthor()   { return author; }
    public String getIsbn()     { return isbn; }
    public int getPriceCents()  { return priceCents; }

    public void setTitle(String title)            { this.title = title; }
    public void setAuthor(String author)          { this.author = author; }
    public void setIsbn(String isbn)              { this.isbn = isbn; }
    public void setPriceCents(int priceCents)     { this.priceCents = priceCents; }
}
