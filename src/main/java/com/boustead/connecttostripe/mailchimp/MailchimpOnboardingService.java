package com.boustead.connecttostripe.mailchimp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailchimpOnboardingService {

    private static final Logger logger = LoggerFactory.getLogger(MailchimpOnboardingService.class);

    @Autowired
    private MailchimpOnboardingCompletedRepository onboardingRepository;

    /**
     * Mark Mailchimp onboarding as completed for a user
     */
    @Transactional
    public void completeOnboarding(String stripeAccountId, String userId) {
        // Check if already completed
        if (onboardingRepository.existsByStripeAccountId(stripeAccountId)) {
            logger.info("Mailchimp onboarding already completed for account: {}", stripeAccountId);
            return;
        }

        // Create completion record
        MailchimpOnboardingCompleted completion = new MailchimpOnboardingCompleted(stripeAccountId, userId);
        onboardingRepository.save(completion);
        
        logger.info("Completed Mailchimp onboarding for account: {}, user: {}", stripeAccountId, userId);
    }

    /**
     * Check if user has completed Mailchimp onboarding
     */
    public boolean isOnboardingCompleted(String stripeAccountId) {
        return onboardingRepository.existsByStripeAccountId(stripeAccountId);
    }

    /**
     * Remove onboarding completion (for testing or reset purposes)
     */
    @Transactional
    public void resetOnboarding(String stripeAccountId) {
        onboardingRepository.deleteByStripeAccountId(stripeAccountId);
        logger.info("Reset Mailchimp onboarding for account: {}", stripeAccountId);
    }
}