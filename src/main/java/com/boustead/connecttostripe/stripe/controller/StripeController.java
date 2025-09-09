package com.boustead.connecttostripe.stripe.controller;

import com.boustead.connecttostripe.exception.InvalidStripeSignatureException;
import com.boustead.connecttostripe.mailchimp.MailchimpUser;
import com.boustead.connecttostripe.mailchimp.MailchimpUserRepository;
import com.boustead.connecttostripe.mailchimp.MailchimpUserResponse;
import com.boustead.connecttostripe.stripe.StripeUser;
import com.boustead.connecttostripe.stripe.StripeUserRepository;
import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    private static final Logger logger = LoggerFactory.getLogger(StripeController.class);

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

    @Autowired
    private StripeUserRepository stripeUserRepository;

    @Autowired
    private MailchimpUserRepository mailchimpUserRepository;

    @PostMapping("/create")
    public Mono<ResponseEntity<CreateUserResponse>> createUserIfNotExists(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody Map<String, String> body
    ) {
        
        String userId = body.get("user_id");
        String accountId = body.get("account_id");

        if (userId == null || accountId == null) {
            logger.error("Missing required fields - UserId: {}, AccountId: {}", userId, accountId);
            return Mono.error(new IllegalArgumentException("Missing required fields: user_id and account_id"));
        }

        String payload = "{\"user_id\":\"" + userId + "\",\"account_id\":\"" + accountId + "\"}";
        logger.debug("Generated payload for signature verification: {}", payload);

        boolean valid = StripeSignatureVerifier.isValid(signature, payload, stripeSecret);

        if (!valid) {
            logger.error("Invalid Stripe signature for user creation. UserId: {}, AccountId: {}, Signature: {}, Payload: {}", 
                userId, accountId, signature, payload);
            return Mono.error(new InvalidStripeSignatureException("Invalid Stripe signature for user creation request"));
        }

        return Mono.fromCallable(() -> {
            Optional<StripeUser> existingUser =
                    stripeUserRepository.findByStripeUserIdAndStripeAccountId(userId, accountId);

            if (existingUser.isPresent()) {
                logger.info("User already exists. UserId: {}, AccountId: {}", userId, accountId);
                return ResponseEntity.ok(
                        new CreateUserResponse(true, "User already exists", existingUser.get().getStripeAccountId())
                );
            }

            StripeUser newUser = new StripeUser();
            newUser.setStripeUserId(userId);
            newUser.setStripeAccountId(accountId);
            StripeUser saved = stripeUserRepository.save(newUser);

            logger.info("User created successfully. UserId: {}, AccountId: {}", userId, accountId);
            return ResponseEntity.ok(
                    new CreateUserResponse(true, "User created successfully", saved.getStripeAccountId())
            );
        });
    }



    @GetMapping("/cors-test")
    public Mono<ResponseEntity<Map<String, String>>> corsTest() {
        System.out.println("=== CORS Test Endpoint Hit ===");
        return Mono.fromCallable(() -> {
            Map<String, String> response = Map.of(
                "message", "CORS is working!", 
                "timestamp", java.time.Instant.now().toString()
            );
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/account/mailchimp")
    public Mono<ResponseEntity<MailchimpUserResponse>> getMailchimpUser(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody Map<String, String> body
    ) {
        String userId = body.get("user_id");
        String accountId = body.get("account_id");
        String payload = "{\"user_id\":\"" + userId + "\",\"account_id\":\"" + accountId + "\"}";

        boolean valid = StripeSignatureVerifier.isValid(signature, payload, stripeSecret);

        if (!valid) {
            logger.error("Invalid Stripe signature for Mailchimp user lookup. UserId: {}, AccountId: {}", userId, accountId);
            return Mono.error(new InvalidStripeSignatureException("Invalid Stripe signature for Mailchimp user lookup"));
        }

        return Mono.fromCallable(() -> {
            Optional<MailchimpUser> mailchimpUserOpt = mailchimpUserRepository.findByStripeAccountId(accountId);

            if (mailchimpUserOpt.isPresent()) {
                logger.info("Mailchimp user found for AccountId: {}", accountId);
                return ResponseEntity.ok(new MailchimpUserResponse(true));
            } else {
                logger.info("No Mailchimp user found for AccountId: {}", accountId);
                return ResponseEntity.ok(new MailchimpUserResponse(false));
            }
        });
    }

}
