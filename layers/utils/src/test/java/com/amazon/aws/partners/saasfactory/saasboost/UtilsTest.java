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

import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void testRandomString() {
        assertThrows(IllegalArgumentException.class, () -> {Utils.randomString(0);});
        assertThrows(IllegalArgumentException.class, () -> {Utils.randomString(-1);});
        assertEquals(12, Utils.randomString(12).length());

        String illegalCharacters = "!#$%&*+-.:=?^_, ";
        String legalCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        String randomString = Utils.randomString(1000);
        for (int ch = 0; ch < randomString.length(); ch++) {
            String character = String.valueOf(randomString.charAt(ch));
            assertEquals("Character " + character + " is illegal", -1, illegalCharacters.indexOf(character));
            assertTrue("Character " + character + " is legal", legalCharacters.contains(character));
        }
    }

    @Test
    public void testToSnakeCase() {
        assertNull(Utils.toSnakeCase(null));
        assertEquals("", Utils.toSnakeCase(""));
        assertEquals("  ", Utils.toSnakeCase("  "));
        assertEquals("a", Utils.toSnakeCase("a"));
        assertEquals("a", Utils.toSnakeCase("A"));
        assertEquals("?", Utils.toSnakeCase("?"));
        assertEquals("snake_case", Utils.toSnakeCase("snake_case"));
        assertEquals("camel_case", Utils.toSnakeCase("camelCase"));
        assertEquals("pascal_case", Utils.toSnakeCase("PascalCase"));
        assertEquals("snake_case", Utils.toSnakeCase("Snake Case"));
        assertEquals("foo_bar", Utils.toSnakeCase("foo baR"));
        assertEquals("foo_bar_baz", Utils.toSnakeCase("fooBarBaz"));
        assertEquals("foo_bar_baz", Utils.toSnakeCase("fooBarBAZ"));
    }
}
