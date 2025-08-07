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
@RequestMapping("/mailchimp")
public class MailchimpUserController {

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

    @Autowired
    MailchimpUserRepository mailchimpUserRepository;

    // get users audiences
    // select preferred audience
    @GetMapping("user/audiences")
    public Mono<ResponseEntity<UserAudienceListResponse>> getMailchimpUserAudiences(
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
                            .map(UserAudienceListResponse::new)
                            .map(ResponseEntity::ok);
                });
    }
}
