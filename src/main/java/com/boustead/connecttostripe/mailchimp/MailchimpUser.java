package com.boustead.connecttostripe.mailchimp;

import jakarta.persistence.*;

@Entity
@Table(name = "mailchimp_users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stripe_account_id"})
})
public class MailchimpUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_account_id", nullable = false)
    private String stripeAccountId;

    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "serverPrefix", nullable = false)
    private String serverPrefix;

    // Getters and setters

    public Long getId() {
        return id;
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getServerPrefix() {
        return serverPrefix;
    }

    public void setServerPrefix(String serverPrefix) {
        this.serverPrefix = serverPrefix;
    }
}
