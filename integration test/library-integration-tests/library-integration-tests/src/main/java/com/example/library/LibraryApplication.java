package com.example.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class LibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }

    /** RestTemplate bean — used by PricingClient. We expose it so tests can swap it. */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
