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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StartCodeBuildTest {

    @Test
    void testReverseBackoff() {
        float initialDelay = 30f;
        float minDelay = 0.25f;
        float factor = 0.2f;

        StartCodeBuild.ReverseBackoff backoff = new StartCodeBuild.ReverseBackoff(initialDelay, minDelay, factor);
        int iter = 0;
        long total = 0;
        long timeout = 60 * 5 * 1000;
        while (total <= timeout) {
            ++iter;
            long delay = Math.round(backoff.delay() * 1000.0f);
            total += delay;
            //System.out.printf("%d %5.2f %5.2f%n", iter, (delay / 1000.0f), (total / 1000.0f));
        }
        assertEquals(722, iter);
    }
}