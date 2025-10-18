package com.boustead.connecttostripe.billing;

import com.boustead.connecttostripe.mailchimp.RetryHelper;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class BillingMailchimpService {

    private static final Logger logger = LoggerFactory.getLogger(BillingMailchimpService.class);

    @Value("${mailchimp.billing.api-key}")
    private String mailchimpApiKey;

    @Value("${mailchimp.billing.audience-id}")
    private String billingAudienceId;

    @Value("${mailchimp.billing.server-prefix}")
    private String serverPrefix;

    @Value("${environment}")
    private String environment;

    @Autowired
    private WebClient mailchimpApiClient;

    public Mono<Void> syncSubscriptionToMailchimp(Subscription subscription) {
        if (subscription.getCustomerEmail() == null) {
            logger.warn("Cannot sync subscription {} to Mailchimp - no customer email", subscription.getId());
            return Mono.empty();
        }

        if(subscription.getCustomerEmail().contains("stripe.com")) {
            return Mono.empty();
        }

        try {
            String emailHash = generateEmailHash(subscription.getCustomerEmail());

            BillingContactRequest request = new BillingContactRequest(
                    subscription.getCustomerEmail(),
                    subscription.getCustomerName(),
                    generateTags(subscription),
                    Map.of(
                            "STRIPE_ID", subscription.getStripeAccountId() != null ? subscription.getStripeAccountId() : "",
                            "CUST_ID", subscription.getStripeCustomerId() != null ? subscription.getStripeCustomerId() : "",
                            "SUB_ID", subscription.getStripeSubscriptionId() != null ? subscription.getStripeSubscriptionId() : "",
                            "PLAN", subscription.getPlanName() != null ? subscription.getPlanName() : ""
                    )
            );

            return mailchimpApiClient.put()
                    .uri("https://{server}.api.mailchimp.com/3.0/lists/{audienceId}/members/{emailHash}",
                         serverPrefix, billingAudienceId, emailHash)
                    .header("Authorization", "Bearer " + mailchimpApiKey)
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .doOnSuccess(result -> logger.info("Successfully synced subscription {} to Mailchimp billing list", subscription.getId()))
                    .onErrorResume(error1 -> {
                        logger.error("Billing mailchimp add or update member error: {}", error1.getMessage(), error1);
                        return Mono.empty();
                    })
                    .retryWhen(RetryHelper.webhookOperationRetry())
                    .then();

        } catch (Exception e) {
            logger.error("Failed to sync subscription {} to Mailchimp: {}", subscription.getId(), e.getMessage(), e);
            return Mono.empty();
        }
    }

    private List<String> generateTags(Subscription subscription) {
        List<String> tags = Arrays.asList(
                "status:" + (subscription.getStatus() != null ? subscription.getStatus() : "unknown"),
                "has-card:" + (subscription.getHasCardDetails() != null ? subscription.getHasCardDetails() : false)
        );
        
        // Add test tag if not in production environment
        if (!"PROD".equals(environment)) {
            tags = new java.util.ArrayList<>(tags);
            tags.add("test");
        }
        
        return tags;
    }

    private String generateEmailHash(String email) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(email.toLowerCase().getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    private static class BillingContactRequest {
        @JsonProperty("email_address")
        private String emailAddress;

        @JsonProperty("status_if_new")
        private String statusIfNew = "subscribed";

        @JsonProperty("status")
        private String status = "subscribed";

        @JsonProperty("merge_fields")
        private Map<String, String> mergeFields;

        @JsonProperty("tags")
        private List<String> tags;

        public BillingContactRequest(String emailAddress, String customerName, List<String> tags, Map<String, String> customFields) {
            this.emailAddress = emailAddress;
            this.tags = tags;
            this.mergeFields = Map.of(
                    "FNAME", customerName != null ? customerName.split(" ")[0] : "",
                    "LNAME", customerName != null && customerName.contains(" ") ? 
                            customerName.substring(customerName.indexOf(" ") + 1) : "",
                    "STRIPE_ID", customFields.getOrDefault("STRIPE_ID", ""),
                    "CUST_ID", customFields.getOrDefault("CUST_ID", ""),
                    "SUB_ID", customFields.getOrDefault("SUB_ID", ""),
                    "PLAN", customFields.getOrDefault("PLAN", ""),
                    "APP", "Connect to Mailchimp"
            );
        }

        public String getEmailAddress() { return emailAddress; }
        public String getStatusIfNew() { return statusIfNew; }
        public String getStatus() { return status; }
        public Map<String, String> getMergeFields() { return mergeFields; }
        public List<String> getTags() { return tags; }
    }
}