package com.boustead.connecttostripe.mailchimp;

import com.boustead.connecttostripe.exception.MailchimpOAuthException;
import com.boustead.connecttostripe.exception.UserNotFoundException;
import com.boustead.connecttostripe.stripe.StripeUserRepository;
import com.boustead.connecttostripe.stripe.controller.CreateUserResponse;
import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth/mailchimp")
public class MailchimpOAuthController {

    private static final Logger logger = LoggerFactory.getLogger(MailchimpOAuthController.class);

    @Value("${mailchimp.client-id}")
    private String clientId;

    @Value("${mailchimp.client-secret}")
    private String clientSecret;

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

    @Value("${mailchimp.redirect-uri}")
    private String redirectUri;

    @Value("${mailchimp.stripe.redirect-uri}")
    private String stripeUri;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StripeUserRepository stripeUserRepository;

    @Autowired
    private MailchimpUserRepository mailchimpUserRepository;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://login.mailchimp.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .build();

    @GetMapping("/callback")
    public Mono<ResponseEntity<Void>> handleCallback(
            @RequestParam String code, 
            @RequestParam String state,
            @RequestParam(required = false) String error) {
        
        // Handle OAuth errors from Mailchimp
        if (error != null) {
            logger.error("Mailchimp OAuth error received: {}", error);
            return redirectToStripeWithError("OAuth authorization failed: " + error);
        }

        if (code == null || code.isEmpty()) {
            logger.error("Missing authorization code from Mailchimp callback");
            throw new MailchimpOAuthException("Missing authorization code from Mailchimp");
        }

        if (state == null || state.isEmpty()) {
            logger.error("Missing state parameter in OAuth callback");
            throw new MailchimpOAuthException("Missing state parameter - possible CSRF attack");
        }

        String stripeAccountId = state;

        return Mono.fromCallable(() -> stripeUserRepository.existsByStripeAccountId(stripeAccountId))
                .flatMap(exists -> {
                    if (!exists) {
                        logger.error("Stripe account ID not found in OAuth callback: {}", stripeAccountId);
                        throw new UserNotFoundException("Stripe account not found: " + stripeAccountId);
                    }

                    // Exchange authorization code for access token
                    return exchangeCodeForToken(code)
                            .flatMap(tokenResponse -> {
                                String accessToken = tokenResponse.getAccess_token();
                                
                                // Get server metadata
                                return getMailchimpMetadata(accessToken)
                                        .flatMap(serverPrefix -> {
                                            // Save or update Mailchimp user
                                            return saveMailchimpUser(stripeAccountId, accessToken, serverPrefix)
                                                    .then(redirectToStripeWithSuccess());
                                        });
                            });
                })
                .onErrorResume(error1 -> {
                    logger.error("OAuth callback processing error: {}", error1.getMessage(), error1);
                    return redirectToStripeWithError("Connection failed: " + error1.getMessage());
                });
    }

    private Mono<MailchimpTokenResponse> exchangeCodeForToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);

        return webClient.post()
                .uri("/oauth2/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(formData)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class).flatMap(errorBody -> {
                            logger.error("Mailchimp token exchange failed. Status: {}, Response: {}", response.statusCode(), errorBody);
                            return Mono.error(new MailchimpOAuthException("Token exchange failed: " + errorBody));
                        })
                )
                .bodyToMono(MailchimpTokenResponse.class);
    }

    private Mono<String> getMailchimpMetadata(String accessToken) {
        return webClient.get()
                .uri("/oauth2/metadata")
                .header("Authorization", "OAuth " + accessToken)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class).flatMap(errorBody -> {
                            logger.error("Mailchimp metadata fetch failed. Status: {}, Response: {}", response.statusCode(), errorBody);
                            return Mono.error(new MailchimpOAuthException("Metadata fetch failed: " + errorBody));
                        })
                )
                .bodyToMono(MailchimpMetadataResponse.class)
                .map(MailchimpMetadataResponse::getDc);
    }

    private Mono<MailchimpUser> saveMailchimpUser(String stripeAccountId, String accessToken, String serverPrefix) {
        return Mono.fromCallable(() -> {
            // Check if user already exists and update, or create new
            MailchimpUser mailchimpUser = mailchimpUserRepository
                    .findByStripeAccountId(stripeAccountId)
                    .orElse(new MailchimpUser());
            
            mailchimpUser.setStripeAccountId(stripeAccountId);
            mailchimpUser.setToken(accessToken);
            mailchimpUser.setServerPrefix(serverPrefix);
            
            return mailchimpUserRepository.save(mailchimpUser);
        });
    }

    private Mono<ResponseEntity<Void>> redirectToStripeWithSuccess() {
        HttpHeaders headers = new HttpHeaders();
        // Redirect back to Stripe Dashboard - adjust URL as needed
        headers.setLocation(URI.create(stripeUri));
        return Mono.just(new ResponseEntity<>(headers, HttpStatus.FOUND));
    }

    private Mono<ResponseEntity<Void>> redirectToStripeWithError(String errorMessage) {
        HttpHeaders headers = new HttpHeaders();
        // Redirect back to Stripe Dashboard with error - adjust URL as needed
        String errorUrl = UriComponentsBuilder
                .fromHttpUrl(stripeUri)
                .queryParam("error", errorMessage)
                .toUriString();
        headers.setLocation(URI.create(errorUrl));
        return Mono.just(new ResponseEntity<>(headers, HttpStatus.FOUND));
    }
}
