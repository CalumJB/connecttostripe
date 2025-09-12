package com.boustead.connecttostripe.stripe.controller;

import com.boustead.connecttostripe.billing.SubscriptionService;
import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/stripe")
public class StripeBillingWebhookController {

    @Value("${stripe.billing.endpoint.secret}")
    private String billingEndpointSecret;

    @Value("${environment}")
    private String environment;

    @Autowired
    private SubscriptionService subscriptionService;

    @PostMapping(value = "/billing-webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> handleStripeBillingWebhook(@RequestBody String payload,
                                                   @RequestHeader(name = "Stripe-Signature") String signatureHeader) {

        Event event;

        try {
            if (billingEndpointSecret != null && signatureHeader != null) {
                event = Webhook.constructEvent(payload, signatureHeader, billingEndpointSecret);
            } else {
                System.err.println("Billing endpoint secret or signature header missing. Endpoint secret: " + billingEndpointSecret + ", signatureheader: " + signatureHeader);
                event = ApiResource.GSON.fromJson(payload, Event.class);
            }
        } catch (JsonSyntaxException | SignatureVerificationException e) {
            System.err.println("Billing webhook error: " + e.getMessage());
            return Mono.error(new IllegalArgumentException("Invalid billing webhook payload or signature"));
        }

        // These events should NOT have an account field since they're from your main account
        String account = event.getAccount();
        if (account != null) {
            System.out.println("Warning: Billing webhook received with account field: " + account);
        }

        StripeObject stripeObject = null;
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        if (deserializer.getObject().isPresent()) {
            stripeObject = deserializer.getObject().get();
        } else {
            System.err.println("Failed to deserialize Stripe billing event object.");
            return Mono.just("OK");
        }
        System.out.println("Event: " + event.getType());
        switch (event.getType()) {
            case "customer.subscription.created" -> {
                Subscription subscription = (Subscription) stripeObject;
                System.out.println("New subscription created: " + subscription.getId() + " for customer: " + subscription.getCustomer());
                
                return Mono.fromCallable(() -> {
                    // Get account ID from subscription metadata or customer data
                    String accountId = getAccountIdFromSubscription(subscription);
                    if (accountId != null) {
                        subscriptionService.createOrUpdateSubscription(subscription, accountId);
                        System.out.println("Stored subscription " + subscription.getId() + " for account: " + accountId);
                    } else {
                        System.err.println("Could not determine account ID for subscription: " + subscription.getId());
                    }
                    return "OK";
                });
            }
            case "customer.subscription.updated" -> {
                Subscription subscription = (Subscription) stripeObject;
                System.out.println("Subscription updated: " + subscription.getId() + " status: " + subscription.getStatus());
                
                return Mono.fromCallable(() -> {
                    String accountId = getAccountIdFromSubscription(subscription);
                    if (accountId != null) {
                        subscriptionService.createOrUpdateSubscription(subscription, accountId);
                        System.out.println("Updated subscription " + subscription.getId() + " status: " + subscription.getStatus());
                    } else {
                        System.err.println("Could not determine account ID for subscription update: " + subscription.getId());
                    }
                    return "OK";
                });
            }
            case "customer.subscription.deleted" -> {
                Subscription subscription = (Subscription) stripeObject;
                System.out.println("Subscription canceled: " + subscription.getId() + " for customer: " + subscription.getCustomer());
                
                return Mono.fromCallable(() -> {
                    subscriptionService.cancelSubscription(subscription.getId());
                    System.out.println("Marked subscription as canceled: " + subscription.getId());
                    return "OK";
                });
            }
            case "checkout.session.completed" -> {
                Session checkoutSession = (Session) stripeObject;
                String customerId = checkoutSession.getCustomer();
                String stripeAccountId = checkoutSession.getMetadata().get("stripe_account_id");
                
                System.out.println("Billing checkout completed for customer: " + customerId);
                System.out.println("Linked to Stripe account: " + stripeAccountId);
                
                if (stripeAccountId != null) {
                    System.out.println("Successfully linked checkout to account: " + stripeAccountId);
                    // Note: The actual subscription linking will happen in customer.subscription.created
                } else {
                    System.err.println("No stripe_account_id found in checkout session metadata");
                }
                
                return Mono.just("OK");
            }
            case "customer.updated" -> {
                Customer customer = (Customer) stripeObject;
                System.out.println("Customer updated: " + customer.getId());
                
                return Mono.fromCallable(() -> {
                    // Update card details status for all subscriptions of this customer
                    subscriptionService.updateCustomerCardDetailsStatus(customer.getId());
                    System.out.println("Updated card details status for customer: " + customer.getId());
                    return "OK";
                });
            }
//            case "invoice.payment_succeeded" -> {
//                Invoice invoice = (Invoice) stripeObject;
//                String subscriptionId = invoice.getSubscription();
//                System.out.println("Invoice payment succeeded for subscription: " + subscriptionId);
//
//                return Mono.fromCallable(() -> {
//                    if (subscriptionId != null) {
//                        // Subscription should already be updated via subscription.updated event
//                        System.out.println("Payment successful - subscription access confirmed");
//                    }
//                    return "OK";
//                });
//            }
//            case "invoice.payment_failed" -> {
//                Invoice invoice = (Invoice) stripeObject;
//                String subscriptionId = invoice.getSubscription();
//                System.out.println("Invoice payment failed for subscription: " + subscriptionId);
//
//                return Mono.fromCallable(() -> {
//                    if (subscriptionId != null) {
//                        System.out.println("Payment failed - subscription may be suspended by Stripe");
//                        // Stripe will update subscription status automatically
//                        // You might want to send notification emails here
//                    }
//                    return "OK";
//                });
//            }
            default -> {
                System.out.println("Unhandled billing event type: " + event.getType());
            }
        }

        return Mono.just("OK");
    }

    private String getAccountIdFromSubscription(Subscription subscription) {
        // First try to get from subscription metadata
        if (subscription.getMetadata() != null && subscription.getMetadata().containsKey("stripe_account_id")) {
            return subscription.getMetadata().get("stripe_account_id");
        }
        
        // Alternative: look up by customer ID in our stored checkout session data
        // This requires the account ID to have been stored during checkout.session.completed
        return findAccountIdByCustomer(subscription.getCustomer());
    }
    
    private String findAccountIdByCustomer(String customerId) {
        // Look up account ID from existing subscription
        return subscriptionService.getSubscriptionByCustomer(customerId)
                .map(com.boustead.connecttostripe.billing.Subscription::getStripeAccountId)
                .orElse(null);
    }
}