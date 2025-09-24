package com.boustead.connecttostripe.billing;

import com.boustead.connecttostripe.exception.GlobalExceptionHandler;
import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.StripeCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.SubscriptionCancelParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PlanConfigurationService planConfigurationService;

    @Autowired
    private BillingMailchimpService billingMailchimpService;

    @Value("${stripe.billing.secret}")
    private String stripeBillingSecret;

    @Transactional
    public Subscription createOrUpdateSubscription(com.stripe.model.Subscription stripeSubscription, String stripeAccountId) {
        Optional<Subscription> existingSubscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscription.getId());

        Subscription subscription = existingSubscription.orElse(new Subscription());
        
        subscription.setStripeSubscriptionId(stripeSubscription.getId());
        subscription.setStripeCustomerId(stripeSubscription.getCustomer());
        subscription.setStripeAccountId(stripeAccountId);
        subscription.setStatus(stripeSubscription.getStatus());
        
        // Fetch customer email, name and card details from Stripe API if not already set
        if ((subscription.getCustomerEmail() == null || subscription.getCustomerName() == null || subscription.getHasCardDetails() == null) && stripeSubscription.getCustomer() != null) {
            try {
                Customer customer = Customer.retrieve(stripeSubscription.getCustomer(),
                        RequestOptions.builder()
                                .setApiKey(stripeBillingSecret)
                                .build());
                if (subscription.getCustomerEmail() == null) {
                    subscription.setCustomerEmail(customer.getEmail());
                }
                if (subscription.getCustomerName() == null) {
                    subscription.setCustomerName(customer.getName());
                }
                
                // Check if customer has card details
                if (subscription.getHasCardDetails() == null || !subscription.getHasCardDetails()) {
                    boolean hasCard = checkCustomerHasCardDetails(stripeSubscription.getCustomer());
                    subscription.setHasCardDetails(hasCard);
                }
                
                logger.info("Retrieved customer details - email: {}, name: {}, hasCard: {} for subscription: {}", 
                           customer.getEmail(), customer.getName(), subscription.getHasCardDetails(), stripeSubscription.getId());
            } catch (Exception e) {
                logger.warn("Failed to retrieve customer details for subscription {}: {}", stripeSubscription.getId(), e.getMessage());
            }
        }
        
        // Extract plan information from subscription items
        extractAndSetPlanDetails(stripeSubscription, subscription);

        Subscription savedSubscription = subscriptionRepository.save(subscription);
        
        // Sync to Mailchimp billing list
        billingMailchimpService.syncSubscriptionToMailchimp(savedSubscription)
                .subscribe();
        
        return savedSubscription;
    }

    @Transactional
    public void cancelSubscription(String stripeSubscriptionId) {
        Optional<Subscription> subscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId);
        
        if (subscription.isPresent()) {
            Subscription sub = subscription.get();
            sub.setStatus("canceled");
            Subscription savedSubscription = subscriptionRepository.save(sub);
            
            // Sync canceled subscription to Mailchimp billing list
            billingMailchimpService.syncSubscriptionToMailchimp(savedSubscription)
                    .subscribe();
        }
    }

    @Transactional
    public void cancelAllSubscriptionsForAccount(String stripeAccountId) {
        List<Subscription> subscriptions = subscriptionRepository.findActiveSubscriptionsByAccountId(stripeAccountId);
        
        if (!subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                try {
                    // Cancel the subscription with Stripe API
                    RequestOptions requestOptions = RequestOptions.builder()
                            .setApiKey(stripeBillingSecret)
                            .build();
                    
                    com.stripe.model.Subscription stripeSubscription = com.stripe.model.Subscription.retrieve(
                            subscription.getStripeSubscriptionId(),
                            requestOptions);
                    stripeSubscription.cancel(SubscriptionCancelParams.builder().build(), requestOptions);
                    
                    //This is trigger the cancel webhook and call cancelSubscription which will handle database update etc
                    
                    logger.info("Canceled Stripe subscription: {} for account: {}", 
                               subscription.getStripeSubscriptionId(), stripeAccountId);
                } catch (Exception e) {
                    logger.error("Failed to cancel Stripe subscription: {} for account: {}. Error: {}", 
                                subscription.getStripeSubscriptionId(), stripeAccountId, e.getMessage());
                }
            }
            logger.info("Initiated cancellation for {} subscriptions for account: {}", subscriptions.size(), stripeAccountId);
        } else {
            logger.info("No active subscriptions found to cancel for account: {}", stripeAccountId);
        }
    }

    public boolean hasActiveSubscription(String stripeAccountId) {
        return subscriptionRepository.hasActiveSubscription(stripeAccountId);
    }

    public Optional<Subscription> getActiveSubscriptionForAccount(String stripeAccountId) {
        List<Subscription> activeSubscriptions = subscriptionRepository.findActiveSubscriptionsByAccountId(stripeAccountId);
        
        if (activeSubscriptions.isEmpty()) {
            return Optional.empty();
        }
        
        if (activeSubscriptions.size() == 1) {
            return Optional.of(activeSubscriptions.get(0));
        }
        
        // Multiple active subscriptions found - log error and return the one with highest allowance
        logger.error("Multiple active subscriptions found for account {}: {} subscriptions. Returning subscription with highest allowance.", 
                    stripeAccountId, activeSubscriptions.size());

        // return the plan with the highest allowance
        return activeSubscriptions.stream()
                .map(subscription -> {
                    PlanConfiguration config = planConfigurationService.getPlanByName(subscription.getPlanName());
                    return new SubscriptionWithAllowance(subscription, config.getMonthlySyncLimit());
                })
                .max((s1, s2) -> {
                    // Unlimited plans (-1) should always be considered highest
                    if (s1.allowance == -1 && s2.allowance == -1) return 0;
                    if (s1.allowance == -1) return 1;
                    if (s2.allowance == -1) return -1;
                    return Integer.compare(s1.allowance, s2.allowance);
                })
                .map(subscriptionWithAllowance -> subscriptionWithAllowance.subscription);
    }
    
    private static class SubscriptionWithAllowance {
        final Subscription subscription;
        final int allowance;
        
        SubscriptionWithAllowance(Subscription subscription, int allowance) {
            this.subscription = subscription;
            this.allowance = allowance;
        }
    }

    public Optional<Subscription> getSubscriptionByCustomer(String stripeCustomerId) {
        return subscriptionRepository.findByStripeCustomerId(stripeCustomerId);
    }

    @Transactional
    public void updateCustomerCardDetailsStatus(String stripeCustomerId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByStripeCustomerId(stripeCustomerId);
        
        if (subscriptionOpt.isPresent()) {
            Subscription subscription = subscriptionOpt.get();
            try {
                // Update card details status
                boolean hasCard = checkCustomerHasCardDetails(stripeCustomerId);
                subscription.setHasCardDetails(hasCard);
                
                // Update customer name from Stripe
                RequestOptions requestOptions = RequestOptions.builder()
                        .setApiKey(stripeBillingSecret)
                        .build();
                Customer customer = Customer.retrieve(stripeCustomerId, requestOptions);
                if (customer.getName() != null) {
                    subscription.setCustomerName(customer.getName());
                }
                
                Subscription savedSubscription = subscriptionRepository.save(subscription);
                
                // Sync updated subscription to Mailchimp billing list
                billingMailchimpService.syncSubscriptionToMailchimp(savedSubscription)
                        .subscribe();
                
                logger.info("Updated card details status to {} and customer name to '{}' for customer: {}", 
                           hasCard, customer.getName(), stripeCustomerId);
            } catch (Exception e) {
                logger.warn("Failed to update customer details for customer {}: {}", stripeCustomerId, e.getMessage());
            }
        } else {
            logger.info("No subscription found for customer: {} - skipping customer details update", stripeCustomerId);
        }
    }

    private void extractAndSetPlanDetails(com.stripe.model.Subscription stripeSubscription, Subscription subscription) {
        String planName = null;
        
        // Try to get plan name from subscription items
        if (stripeSubscription.getItems() != null && !stripeSubscription.getItems().getData().isEmpty()) {
            SubscriptionItem item = stripeSubscription.getItems().getData().get(0);
            if (item.getPrice() != null) {
                String priceId = item.getPrice().getId();
                planName = planConfigurationService.determinePlanName(priceId);
                if(planName.equals("UNKNOWN")){
                    logger.error("Subscription created with UNKNOWN plan for stripe account " + subscription.getStripeAccountId());
                }
            }
        }
        
        // Fallback: try to get plan info from metadata
        if (planName == null && stripeSubscription.getMetadata() != null) {
            planName = stripeSubscription.getMetadata().get("plan_name");
        }
        
        subscription.setPlanName(planName);
    }
    
    private boolean checkCustomerHasCardDetails(String customerId) {
        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeBillingSecret)
                    .build();
            
            StripeCollection<PaymentMethod> paymentMethods = PaymentMethod.list(
                    PaymentMethodListParams.builder()
                            .setCustomer(customerId)
                            .setType(PaymentMethodListParams.Type.CARD)
                            .build(),
                    requestOptions);
            
            return paymentMethods.getData() != null && !paymentMethods.getData().isEmpty();
        } catch (Exception e) {
            logger.warn("Failed to check payment methods for customer {}: {}", customerId, e.getMessage());
            return false;
        }
    }
}