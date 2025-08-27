package com.boustead.connecttostripe.stripe.controller;

import com.boustead.connecttostripe.stripe.StripeUserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/billing")
@CrossOrigin(origins = {"https://connectto.app"})
public class BillingController {

    @Value("${stripe.billing.secret:}")
    private String stripeBillingSecret;

    @Value("${mailchimp.stripe.redirect-uri}")
    private String mailchimpRedirectUrl;

    @Autowired
    private StripeUserRepository stripeUserRepository;

    @PostMapping("/create-checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @RequestBody CheckoutSessionRequest request) {

        // Validate required fields
        if (request.getPriceId() == null || request.getPriceId().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price ID is required");
        }
        
        if (request.getStripeAccountId() == null || request.getStripeAccountId().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe Account ID is required");
        }

        // Verify the stripe account exists in our system
        if (!stripeUserRepository.existsByStripeAccountId(request.getStripeAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Stripe account not found: " + request.getStripeAccountId());
        }

        try {
            Stripe.apiKey = stripeBillingSecret;

            SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(request.getPriceId())
                            .setQuantity(1L)
                            .build())
                    .setSuccessUrl(request.getSuccessUrl() != null ? request.getSuccessUrl() :
                            mailchimpRedirectUrl)
                    .setCancelUrl(request.getCancelUrl() != null ? request.getCancelUrl() :
                            mailchimpRedirectUrl)
                    .putMetadata("stripe_account_id", request.getStripeAccountId())
                    .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                            .setTrialPeriodDays(14L)
                            .putMetadata("stripe_account_id", request.getStripeAccountId())
                            .build());

            // Add customer email if provided
            if (request.getCustomerEmail() != null && !request.getCustomerEmail().isEmpty()) {
                sessionBuilder.setCustomerEmail(request.getCustomerEmail());
            }

            Session session = Session.create(sessionBuilder.build());

            return ResponseEntity.ok(new CheckoutSessionResponse(session.getId(), session.getUrl()));

        } catch (StripeException e) {
            System.err.println("Stripe error creating checkout session: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Failed to create checkout session: " + e.getMessage());
        }
    }
}