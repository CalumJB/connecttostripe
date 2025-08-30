package com.boustead.connecttostripe.mailchimp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MailchimpOnboardingCompletedRepository extends JpaRepository<MailchimpOnboardingCompleted, Long> {

    Optional<MailchimpOnboardingCompleted> findByStripeAccountId(String stripeAccountId);

    boolean existsByStripeAccountId(String stripeAccountId);

    void deleteByStripeAccountId(String stripeAccountId);
}