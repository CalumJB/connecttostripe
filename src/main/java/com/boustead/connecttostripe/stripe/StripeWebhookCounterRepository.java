package com.boustead.connecttostripe.stripe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StripeWebhookCounterRepository extends JpaRepository<StripeWebhookCounter, Long> {
    
    Optional<StripeWebhookCounter> findByStripeAccountIdAndYearMonth(String stripeAccountId, String yearMonth);
    
    @Modifying
    @Query("UPDATE StripeWebhookCounter c SET c.sessionCount = c.sessionCount + 1, c.updatedAt = CURRENT_TIMESTAMP WHERE c.stripeAccountId = :stripeAccountId AND c.yearMonth = :yearMonth")
    int incrementCounter(@Param("stripeAccountId") String stripeAccountId, @Param("yearMonth") String yearMonth);
    
    @Query("SELECT c FROM StripeWebhookCounter c WHERE c.stripeAccountId = :stripeAccountId AND c.yearMonth >= :startYearMonth ORDER BY c.yearMonth DESC")
    List<StripeWebhookCounter> findByStripeAccountIdAndYearMonthRange(@Param("stripeAccountId") String stripeAccountId, @Param("startYearMonth") String startYearMonth);
}