package com.boustead.connecttostripe.stripe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StripeUserRepository extends JpaRepository<StripeUser, Long> {

    Optional<StripeUser> findByStripeUserIdAndStripeAccountId(String stripeUserId, String stripeAccountId);

    boolean existsByStripeAccountId(String stripeAccountId);
}
