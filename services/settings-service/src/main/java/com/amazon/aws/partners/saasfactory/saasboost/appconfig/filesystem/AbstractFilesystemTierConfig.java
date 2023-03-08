package com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public abstract class AbstractFilesystemTierConfig {
    private Boolean encrypt;

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private String encryptionKey;

    protected AbstractFilesystemTierConfig(Builder b) {
        this.encrypt = b.encrypt == null ? Boolean.FALSE : b.encrypt;
        this.encryptionKey = b.encryptionKey;
    }

    public Boolean getEncrypt() {
        return this.encrypt;
    }

    public String getEncryptionKey() {
        return this.encryptionKey;
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public abstract static class Builder {
        private Boolean encrypt;
        private String encryptionKey;

        public Builder encrypt(Boolean encrypt) {
            this.encrypt = encrypt;
            return this;
        }

        public Builder encryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
            return this;
        }

        public abstract AbstractFilesystemTierConfig build();
    }
}
