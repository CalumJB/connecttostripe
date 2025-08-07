package com.boustead.connecttostripe.stripe.controller;

import com.boustead.connecttostripe.mailchimp.MailchimpUserRepository;
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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    @Value("${stripe.endpoint.secret}")
    private String endpointSecret;

    @Autowired
    MailchimpUserRepository mailchimpUserRepository;

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
            System.err.println("Received webhook with empty account.");
            return Mono.just("OK");
        }

        // check that account is setup with mailchimp token and that an audience has been selected
        if(mailchimpUserRepository.existsByStripeAccountId(account)) {
            System.out.println("Received event but account not linked");
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

                if(customerEmail == null || customerEmail.isEmpty()) {
                    System.err.println("Received checkout event for account " + account + " but customer email was empty");
                    return Mono.just("OK");
                }

                // now we can send it to mailchimp list
                // get mailchimp audience

                // send to specific audience

                System.out.println("PaymentIntent was successful: " + checkoutSession.getId());
            }
            default -> {
                System.out.println("Unhandled event type: " + event.getType());
            }
        }

        return Mono.just("OK");
    }
}
