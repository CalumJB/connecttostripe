package com.boustead.connecttostripe.mailchimp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MailchimpUserRepository extends JpaRepository<MailchimpUser, Long> {
    Optional<MailchimpUser> findByStripeAccountId(String stripeAccountId);
    boolean existsByStripeAccountId(String stripeAccountId);
}
