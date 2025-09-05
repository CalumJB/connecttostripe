package com.boustead.connecttostripe.mailchimp;

import org.springframework.web.server.ResponseStatusException;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Utility class providing standardized retry strategies for Mailchimp API operations.
 * Reduces code duplication and ensures consistent retry behavior across the application.
 */
public class RetryHelper {

    /**
     * Aggressive retry strategy for read operations (GET requests).
     * Safe to retry multiple times as these are idempotent operations.
     * 
     * @return Retry strategy with 3 attempts and exponential backoff
     */
    public static Retry readOperationRetry() {
        return Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(5))
                .filter(RetryHelper::shouldRetry);
    }

    /**
     * Conservative retry strategy for webhook operations (PUT/POST requests).
     * More cautious approach with fewer retries for potentially non-idempotent operations.
     * 
     * @return Retry strategy with 2 attempts and longer backoff
     */
    public static Retry webhookOperationRetry() {
        return Retry.backoff(2, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10))
                .filter(RetryHelper::shouldRetry);
    }

    /**
     * Determines whether an error should trigger a retry.
     * Only retries on transient failures that might succeed on subsequent attempts.
     * 
     * @param throwable The exception that occurred
     * @return true if the operation should be retried, false otherwise
     */
    private static boolean shouldRetry(Throwable throwable) {
        // Handle HTTP status code errors
        if (throwable instanceof ResponseStatusException rse) {
            int status = rse.getStatusCode().value();
            
            // Retry on server errors, timeouts, and rate limiting
            return status >= 500 ||     // 5xx server errors (temporary Mailchimp issues)
                   status == 408 ||     // Request timeout (network slowness)
                   status == 429;       // Rate limiting (retry with backoff)
        }
        
        // Retry on network-level exceptions
        return throwable instanceof java.net.ConnectException ||                              // Connection failed
               throwable instanceof java.util.concurrent.TimeoutException ||                 // Request timed out
               throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException; // Other network issues
    }

}