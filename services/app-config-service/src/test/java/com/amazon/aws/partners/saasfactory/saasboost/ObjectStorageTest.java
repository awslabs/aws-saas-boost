package com.amazon.aws.partners.saasfactory.saasboost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectStorageTest {
    @Test
    public void basic() {
        assertEquals(new ObjectStorage(ObjectStorage.builder()), Utils.fromJson("{}", ObjectStorage.class));
    }
}
