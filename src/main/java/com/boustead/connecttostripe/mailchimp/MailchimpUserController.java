package com.boustead.connecttostripe.mailchimp;

import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/mailchimp")
public class MailchimpUserController {

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

    @Autowired
    MailchimpUserRepository mailchimpUserRepository;

@PostMapping("user/audiences")
    public Mono<ResponseEntity<MailchimpAudienceList>> getMailchimpUserAudiences(
            @RequestHeader(name = "Stripe-Signature") String signature,
            @RequestBody Map<String, String> body) {

        String userId = body.get("user_id");
        String accountId = body.get("account_id");

        String payload = String.format("{\"user_id\":\"%s\",\"account_id\":\"%s\"}", userId, accountId);

        if (!StripeSignatureVerifier.isValid(signature, payload, stripeSecret)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid signature for userId: " + userId + ", accountId: " + accountId
            ));
        }

        return Mono.fromCallable(() -> mailchimpUserRepository.findByStripeAccountId(accountId))
                .flatMap(optionalUser -> {
                    if (optionalUser.isEmpty()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Mailchimp user not found for accountId: " + accountId
                        ));
                    }

                    MailchimpUser user = optionalUser.get();
                    String token = user.getToken();
                    String serverPrefix = user.getServerPrefix();

                    WebClient mailchimpClient = WebClient.builder()
                            .baseUrl("https://" + serverPrefix + ".api.mailchimp.com/3.0")
                            .defaultHeader("Authorization", "OAuth " + token)
                            .build();

                    return mailchimpClient
                            .get()
                            .uri("/lists")
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, response ->
                                    response.bodyToMono(String.class).flatMap(error ->
                                            Mono.error(new RuntimeException("Mailchimp error: " + error))
                                    )
                            )
                            .bodyToMono(MailchimpAudienceList.class)
                            .map(ResponseEntity::ok);
                });
    }

    @PutMapping("user/audience/select")
    public Mono<ResponseEntity<String>> selectMailchimpAudience(
            @RequestHeader(name = "Stripe-Signature") String signature,
            @RequestBody Map<String, String> body) {

        String userId = body.get("user_id");
        String accountId = body.get("account_id");
        String audienceId = body.get("audience_id");

        String payload = String.format("{\"user_id\":\"%s\",\"account_id\":\"%s\"}", userId, accountId);

        if (!StripeSignatureVerifier.isValid(signature, payload, stripeSecret)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid signature for userId: " + userId + ", accountId: " + accountId
            ));
        }

        if (audienceId == null || audienceId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "audience_id is required"
            ));
        }

        return Mono.fromCallable(() -> {
            return mailchimpUserRepository.findByStripeAccountId(accountId)
                    .map(user -> {
                        user.setSelectedAudienceId(audienceId);
                        mailchimpUserRepository.save(user);
                        return ResponseEntity.ok("Audience selected successfully");
                    })
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Mailchimp user not found for accountId: " + accountId
                    ));
        });
    }

    @PostMapping("user/audience/selected")
    public Mono<ResponseEntity<Map<String, String>>> getSelectedAudience(
            @RequestHeader(name = "Stripe-Signature") String signature,
            @RequestBody Map<String, String> body) {

        String userId = body.get("user_id");
        String accountId = body.get("account_id");

        String payload = String.format("{\"user_id\":\"%s\",\"account_id\":\"%s\"}", userId, accountId);

        if (!StripeSignatureVerifier.isValid(signature, payload, stripeSecret)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid signature for userId: " + userId + ", accountId: " + accountId
            ));
        }

        return Mono.fromCallable(() -> {
            return mailchimpUserRepository.findByStripeAccountId(accountId)
                    .map(user -> {
                        String selectedAudienceId = user.getSelectedAudienceId();
                        Map<String, String> response = Map.of(
                                "selected_audience_id", selectedAudienceId != null ? selectedAudienceId : ""
                        );
                        return ResponseEntity.ok(response);
                    })
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Mailchimp user not found for accountId: " + accountId
                    ));
        });
    }
}
