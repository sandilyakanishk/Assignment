package com.example.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================================
 * INTEGRATION TEST #3 — Web layer (Controller ↔ HTTP / JSON serialization)
 * ============================================================================
 *
 * What this validates:
 *   • URL routing (does POST /api/books hit the right method?)
 *   • Request body → Java object deserialization (Jackson)
 *   • Java object → response body serialization
 *   • HTTP status codes (201 Created, 404 Not Found, 409 Conflict, etc.)
 *   • Exception → status mapping via @ExceptionHandler
 *   • Bean validation (@Valid on the request body)
 *
 * Environment setup:
 *   • @WebMvcTest loads ONLY the web slice — controllers, JSON converters,
 *     Spring MVC infrastructure. No JPA, no service beans.
 *   • BookService is provided as a @MockBean — we're testing the integration
 *     between the HTTP layer and the controller, not the controller and its
 *     real downstream service. The service layer is tested separately in #2.
 *
 * Speed: very fast (~1s), no DB, no full context.
 */
@WebMvcTest(BookController.class)
class BookControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private BookService bookService;

    @Test
    @DisplayName("POST /api/books returns 201 with the created book and Location header")
    void create_returns201() throws Exception {
        Book input = new Book("Test Driven Development", "Kent Beck", "9780321146533", 3500);
        Book saved = new Book("Test Driven Development", "Kent Beck", "9780321146533", 3500);
        // Simulate the ID assigned by JPA
        java.lang.reflect.Field idField = Book.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(saved, 1L);

        when(bookService.create(any(Book.class))).thenReturn(saved);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Driven Development"))
                .andExpect(jsonPath("$.isbn").value("9780321146533"));
    }

    @Test
    @DisplayName("POST /api/books with blank title returns 400 (validation)")
    void create_withInvalidBody_returns400() throws Exception {
        Book invalid = new Book("", "Kent Beck", "9780321146533", 3500);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookService);   // ← service must NOT be called when validation fails
    }

    @Test
    @DisplayName("GET /api/books/{id} returns the book")
    void getById_returnsBook() throws Exception {
        Book b = new Book("Clean Code", "Robert C Martin", "9780132350884", 3800);
        when(bookService.getById(7L)).thenReturn(b);

        mockMvc.perform(get("/api/books/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.author").value("Robert C Martin"));
    }

    @Test
    @DisplayName("GET /api/books/{id} on missing book returns 404")
    void getById_missing_returns404() throws Exception {
        when(bookService.getById(99L))
                .thenThrow(new BookService.BookNotFoundException("Book not found: 99"));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("99")));
    }

    @Test
    @DisplayName("POST /api/books with duplicate ISBN returns 409 Conflict")
    void create_duplicate_returns409() throws Exception {
        when(bookService.create(any(Book.class)))
                .thenThrow(new IllegalStateException("Book already exists with ISBN x"));

        Book input = new Book("Anything", "Anyone", "9999999999999", 100);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("already exists")));
    }

    @Test
    @DisplayName("GET /api/books returns the full list as JSON array")
    void list_returnsArray() throws Exception {
        when(bookService.listAll()).thenReturn(List.of(
                new Book("A", "X", "1111111111111", 100),
                new Book("B", "Y", "2222222222222", 200)
        ));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("A"))
                .andExpect(jsonPath("$[1].title").value("B"));
    }

    @Test
    @DisplayName("DELETE /api/books/{id} returns 204 No Content")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/books/5"))
                .andExpect(status().isNoContent());

        verify(bookService).delete(5L);
    }
}
