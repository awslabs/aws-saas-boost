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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Scanner;

public class Keyboard {

    private static final Logger LOGGER = LoggerFactory.getLogger(Keyboard.class);
    private static Scanner reader = new Scanner(System.in);

    static {
        reader.useDelimiter(System.lineSeparator());
    }

    public static String readString() {
        String value = null;
        try {
            value = reader.next();
        } catch (Exception exception) {
            LOGGER.error("Error reading String data, null value returned.");
        }
        return value;
    }

    public static Boolean readBoolean() {
        String token = reader.next();
        String[] trueValues = new String[]{"true", "y", "yes", "1"};
        String[] falseValues = new String[]{"false", "n", "no", "0"};
        Boolean value = null;
        try {
            if (Arrays.asList(trueValues).contains(token.toLowerCase())) {
                value = true;
            } else if (Arrays.asList(falseValues).contains(token.toLowerCase())) {
                value = false;
            } else {
                LOGGER.error("Unknown boolean value entered, false value returned.");
                value = false;
            }
        } catch (Exception exception) {
            LOGGER.error("Error reading boolean data, null value returned.");
        }
        return value;
    }

    public static Integer readInt() {
        Integer value = null;
        try {
            value = reader.nextInt();
        } catch (Exception exception) {
            LOGGER.error("Error reading int data, null value returned.");
        }
        return value;
    }

    public static Long readLong() {
        Long value = null;
        try {
            value = reader.nextLong();
        } catch (Exception exception) {
            LOGGER.error("Error reading long data, null value returned.");
        }
        return value;
    }

    public static Float readFloat() {
        Float value = null;
        try {
            value = reader.nextFloat();
        } catch (Exception exception) {
            LOGGER.error("Error reading float data, null value returned.");
        }
        return value;
    }

    public static Double readDouble() {
        Double value = null;
        try {
            value = reader.nextDouble();
        } catch (Exception exception) {
            LOGGER.error("Error reading double data, null value returned.");
        }
        return value;
    }
}
