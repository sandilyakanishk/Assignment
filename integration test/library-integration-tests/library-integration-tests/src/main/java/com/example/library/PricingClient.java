package com.example.library;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Calls an EXTERNAL pricing service to fetch the latest price for an ISBN.
 *
 * Why this matters for integration testing:
 *  - In production it hits a real HTTP endpoint we don't control.
 *  - In tests we point it at WireMock (a local in-process HTTP server) so we
 *    can verify HTTP-level integration without depending on the real service.
 */
@Component
public class PricingClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PricingClient(RestTemplate restTemplate,
                         @Value("${pricing.api.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Returns the latest price in cents for the given ISBN.
     *
     * @throws PricingNotFoundException if the API responds 404
     * @throws PricingServiceException  on any other failure
     */
    public int fetchPriceCents(String isbn) {
        String url = baseUrl + "/prices/" + isbn;
        try {
            PriceResponse response = restTemplate.getForObject(url, PriceResponse.class);
            if (response == null) {
                throw new PricingServiceException("Empty response from pricing API");
            }
            return response.priceCents;
        } catch (HttpClientErrorException.NotFound e) {
            throw new PricingNotFoundException("No price found for ISBN " + isbn);
        } catch (Exception e) {
            throw new PricingServiceException("Failed to fetch price: " + e.getMessage(), e);
        }
    }

    /** Body shape the pricing API returns. */
    public static class PriceResponse {
        public String isbn;
        public int priceCents;
    }

    public static class PricingNotFoundException extends RuntimeException {
        public PricingNotFoundException(String msg) { super(msg); }
    }

    public static class PricingServiceException extends RuntimeException {
        public PricingServiceException(String msg)              { super(msg); }
        public PricingServiceException(String msg, Throwable t) { super(msg, t); }
    }
}
