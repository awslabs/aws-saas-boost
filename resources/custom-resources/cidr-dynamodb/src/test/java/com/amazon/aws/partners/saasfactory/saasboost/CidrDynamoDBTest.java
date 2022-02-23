/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.*;

import static org.junit.Assert.*;

public class CidrDynamoDBTest {

    @Test
    public void testGenerateBatches() {
        List<List<WriteRequest>> batches = CidrDynamoDB.generateBatches();
        // Max batch write size for DynamoDB is 25 and we're batching up 256 items
        // We should have 11 batches total
        assertEquals(11, batches.size());
        // The first 10 batches should be filled to the limit
        for (int i = 0; i < 10; i++) {
            assertEquals(25, batches.get(i).size());
        }
        // and one remainder batch of 6
        assertEquals(6, batches.get(10).size());
    }
}