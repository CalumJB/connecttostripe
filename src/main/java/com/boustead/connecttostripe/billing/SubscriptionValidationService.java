package com.boustead.connecttostripe.billing;

import com.boustead.connecttostripe.stripe.service.StripeWebhookCounterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SubscriptionValidationService {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private StripeWebhookCounterService counterService;

    @Autowired
    private PlanConfigurationService planConfigurationService;

    /**
     * Check if a Stripe account has an active subscription
     * @param stripeAccountId The Stripe Connect account ID
     * @return true if account has active or trialing subscription
     */
    public boolean hasValidSubscription(String stripeAccountId) {
        return subscriptionService.hasActiveSubscription(stripeAccountId);
    }

    /**
     * Get subscription details for an account
     * @param stripeAccountId The Stripe Connect account ID
     * @return Optional containing subscription details if active
     */
    public Optional<Subscription> getActiveSubscription(String stripeAccountId) {
        return subscriptionService.getActiveSubscriptionForAccount(stripeAccountId);
    }

    /**
     * Check if account can perform sync operations (considering usage limits)
     * @param stripeAccountId The Stripe Connect account ID
     * @return true if sync is allowed, false otherwise
     */
    public boolean canPerformSync(String stripeAccountId) {
        Optional<Subscription> subscription = getActiveSubscription(stripeAccountId);
        
        if (subscription.isEmpty()) {
            return false;
        }
        
        Subscription sub = subscription.get();
        String status = sub.getStatus();
        
        // First check if subscription is active
        if (!"active".equals(status) && !"trialing".equals(status)) {
            // Inactive subscription - fall back to free tier
            return false;
        }
        
        // Then check usage limits for paid plan
        return isWithinUsageLimits(stripeAccountId, sub);
    }

    /**
     * Check if account is within usage limits for their plan
     * @param stripeAccountId The Stripe Connect account ID
     * @param subscription The active subscription
     * @return true if within limits, false if over limit
     */
    private boolean isWithinUsageLimits(String stripeAccountId, Subscription subscription) {
        // Get current month usage
        Integer currentUsage = counterService.getCurrentMonthCheckoutSessionCount(stripeAccountId);
        
        // Get plan configuration
        PlanConfiguration planConfig = planConfigurationService.getPlanByName(subscription.getPlanName());
        
        // Check if within limits
        return planConfig.canPerformSync(currentUsage);
    }

    /**
     * Check if account is within free tier limits
     * @param stripeAccountId The Stripe Connect account ID
     * @return true if within free tier limits
     */
    private boolean isWithinFreeTierLimits(String stripeAccountId) {
        Integer currentUsage = counterService.getCurrentMonthCheckoutSessionCount(stripeAccountId);
        PlanConfiguration freePlan = planConfigurationService.getPlanByName("FREE");
        return freePlan.canPerformSync(currentUsage);
    }

    /**
     * Get subscription status message for user feedback
     * @param stripeAccountId The Stripe Connect account ID
     * @return Human-readable status message
     */
    public String getSubscriptionStatusMessage(String stripeAccountId) {
        Optional<Subscription> subscription = getActiveSubscription(stripeAccountId);
        Integer currentUsage = counterService.getCurrentMonthCheckoutSessionCount(stripeAccountId);
        
        if (subscription.isEmpty()) {
            return String.format("Free tier.");
        }
        
        Subscription sub = subscription.get();
        PlanConfiguration planConfig = planConfigurationService.getPlanByName(sub.getPlanName());
        
        String baseMessage = switch (sub.getStatus()) {
            case "trialing" -> "Free trial active";
            case "active" -> "Subscription active (" + planConfig.getDisplayName() + ")";
            case "past_due" -> "Payment failed. Please update your payment method.";
            case "canceled" -> "Subscription canceled. Sync access has ended.";
            case "unpaid" -> "Subscription unpaid. Please resolve payment issues.";
            default -> "Subscription status: " + sub.getStatus();
        };
        
        // Add usage information for active subscriptions
        if ("active".equals(sub.getStatus()) || "trialing".equals(sub.getStatus())) {
            if (planConfig.isUnlimited()) {
                baseMessage += ". Usage this month: " + currentUsage + " syncs (unlimited plan).";
            } else {
                baseMessage += String.format(". Usage this month: %d/%d syncs.", 
                    currentUsage, planConfig.getMonthlySyncLimit());
            }
        }
        
        return baseMessage;
    }

    /**
     * Get usage summary for an account
     */
    public String getUsageSummary(String stripeAccountId) {
        Optional<Subscription> subscription = getActiveSubscription(stripeAccountId);
        Integer currentUsage = counterService.getCurrentMonthCheckoutSessionCount(stripeAccountId);
        
        if (subscription.isEmpty()) {
            PlanConfiguration freePlan = planConfigurationService.getPlanByName("FREE");
            return String.format("%s: %d/%d syncs this month", 
                freePlan.getDisplayName(), currentUsage, freePlan.getMonthlySyncLimit());
        }
        
        Subscription sub = subscription.get();
        PlanConfiguration planConfig = planConfigurationService.getPlanByName(sub.getPlanName());
        
        return String.format("%s: %d/%d syncs this month", 
            planConfig.getDisplayName(), currentUsage, planConfig.getMonthlySyncLimit());
    }
}