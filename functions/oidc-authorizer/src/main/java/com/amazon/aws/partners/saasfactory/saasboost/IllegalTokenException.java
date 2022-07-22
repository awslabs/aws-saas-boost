package com.amazon.aws.partners.saasfactory.saasboost;

public class IllegalTokenException extends Exception {
    public IllegalTokenException(String message) {
        super(message);
    }
    public IllegalTokenException(Exception source) {
        super(source);
    }
}
