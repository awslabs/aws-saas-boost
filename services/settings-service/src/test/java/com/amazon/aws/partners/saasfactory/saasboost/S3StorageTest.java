package com.amazon.aws.partners.saasfactory.saasboost;

import com.amazon.aws.partners.saasfactory.saasboost.appconfig.S3Storage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class S3StorageTest {
    @Test
    public void basic() {
        assertEquals(new S3Storage(S3Storage.builder()), Utils.fromJson("{}", S3Storage.class));
    }
}
