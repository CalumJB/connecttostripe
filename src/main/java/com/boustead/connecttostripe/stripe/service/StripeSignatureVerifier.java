package com.boustead.connecttostripe.stripe.service;

import org.apache.commons.codec.binary.Hex;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class StripeSignatureVerifier {

    public static String getSignature(String signature){
        String[] parts = signature.split(",");
        String timestamp = null;
        String expectedSignature = null;

        for (String part : parts) {
            if (part.startsWith("t=")) {
                timestamp = part.substring(2);
            } else if (part.startsWith("v1=")) {
                expectedSignature = part.substring(3);
            }
        }

        return expectedSignature;
    }
    public static boolean isValid(String signature, String payload, String secret) {
        try {
            // Parse the Stripe-Signature header
            String[] parts = signature.split(",");
            String timestamp = null;
            String expectedSignature = null;

            for (String part : parts) {
                if (part.startsWith("t=")) {
                    timestamp = part.substring(2);
                } else if (part.startsWith("v1=")) {
                    expectedSignature = part.substring(3);
                }
            }

            if (timestamp == null || expectedSignature == null) {
                return false;
            }

            // Construct the signed payload: "timestamp.payload"
            String signedPayload = timestamp + "." + payload;

            // Compute HMAC-SHA256
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(signedPayload.getBytes());
            String computedSignature = Hex.encodeHexString(hash);

            // Compare computed vs received
            return computedSignature.equals(expectedSignature);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
