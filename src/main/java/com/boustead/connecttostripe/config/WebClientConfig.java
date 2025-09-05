package com.boustead.connecttostripe.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * Shared WebClient for Mailchimp API calls with optimized connection management.
     * Uses ChatGPT's recommended configuration with background eviction to prevent stale connections.
     */
    @Bean
    public WebClient mailchimpApiClient() {
        ConnectionProvider provider = ConnectionProvider.builder("mailchimp-api-pool")
                .maxConnections(50)                                  // Higher limit for throughput
                .maxIdleTime(Duration.ofSeconds(60))                 // Recycle before NAT/remote idle kill
                .maxLifeTime(Duration.ofMinutes(5))                  // Don't keep sockets forever
                .evictInBackground(Duration.ofSeconds(30))           // Proactive stale connection cleanup
                .pendingAcquireTimeout(Duration.ofSeconds(10))       // Fail fast if pool exhausted
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .compress(true)                                      // Reduce bandwidth usage
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)  // 5 second connect timeout
                .responseTimeout(Duration.ofSeconds(30))             // 30 second response timeout
                .keepAlive(true);                                    // Use TCP keepalive properly

        return WebClient.builder()
                .baseUrl("https://api.mailchimp.com")                // Generic base, override with full URLs
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "ConnectToStripe/1.0")
                .build();
    }

    /**
     * Separate WebClient for Mailchimp OAuth operations with lighter configuration.
     */
    @Bean
    public WebClient mailchimpOAuthClient() {
        ConnectionProvider provider = ConnectionProvider.builder("mailchimp-oauth-pool")
                .maxConnections(10)                                  // OAuth calls are less frequent
                .maxIdleTime(Duration.ofSeconds(45))
                .maxLifeTime(Duration.ofMinutes(3))
                .evictInBackground(Duration.ofSeconds(20))           // More aggressive cleanup for OAuth
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .keepAlive(true);

        return WebClient.builder()
                .baseUrl("https://login.mailchimp.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "ConnectToStripe/1.0")
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
    }
}