package com.boustead.connecttostripe.stripe.service;

import com.boustead.connecttostripe.stripe.StripeWebhookCounter;
import com.boustead.connecttostripe.stripe.StripeWebhookCounterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    public UsageData getLast12MonthsUsage(String stripeAccountId) {
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusMonths(11); // 12 months including current month
        String startYearMonth = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        List<StripeWebhookCounter> counters = counterRepository.findByStripeAccountIdAndYearMonthRange(stripeAccountId, startYearMonth);
        
        // Convert to map for quick lookup
        Map<String, Integer> counterMap = counters.stream()
                .collect(Collectors.toMap(
                    StripeWebhookCounter::getYearMonth,
                    StripeWebhookCounter::getSessionCount
                ));
        
        // Generate last 12 months with counts (0 if no data)
        List<MonthlyUsage> monthlyUsage = new ArrayList<>();
        int totalUsage = 0;
        
        for (int i = 11; i >= 0; i--) {
            LocalDate monthDate = now.minusMonths(i);
            String yearMonth = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String monthName = monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            int count = counterMap.getOrDefault(yearMonth, 0);
            
            monthlyUsage.add(new MonthlyUsage(yearMonth, monthName, count));
            totalUsage += count;
        }
        
        return new UsageData(monthlyUsage, totalUsage);
    }

    private String getCurrentYearMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public static class UsageData {
        private final List<MonthlyUsage> monthlyBreakdown;
        private final int totalSessions;

        public UsageData(List<MonthlyUsage> monthlyBreakdown, int totalSessions) {
            this.monthlyBreakdown = monthlyBreakdown;
            this.totalSessions = totalSessions;
        }

        public List<MonthlyUsage> getMonthlyBreakdown() {
            return monthlyBreakdown;
        }

        public int getTotalSessions() {
            return totalSessions;
        }
    }

    public static class MonthlyUsage {
        private final String yearMonth;
        private final String monthName;
        private final int sessionCount;

        public MonthlyUsage(String yearMonth, String monthName, int sessionCount) {
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