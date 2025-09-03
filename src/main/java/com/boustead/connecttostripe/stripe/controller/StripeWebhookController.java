package com.boustead.connecttostripe.stripe.controller;

import com.boustead.connecttostripe.billing.SubscriptionValidationService;
import com.boustead.connecttostripe.billing.SubscriptionService;
import com.boustead.connecttostripe.mailchimp.MailchimpUser;
import com.boustead.connecttostripe.mailchimp.MailchimpUserRepository;
import com.boustead.connecttostripe.stripe.service.StripeWebhookCounterService;
import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    @Value("${stripe.endpoint.secret}")
    private String endpointSecret;

    @Autowired
    MailchimpUserRepository mailchimpUserRepository;

    @Autowired
    StripeWebhookCounterService stripeWebhookCounterService;

    @Autowired
    SubscriptionValidationService subscriptionValidationService;

    @Autowired
    SubscriptionService subscriptionService;

    @Value("${environment}")
    private String environment;

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> handleStripeWebhook(@RequestBody String payload,
                                            @RequestHeader(name = "Stripe-Signature") String signatureHeader) {

        Event event;

        try {
            if (endpointSecret != null && signatureHeader != null) {
                event = Webhook.constructEvent(payload, signatureHeader, endpointSecret);
            } else {
                System.err.println("Endpoint secret or signature header missing. Endpoint secret: " + endpointSecret + ", signatureheader: " + signatureHeader);
                event = ApiResource.GSON.fromJson(payload, Event.class);
            }
        } catch (JsonSyntaxException | SignatureVerificationException e) {
            System.err.println("Webhook error: " + e.getMessage());
            return Mono.error(new IllegalArgumentException("Invalid webhook payload or signature"));
        }

        String account = event.getAccount();

        if (account == null || account.isEmpty()) {
            if(environment == "PROD"){
                System.err.println("Received webhook with empty account.");
                return Mono.just("OK");
            } else {
                System.out.println("Received webhook with empty account.");
                System.out.println("Assigning test account id to event.");
                account = "acct_1RvwZk7l0o2aIIbm";
            }

        }

        // Check if account has valid subscription before processing sync events
        if (!subscriptionValidationService.canPerformSync(account)) {
            System.out.println("Account " + account + " cannot perform sync. Reason:");
            System.out.println("  Status: " + subscriptionValidationService.getSubscriptionStatusMessage(account));
            System.out.println("  Usage: " + subscriptionValidationService.getUsageSummary(account));
            return Mono.just("OK");
        }

        // check that account is setup with mailchimp token
        Optional<MailchimpUser> mailchimpUserOpt = mailchimpUserRepository.findByStripeAccountId(account);
        if(mailchimpUserOpt.isEmpty()) {
            System.out.println("Received event but account not linked to Mailchimp");
            return Mono.just("OK");
        }

        MailchimpUser mailchimpUser = mailchimpUserOpt.get();
        if(mailchimpUser.getSelectedAudienceId() == null || mailchimpUser.getSelectedAudienceId().isEmpty()) {
            System.out.println("Received event but no audience selected for account: " + account);
            return Mono.just("OK");
        }

        if(mailchimpUser.getAudienceStatus() == null || mailchimpUser.getAudienceStatus().isEmpty()) {
            System.out.println("Received event but no permissions selected for account: " + account);
            return Mono.just("OK");
        }

        // check that accounts have been set

        StripeObject stripeObject = null;
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        if (deserializer.getObject().isPresent()) {
            stripeObject = deserializer.getObject().get();
        } else {
            System.err.println("Failed to deserialize Stripe event object.");
            return Mono.just("OK");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session checkoutSession = (Session) stripeObject;

                String customerEmail = checkoutSession.getCustomerDetails().getEmail();
                String customerName = checkoutSession.getCustomerDetails().getName();

                if(customerEmail == null || customerEmail.isEmpty()) {
                    System.err.println("Received checkout event for account " + account + " but customer email was empty");
                    return Mono.just("OK");
                }

                stripeWebhookCounterService.incrementCheckoutSessionCount(account);

                // Add customer to selected Mailchimp audience
                return addCustomerToMailchimpAudience(mailchimpUser, customerEmail, customerName)
                        .doOnSuccess(result -> System.out.println("Customer " + customerEmail + " added to Mailchimp audience: " + mailchimpUser.getSelectedAudienceId()))
                        .doOnError(error -> System.err.println("Failed to add customer to Mailchimp: " + error.getMessage()))
                        .then(Mono.just("OK"));
            }
            case "account.application.deauthorized" -> {
                System.out.println("Account " + account + " deauthorized the application");
                
                // Disconnect user from Mailchimp and cancel subscriptions when they deauthorize the app
                String finalAccount = account;
                return Mono.fromCallable(() -> {
                    // Cancel all subscriptions for the account
                    subscriptionService.cancelAllSubscriptionsForAccount(finalAccount);
                    
                    // Delete Mailchimp connection
                    int deletedRows = mailchimpUserRepository.deleteByStripeAccountId(finalAccount);
                    if (deletedRows > 0) {
                        System.out.println("Disconnected Mailchimp account for deauthorized Stripe account: " + finalAccount);
                    } else {
                        System.out.println("No Mailchimp connection found for deauthorized account: " + finalAccount);
                    }
                    return "OK";
                });
            }
            default -> {
                System.out.println("Unhandled event type: " + event.getType());
            }
        }

        return Mono.just("OK");
    }

    private Mono<Void> addCustomerToMailchimpAudience(MailchimpUser mailchimpUser, String customerEmail, String customerName) {
        String token = mailchimpUser.getToken();
        String serverPrefix = mailchimpUser.getServerPrefix();
        String audienceId = mailchimpUser.getSelectedAudienceId();

        WebClient mailchimpClient = WebClient.builder()
                .baseUrl("https://" + serverPrefix + ".api.mailchimp.com/3.0")
                .defaultHeader("Authorization", "OAuth " + token)
                .defaultHeader("Content-Type", "application/json")
                .build();

        Map<String, Object> memberData = new java.util.HashMap<>();
        memberData.put("email_address", customerEmail);
        memberData.put("status", mailchimpUser.getAudienceStatus());
        memberData.put("tags", new String[]{"stripe"});
        
        if (customerName != null && !customerName.trim().isEmpty()) {
            // Split name into first and last name for Mailchimp merge fields
            String[] nameParts = customerName.trim().split("\\s+", 2);
            String firstName = nameParts[0];
            String lastName = nameParts.length > 1 ? nameParts[1] : "";
            
            Map<String, String> mergeFields = new java.util.HashMap<>();
            mergeFields.put("FNAME", firstName);
            if (!lastName.isEmpty()) {
                mergeFields.put("LNAME", lastName);
            }
            memberData.put("merge_fields", mergeFields);
        }

        // Use PUT with email hash to handle both new and existing members
        String subscriberHash;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(customerEmail.toLowerCase().getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            subscriberHash = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Mono.error(new RuntimeException("MD5 algorithm not available", e));
        }

        return mailchimpClient
                .put()
                .uri("/lists/{list_id}/members/{subscriber_hash}", audienceId, subscriberHash)
                .bodyValue(memberData)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                         response -> response.bodyToMono(String.class)
                                 .flatMap(error -> Mono.error(new RuntimeException("Mailchimp API error: " + error))))
                .bodyToMono(String.class)
                .then();
    }
}
