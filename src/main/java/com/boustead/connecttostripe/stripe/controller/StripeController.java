package com.boustead.connecttostripe.stripe.controller;

import com.boustead.connecttostripe.mailchimp.MailchimpUser;
import com.boustead.connecttostripe.mailchimp.MailchimpUserRepository;
import com.boustead.connecttostripe.mailchimp.MailchimpUserResponse;
import com.boustead.connecttostripe.stripe.StripeUser;
import com.boustead.connecttostripe.stripe.StripeUserRepository;
import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

    @Autowired
    private StripeUserRepository stripeUserRepository;

    @Autowired
    private MailchimpUserRepository mailchimpUserRepository;

    @PostMapping("/create")
    public ResponseEntity<CreateUserResponse> createUserIfNotExists(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody Map<String, String> body
    ) {
        String userId = body.get("user_id");
        String accountId = body.get("account_id");

        String payload = "{\"user_id\":\"" + userId + "\",\"account_id\":\"" + accountId + "\"}";

        boolean valid = StripeSignatureVerifier.isValid(signature, payload, stripeSecret);

        if (!valid) {
            return ResponseEntity
                    .badRequest()
                    .body(new CreateUserResponse(false, "Invalid Stripe signature", null));
        }

        Optional<StripeUser> existingUser =
                stripeUserRepository.findByStripeUserIdAndStripeAccountId(userId, accountId);

        if (existingUser.isPresent()) {
            return ResponseEntity.ok(
                    new CreateUserResponse(true, "User already exists", existingUser.get().getStripeAccountId())
            );
        }

        StripeUser newUser = new StripeUser();
        newUser.setStripeUserId(userId);
        newUser.setStripeAccountId(accountId);
        StripeUser saved = stripeUserRepository.save(newUser);

        return ResponseEntity.ok(
                new CreateUserResponse(true, "User created successfully", saved.getStripeAccountId())
        );
    }



    @PostMapping("/account/mailchimp")
    public ResponseEntity<MailchimpUserResponse> getMailchimpUser(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody Map<String, String> body
    ) {
        String userId = body.get("user_id");
        String accountId = body.get("account_id");
        String payload = "{\"user_id\":\"" + userId + "\",\"account_id\":\"" + accountId + "\"}";

        boolean valid = StripeSignatureVerifier.isValid(signature, payload, stripeSecret);

        if (!valid) {
            return ResponseEntity
                    .badRequest()
                    .body(new MailchimpUserResponse(false));
        }

        Optional<MailchimpUser> mailchimpUserOpt = mailchimpUserRepository.findByStripeAccountId(accountId);

        if (mailchimpUserOpt.isPresent()) {
            MailchimpUser user = mailchimpUserOpt.get();
            return ResponseEntity.ok(
                    new MailchimpUserResponse(true)
            );
        } else {
            return ResponseEntity.ok(new MailchimpUserResponse(false));
        }
    }

}
