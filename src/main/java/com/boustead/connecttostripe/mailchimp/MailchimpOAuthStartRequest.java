package com.boustead.connecttostripe.mailchimp;

public record MailchimpOAuthStartRequest(String stripeUserId, String stripeAccountId, String state) {
}
