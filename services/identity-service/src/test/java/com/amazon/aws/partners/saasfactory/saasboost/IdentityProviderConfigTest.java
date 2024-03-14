package com.amazon.aws.partners.saasfactory.saasboost;

import org.junit.jupiter.api.Test;

public class IdentityProviderConfigTest {

    @Test
    public void testToJson() {
        IdentityProviderConfig config = new IdentityProviderConfig(IdentityProvider.ProviderType.COGNITO);
        //System.out.println(config.getMetadata().stringPropertyNames());
        System.out.println(Utils.toJson(config));

        String json = "{\"type\": \"COGNITO\", \"metadata\": {\"assumedRole\": \"arn:aws:iam::1234:role/foo\", \"userPoolId\": \"foobar\"}}";
        IdentityProviderConfig fromJson = Utils.fromJson(json, IdentityProviderConfig.class);
        System.out.println(Utils.toJson(fromJson));

        String emptyJson = "{}";
        IdentityProviderConfig fromEmptyJson = Utils.fromJson(emptyJson, IdentityProviderConfig.class);
        System.out.println(Utils.toJson(fromEmptyJson));

    }
}