package com.amazon.aws.partners.saasfactory.saasboost;

public class SystemUserDataAccessLayerFactory {

    private SystemUserDataAccessLayerFactory() {
    }

    public static SystemUserDataAccessLayerFactory getInstance() {
        return SystemUserDataAccessLayerFactoryInstance.instance;
    }

    public SystemUserDataAccessLayer getDataAccessLayer(String identityProvider) {
        switch (identityProvider) {
            case "COGNITO":
                return new CognitoUserDataAccessLayer();
            case "KEYCLOAK":
                return new KeycloakUserDataAccessLayer();
            default:
                return null;
        }
    }

    private static class SystemUserDataAccessLayerFactoryInstance {
        public static final SystemUserDataAccessLayerFactory instance = new SystemUserDataAccessLayerFactory();
    }
}
