/**
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

import java.util.*;

import static org.junit.Assert.*;

public class CidrDynamoDBTest {

    @Test
    public void testBatchWriteItemBatching() {
        String table = "TABLE_NAME";

        final int batchWriteItemLimit = 25;
        final int maxOctet = 255;
        int octet = -1;
        List<String> batch = new ArrayList<>();
        while (octet <= maxOctet) {
            octet++;
            if (batch.size() == batchWriteItemLimit || octet > maxOctet) {
                Map<String, Collection<String>> putRequests = new HashMap<>();
                putRequests.put(table, batch);
                int count = 0;
                for (String req : batch) {
                    System.out.println(String.format("%02d. %s", ++count, req));
                }
                System.out.println();
                count = 0;
                batch.clear();
            }
            String cidr = String.format("10.%d.0.0", octet);
            String putRequest = "SET cidr_block = " + cidr;
            batch.add(putRequest);
        }
    }
}