package com.boustead.connecttostripe.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    
    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);
    
    List<Subscription> findByStripeAccountId(String stripeAccountId);
    
    @Query("SELECT s FROM Subscription s WHERE s.stripeAccountId = :accountId AND s.status IN ('active', 'trialing')")
    List<Subscription> findActiveSubscriptionsByAccountId(@Param("accountId") String accountId);
    
    @Query("SELECT COUNT(s) > 0 FROM Subscription s WHERE s.stripeAccountId = :accountId AND s.status IN ('active', 'trialing')")
    boolean hasActiveSubscription(@Param("accountId") String accountId);
}