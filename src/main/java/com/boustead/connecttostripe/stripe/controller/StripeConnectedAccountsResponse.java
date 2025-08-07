package com.boustead.connecttostripe.stripe.controller;

import java.util.List;

public record StripeConnectedAccountsResponse(List<ConnectedAccounts> connectedAccounts) {
}
