package com.boustead.connecttostripe.stripe;

import jakarta.persistence.*;

@Entity
@Table(name = "stripe_users")
public class StripeUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true)
    private String stripeAccountId;

    // Add getters/setters

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
    }


}
