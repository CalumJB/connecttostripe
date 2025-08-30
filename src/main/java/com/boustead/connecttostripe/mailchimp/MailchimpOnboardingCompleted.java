package com.boustead.connecttostripe.mailchimp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mailchimp_onboarding_completed")
public class MailchimpOnboardingCompleted {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String stripeAccountId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private LocalDateTime completedAt;

    // Constructors
    public MailchimpOnboardingCompleted() {}

    public MailchimpOnboardingCompleted(String stripeAccountId, String userId) {
        this.stripeAccountId = stripeAccountId;
        this.userId = userId;
        this.completedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}