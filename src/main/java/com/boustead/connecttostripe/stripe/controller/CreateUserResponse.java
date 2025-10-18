package com.boustead.connecttostripe.stripe.controller;

public class CreateUserResponse {
    private boolean success;
    private String message;

    public CreateUserResponse() {}

    public CreateUserResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }


    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
