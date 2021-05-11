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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;

import java.io.*;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

public class Utils {

	private final static Logger LOGGER = LoggerFactory.getLogger(Utils.class);
	private final static String GIT_PROPERTIES_FILENAME = "git.properties";
	private final static DateFormat JAVASCRIPT_ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX'Z'");
	static {
		JAVASCRIPT_ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	private final static ObjectMapper MAPPER = new ObjectMapper();
	static {
		MAPPER.findAndRegisterModules();
		MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		MAPPER.setDateFormat(JAVASCRIPT_ISO8601);
		MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		MAPPER.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
		MAPPER.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
	}

	// We shouldn't be instantiated by callers
	private Utils() {
	}

	public static String escapeJson(String json) {
		return String.valueOf(JsonStringEncoder.getInstance().quoteAsString(json));
	}

	public static String unescapeJson(String quotedJson) {
		StringBuilder json = new StringBuilder();
		int index = 0;
		while (index < quotedJson.length()) {
			char current = quotedJson.charAt(index);
			index++;
			if (current == '\\' && index < quotedJson.length()) {
				char escapedCharacter = quotedJson.charAt(index);
				index++;

				if (escapedCharacter == '"' || escapedCharacter == '\\' || escapedCharacter == '/' || escapedCharacter == '\'') {
					// If the character after the backslash is another slash or a quote
					// then add it to the JSON string we're building. Normal use case is
					// that the next character should be a double quote mark.
					json.append(escapedCharacter);
				} else if (escapedCharacter == 'n') {
					// newline escape sequence
					json.append('\n');
				} else if (escapedCharacter == 'r') {
					// linefeed escape sequence
					json.append('\r');
				} else if (escapedCharacter == 't') {
					// tab escape sequence
					json.append('\t');
				} else if (escapedCharacter == 'u') {
					// unicode escape sequence should be 4 characters long
					if ((index + 4) <= quotedJson.length()) {
						StringBuilder hexadecimal = new StringBuilder();
						for (char hex : quotedJson.substring(current, (current + 4)).toCharArray()) {
							if (Character.isLetterOrDigit(hex)) {
								hexadecimal.append(Character.toLowerCase(hex));
							}
						}
						int codepoint = Integer.parseInt(hexadecimal.toString(), 16);
						json.append((char) codepoint);
						index += 4;
					}
				} // ignorning bell and formfeed
			} else {
				// Non escaped, normal character
				json.append(current);
			}
		}
		return json.toString();
	}

	public static String toJson(Object obj) {
		String json = null;
		try {
			json = MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			LOGGER.error(Utils.getFullStackTrace(e));
		}
		return json;
	}

	public static String toQuotedJson(Object obj) {
		return escapeJson(toJson(obj));
	}

	public static <T> T fromJson(String json, Class<T> serializeTo) {
		T object = null;
		try {
			object = MAPPER.readValue(json, serializeTo);
		} catch (Exception e) {
			LOGGER.error(Utils.getFullStackTrace(e));
		}
		return object;
	}

	public static <T> T fromQuotedJson(String json, Class<T> serializeTo) {
		return fromJson(unescapeJson(json), serializeTo);
	}

	public static <T> T fromJson(InputStream json, Class<T> serializeTo) {
		T object = null;
		try {
			object = MAPPER.readValue(json, serializeTo);
		} catch (Exception e) {
			LOGGER.error(Utils.getFullStackTrace(e));
		}
		return object;
	}

	public static <B extends AwsSyncClientBuilder<B, C> & AwsClientBuilder<?, C>, C> C sdkClient(AwsSyncClientBuilder<B, C> builder, String service) {
		Region signingRegion = Region.of(System.getenv("AWS_REGION"));
		String endpoint = "https://" + service + "." + signingRegion.toString() + ".amazonaws.com";
		// Route53 doesn't follow the rules...
		if ("route53".equals(service)) {
			signingRegion = Region.AWS_GLOBAL;
			endpoint = "https://route53.amazonaws.com";
		}
		C client = builder
				.httpClientBuilder(UrlConnectionHttpClient.builder())
				.credentialsProvider(EnvironmentVariableCredentialsProvider.create())
				.region(signingRegion)
				.endpointOverride(URI.create(endpoint))
				.overrideConfiguration(ClientOverrideConfiguration.builder()
						.retryPolicy(RetryPolicy.builder()
								.backoffStrategy(BackoffStrategy.defaultStrategy())
								.throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy())
								.numRetries(SdkDefaultRetrySetting.defaultMaxAttempts())
								.retryCondition(RetryCondition.defaultRetryCondition())
								.build()
						)
						.build()
				)
				.build();
		return client;
	}

	public static boolean isEmpty(String str) {
		return (str == null || str.isEmpty());
	}

	public static boolean isBlank(String str) {
		return (str == null || str.isBlank());
	}

	public static boolean isNotEmpty(String str) {
		return !isEmpty(str);
	}

	public static boolean isNotBlank(String str) {
		return !isBlank(str);
	}

	public static String getFullStackTrace(Exception e) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw, true);
		e.printStackTrace(pw);
		return sw.getBuffer().toString();
	}

	public static void logRequestEvent(Map<String, Object> event) {
		LOGGER.info(toJson(event));
	}

	public static boolean warmup(Map<String, Object> event) {
		boolean warmup = false;
		if (event.containsKey("queryStringParameters")) {
			Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
			if (queryParams != null && "warmup".equals(queryParams.get("source"))) {
				warmup = true;
			}
		} else {
			if ("warmup".equals(event.get("source"))) {
				warmup = true;
			}
		}
		return warmup;
	}

	public static String version(Class<?> clazz) {
		String version = null;
		try (InputStream propertiesFile = clazz.getClassLoader().getResourceAsStream(GIT_PROPERTIES_FILENAME)) {
			Properties versionProperties = new Properties();
			versionProperties.load(propertiesFile);
			version = versionProperties.getProperty("git.commit.id.describe") + ", Commit time: " + versionProperties.getProperty("git.commit.time");
		} catch (Exception e) {
			LOGGER.error("Error loading version info from {} for {}", GIT_PROPERTIES_FILENAME, clazz.getName());
			LOGGER.error(Utils.getFullStackTrace(e));
		}
		return version;
	}
}
