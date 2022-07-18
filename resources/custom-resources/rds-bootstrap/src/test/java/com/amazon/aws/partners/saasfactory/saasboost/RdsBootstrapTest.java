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

import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class RdsBootstrapTest {

    @Test
    public void testSqlScanner() {
        InputStream bootstrapSQL = Thread.currentThread().getContextClassLoader().getResourceAsStream("bootstrap.sql");
        Scanner sqlScanner = new Scanner(bootstrapSQL, "UTF-8");
        sqlScanner.useDelimiter(Pattern.compile(RdsBootstrap.SQL_STATEMENT_DELIMITER));
        List<String> sql = new ArrayList<>();
        while (sqlScanner.hasNext()) {
            String ddl = sqlScanner.next().trim();
            if (!ddl.isEmpty()) {
                sql.add(ddl);
            }
        }
        assertEquals(6, sql.size());
    }
}