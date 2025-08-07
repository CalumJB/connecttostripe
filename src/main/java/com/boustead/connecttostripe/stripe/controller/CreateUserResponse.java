package com.boustead.connecttostripe.stripe.controller;

public class CreateUserResponse {
    private boolean success;
    private String message;
    private String stripeUserId;

    public CreateUserResponse() {}

    public CreateUserResponse(boolean success, String message, String stripeUserId) {
        this.success = success;
        this.message = message;
        this.stripeUserId = stripeUserId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getStripeUserId() {
        return stripeUserId;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setStripeUserId(String stripeUserId) {
        this.stripeUserId = stripeUserId;
    }
}
