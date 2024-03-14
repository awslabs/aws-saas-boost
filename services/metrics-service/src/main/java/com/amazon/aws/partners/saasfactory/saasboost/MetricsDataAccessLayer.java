package com.amazon.aws.partners.saasfactory.saasboost;

public class MetricsDataAccessLayer {

    public MetricsProviderConfig getProviderConfig() {
        return new MetricsProviderConfig(MetricsProvider.ProviderType.CLOUDWATCH);
    }
}
