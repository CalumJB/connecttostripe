package com.boustead.connecttostripe.billing;

import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import com.boustead.connecttostripe.stripe.service.StripeWebhookCounterService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class UserPlanController {

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

    @Value("${stripe.billing.secret:}")
    private String stripeBillingSecret;

    @Value("${mailchimp.stripe.redirect-uri}")
    private String mailchimpRedirectUrl;

    @Autowired
    private SubscriptionValidationService subscriptionValidationService;

    @Autowired
    private StripeWebhookCounterService counterService;

    @Autowired
    private PlanConfigurationService planConfigurationService;

    @PostMapping("/plan-info")
    public Mono<ResponseEntity<UserPlanInfoResponse>> getUserPlanInfo(
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
            // Get active subscription for account
            var subscription = subscriptionValidationService.getActiveSubscription(accountId);
            
            if (subscription.isEmpty()) {
                // No active subscription - use free tier configuration
                Integer currentUsage = counterService.getCurrentMonthCheckoutSessionCount(accountId);
                PlanConfiguration freePlan = planConfigurationService.getPlanByName("FREE");
                
                return ResponseEntity.ok(new UserPlanInfoResponse(
                    "FREE", 
                    freePlan.getDisplayName(), 
                    freePlan.getMonthlySyncLimit(), 
                    currentUsage, 
                    "active"
                ));
            }

            var sub = subscription.get();
            String planName = sub.getPlanName() != null ? sub.getPlanName() : "UNKNOWN";
            
            // Get plan configuration
            PlanConfiguration planConfig = planConfigurationService.getPlanByName(planName);
            
            // Get current month usage
            Integer currentUsage = counterService.getCurrentMonthCheckoutSessionCount(accountId);
            
            // Create response
            UserPlanInfoResponse response = new UserPlanInfoResponse(
                planName,
                planConfig.getDisplayName(),
                planConfig.getMonthlySyncLimit(),
                currentUsage,
                sub.getStatus()
            );
            
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/customer-portal")
    public Mono<ResponseEntity<CustomerPortalResponse>> getCustomerPortal(
            @RequestHeader(name = "Stripe-Signature") String signature,
            @RequestBody Map<String, String> body) {

        String userId = body.get("user_id");
        String accountId = body.get("account_id");

        String payload = String.format("{\"user_id\":\"%s\",\"account_id\":\"%s\"}",
            userId, accountId);

        if (!StripeSignatureVerifier.isValid(signature, payload, stripeSecret)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid signature for userId: " + userId + ", accountId: " + accountId
            ));
        }

        return Mono.fromCallable(() -> {
            // Get active subscription for account
            Optional<Subscription> subscription = subscriptionValidationService.getActiveSubscription(accountId);
            
            if (subscription.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "No active subscription found for account: " + accountId);
            }

            String customerId = subscription.get().getStripeCustomerId();
            if (customerId == null || customerId.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "No customer ID associated with subscription for account: " + accountId);
            }

            try {
                Stripe.apiKey = stripeBillingSecret;

                SessionCreateParams params = SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(mailchimpRedirectUrl)
                        .build();

                Session portalSession = Session.create(params);

                return ResponseEntity.ok(new CustomerPortalResponse(portalSession.getUrl()));

            } catch (StripeException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Failed to create customer portal session: " + e.getMessage());
            }
        });
    }

    public static class CustomerPortalResponse {
        private String url;

        public CustomerPortalResponse() {}

        public CustomerPortalResponse(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    @PostMapping("/usage")
    public Mono<ResponseEntity<UsageResponse>> getUserUsage(
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
            StripeWebhookCounterService.UsageData usageData = counterService.getLast12MonthsUsage(accountId);
            return ResponseEntity.ok(new UsageResponse(usageData));
        });
    }

    public static class UsageResponse {
        private final List<MonthlyUsageInfo> monthlyBreakdown;
        private final int totalSessions;

        public UsageResponse(StripeWebhookCounterService.UsageData usageData) {
            this.monthlyBreakdown = usageData.getMonthlyBreakdown().stream()
                    .map(monthly -> new MonthlyUsageInfo(
                        monthly.getYearMonth(),
                        monthly.getMonthName(),
                        monthly.getSessionCount()
                    ))
                    .collect(java.util.stream.Collectors.toList());
            this.totalSessions = usageData.getTotalSessions();
        }

        public List<MonthlyUsageInfo> getMonthlyBreakdown() {
            return monthlyBreakdown;
        }

        public int getTotalSessions() {
            return totalSessions;
        }
    }

    public static class MonthlyUsageInfo {
        private final String yearMonth;
        private final String monthName;
        private final int sessionCount;

        public MonthlyUsageInfo(String yearMonth, String monthName, int sessionCount) {
            this.yearMonth = yearMonth;
            this.monthName = monthName;
            this.sessionCount = sessionCount;
        }

        public String getYearMonth() {
            return yearMonth;
        }

        public String getMonthName() {
            return monthName;
        }

        public int getSessionCount() {
            return sessionCount;
        }
    }
}