package com.amazon.aws.partners.saasfactory.saasboost;

public abstract class AbstractBillingProviderApi implements BillingProviderApi {

    private BillingProviderCredentials credentials;

    public AbstractBillingProviderApi(BillingProviderCredentials credentials) {
        this.credentials = credentials;
    }

    private AbstractBillingProviderApi() {
    }

    public BillingProviderCredentials getCredentials() {
        return credentials;
    }
}