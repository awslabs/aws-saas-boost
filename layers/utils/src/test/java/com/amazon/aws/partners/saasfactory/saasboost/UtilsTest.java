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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UtilsTest {

    @Test
    public void testIsChinaRegion() {
        assertFalse(Utils.isChinaRegion("us-east-1"), "N. Virginia is not in China");
        assertFalse(Utils.isChinaRegion("us-gov-west-1"), "US Gov Cloud is not in China");
        assertTrue(Utils.isChinaRegion("cn-north-1"), "Beijing is in China");
        assertTrue(Utils.isChinaRegion("cn-northwest-1"), "Ningxia is in China");
    }

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
            assertEquals(-1, illegalCharacters.indexOf(character),
                    "Character " + character + " is illegal");
            assertTrue(legalCharacters.contains(character), "Character " + character + " is legal");
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

    @Test
    public void testCollectionFromJson() {
        String json = "[{\"foo\": \"Santa\", \"bar\": \"Claus\"}]";
        MyPojo[] pojoArray = Utils.fromJson(json, MyPojo[].class);
        List<MyPojo> pojoList = Arrays.asList(pojoArray);
    }

    public static class MyPojo {
        private String foo;
        private String bar;

        public MyPojo() {
        }

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }
    }
}
