package com.boustead.connecttostripe.mailchimp;

import com.boustead.connecttostripe.stripe.StripeUserRepository;
import com.boustead.connecttostripe.stripe.controller.CreateUserResponse;
import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
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

    @Value("${mailchimp.client-id}")
    private String clientId;

    @Value("${mailchimp.client-secret}")
    private String clientSecret;

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

    @Value("${mailchimp.redirect-uri}")
    private String redirectUri;

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

//    @GetMapping("/start")
//    public Mono<ResponseEntity<Void>> startOauthFlow(@RequestParam("state") String state) {
//
//        String mailchimpAuthUrl = UriComponentsBuilder
//                .fromHttpUrl("https://login.mailchimp.com/oauth2/authorize")
//                .queryParam("response_type", "code")
//                .queryParam("client_id", clientId)
//                .queryParam("redirect_uri", redirectUri)
//                .queryParam("state", state)
//                .build()
//                .toUriString();
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setLocation(URI.create(mailchimpAuthUrl));
//        return Mono.just(new ResponseEntity<>(headers, HttpStatus.FOUND));
//    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startOauth(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody MailchimpOAuthStartRequest request) throws JsonProcessingException {

        try {
            String payload = "{\"user_id\":\"" + request.stripeUserId() + "\",\"account_id\":\"" + request.stripeAccountId() + "\"}";
            Webhook.Signature.verifyHeader(payload, signature, stripeSecret, 1000L);
        } catch (SignatureVerificationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Stripe signature", e);
        }

        // Build the full Mailchimp authorization URL
        String authUrl = UriComponentsBuilder
                .fromUriString("https://login.mailchimp.com/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", request.state())
                .toUriString();

        // Create the response body
        Map<String, String> responseBody = Map.of("redirectUrl", authUrl);

        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/callback")
    public Mono<String> handleCallback(@RequestParam String code, @RequestParam String state) {
        if (state == null || state.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Missing or empty state parameter"));
        }

        String stripeAccountId = state;

        return Mono.fromCallable(() -> stripeUserRepository.existsByStripeAccountId(stripeAccountId))
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalArgumentException("Stripe account ID not found: " + stripeAccountId));
                    }

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
                                        System.err.println("Mailchimp error: " + errorBody);
                                        return Mono.error(new RuntimeException("Mailchimp token exchange failed: " + errorBody));
                                    })
                            )
                            .bodyToMono(MailchimpTokenResponse.class)
                            .flatMap(tokenResponse -> {
                                String accessToken = tokenResponse.getAccess_token();

                                return webClient.get()
                                        .uri("/oauth2/metadata")
                                        .header("Authorization", "OAuth " + accessToken)
                                        .retrieve()
                                        .onStatus(status -> status.isError(), response ->
                                                response.bodyToMono(String.class).flatMap(errorBody -> {
                                                    System.err.println("Mailchimp error: " + errorBody);
                                                    return Mono.error(new RuntimeException("Mailchimp token exchange failed: " + errorBody));
                                                })
                                        )
                                        .bodyToMono(MailchimpMetadataResponse.class)
                                        .map(MailchimpMetadataResponse::getDc)
                                        .flatMap(serverPrefix -> {
                                            MailchimpUser mailchimpUser = new MailchimpUser();
                                            mailchimpUser.setStripeAccountId(stripeAccountId);
                                            mailchimpUser.setToken(accessToken);
                                            mailchimpUser.setServerPrefix(serverPrefix);

                                            return Mono.fromCallable(() -> mailchimpUserRepository.save(mailchimpUser))
                                                    .map(saved -> "Mailchimp token and server prefix saved successfully");
                                        });
                            });
                });
    }
}
