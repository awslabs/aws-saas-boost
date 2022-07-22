package com.amazon.aws.partners.saasfactory.saasboost;

public class Resource {
    String httpMethod;
    String resourcePath;

    public Resource(String httpMethod, String resourcePath) {
        this.httpMethod = httpMethod;
        this.resourcePath = resourcePath;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "httpMethod='" + httpMethod + '\'' +
                ", resourcePath='" + resourcePath + '\'' +
                '}';
    }
}
