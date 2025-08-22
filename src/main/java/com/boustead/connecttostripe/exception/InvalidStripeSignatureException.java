package com.boustead.connecttostripe.exception;

public class InvalidStripeSignatureException extends RuntimeException {
    
    public InvalidStripeSignatureException(String message) {
        super(message);
    }
    
    public InvalidStripeSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}