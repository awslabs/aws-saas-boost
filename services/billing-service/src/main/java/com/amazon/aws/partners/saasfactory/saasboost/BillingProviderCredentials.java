package com.amazon.aws.partners.saasfactory.saasboost;

public interface BillingProviderCredentials {

    enum CredentialType {
        IAM,
        JWT
    }

    CredentialType type();

    Object resolveCredentials();
}
