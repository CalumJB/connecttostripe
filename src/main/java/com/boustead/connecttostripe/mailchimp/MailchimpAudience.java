package com.boustead.connecttostripe.mailchimp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailchimpAudience(
    String id,
    String name
) {
}
