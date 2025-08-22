package com.boustead.connecttostripe.stripe.service;

import com.boustead.connecttostripe.stripe.StripeWebhookCounter;
import com.boustead.connecttostripe.stripe.StripeWebhookCounterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class StripeWebhookCounterService {

    @Autowired
    private StripeWebhookCounterRepository counterRepository;

    @Transactional
    public void incrementCheckoutSessionCount(String stripeAccountId) {
        String currentYearMonth = getCurrentYearMonth();
        
        int updatedRows = counterRepository.incrementCounter(stripeAccountId, currentYearMonth);
        
        if (updatedRows == 0) {
            StripeWebhookCounter counter = new StripeWebhookCounter();
            counter.setStripeAccountId(stripeAccountId);
            counter.setYearMonth(currentYearMonth);
            counter.setSessionCount(1);
            counterRepository.save(counter);
        }
    }

    public Integer getMonthlyCheckoutSessionCount(String stripeAccountId, String yearMonth) {
        Optional<StripeWebhookCounter> counter = counterRepository.findByStripeAccountIdAndYearMonth(stripeAccountId, yearMonth);
        return counter.map(StripeWebhookCounter::getSessionCount).orElse(0);
    }

    public Integer getCurrentMonthCheckoutSessionCount(String stripeAccountId) {
        return getMonthlyCheckoutSessionCount(stripeAccountId, getCurrentYearMonth());
    }

    private String getCurrentYearMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}