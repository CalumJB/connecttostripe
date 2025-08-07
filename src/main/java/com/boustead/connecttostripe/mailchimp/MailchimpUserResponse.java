package com.boustead.connecttostripe.mailchimp;

public class MailchimpUserResponse {
    private boolean exists;

    public MailchimpUserResponse() {}

    public MailchimpUserResponse(boolean exists) {
        this.exists = exists;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

}
