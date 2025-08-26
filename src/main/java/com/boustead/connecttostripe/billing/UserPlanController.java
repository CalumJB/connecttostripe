package com.boustead.connecttostripe.billing;

import com.boustead.connecttostripe.stripe.service.StripeSignatureVerifier;
import com.boustead.connecttostripe.stripe.service.StripeWebhookCounterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserPlanController {

    @Value("${stripe.signing.secret}")
    private String stripeSecret;

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
}