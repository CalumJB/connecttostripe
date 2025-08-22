package com.boustead.connecttostripe.exception;

public class MailchimpOAuthException extends RuntimeException {
    
    public MailchimpOAuthException(String message) {
        super(message);
    }
    
    public MailchimpOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}