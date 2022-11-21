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

package com.amazon.aws.partners.saasfactory.saasboost.keycloak;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;

import static com.amazon.aws.partners.saasfactory.saasboost.keycloak.KeycloakTestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public final class KeycloakApiTest {
    private static final String TEST_HOST = "http://subdomain.domain.org";
    private static final String TEST_REALM = "sb-myenv";
    private static final String EXAMPLE_AUTH_HEADER = "Bearer JWTTOKEN";
    private static final Map<String, Object> TEST_EVENT = Map.of(
            "headers", Map.of("Authorization", EXAMPLE_AUTH_HEADER));

    private HttpClient mockClient;
    private ArgumentCaptor<HttpRequest> requestCaptor;
    private KeycloakApi api;

    @Before
    public void setup() {
        mockClient = mock(HttpClient.class);
        requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        api = new KeycloakApi(TEST_HOST, TEST_REALM, mockClient);
    }

    @Test
    public void listUsers_basic() throws IOException, InterruptedException {
        UserRepresentation user1 = mockKeycloakUser("user1");
        UserRepresentation user2 = mockKeycloakUser("user2");
        List<UserRepresentation> expectedUsers = List.of(user1, user2);
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(expectedUsers)))
                .when(mockClient).send(requestCaptor.capture(), any(BodyHandler.class));
        List<UserRepresentation> actualUsers = api.listUsers(TEST_EVENT);
        assertRequest(requestCaptor.getValue(), "GET", endpoint("/users"), null);
        assertUserListsEqual(expectedUsers, actualUsers);
    }

    @Test(expected = RuntimeException.class)
    public void listUsers_wrongStatusCode() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_BAD_GATEWAY, null))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.listUsers(TEST_EVENT);
    }

    @Test(expected = RuntimeException.class)
    public void listUsers_invalidResponse() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(null)))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.listUsers(TEST_EVENT);
    }

    @Test
    public void getUser_basic() throws IOException, InterruptedException {
        final String username = "user";
        UserRepresentation expected = mockKeycloakUser(username);
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(List.of(expected))))
                .when(mockClient).send(requestCaptor.capture(), any(BodyHandler.class));
        UserRepresentation actual = api.getUser(TEST_EVENT, username);
        assertRequest(requestCaptor.getValue(), "GET", 
                endpoint("/users?exact=true&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)), null);
        assertUsersEqual(expected, actual);
    }

    @Test
    public void getUser_UTF8Name() throws IOException, InterruptedException {
        final String username = "李秀";
        UserRepresentation expected = mockKeycloakUser(username);
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(List.of(expected))))
                .when(mockClient).send(requestCaptor.capture(), any(BodyHandler.class));
        UserRepresentation actual = api.getUser(TEST_EVENT, username);
        assertRequest(requestCaptor.getValue(), "GET", 
                endpoint("/users?exact=true&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)), null);
        assertUsersEqual(expected, actual);
    }

    @Test(expected = RuntimeException.class)
    public void getUser_wrongStatusCode() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_BAD_GATEWAY, null))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.getUser(TEST_EVENT, "anyUser");
    }

    @Test(expected = RuntimeException.class)
    public void getUser_invalidResponse() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(null)))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.getUser(TEST_EVENT, "anyUser");
    }

    @Test
    public void createUser_basic() throws IOException, InterruptedException {
        UserRepresentation expected = mockKeycloakUser("user");
        doReturn(mockResponse(HttpURLConnection.HTTP_CREATED, null))
                .when(mockClient).send(requestCaptor.capture(), any(BodyHandler.class));
        // createUser calls getUser, so mock getUser to return expected
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(List.of(expected))))
                .when(mockClient).send(argThat((request) -> "GET".equals(request.method())), any(BodyHandler.class));
        UserRepresentation actual = api.createUser(TEST_EVENT, expected);
        assertRequest(requestCaptor.getValue(), "POST", endpoint("/users"), Utils.toJson(expected));
        assertUsersEqual(expected, actual);
    }

    @Test(expected = RuntimeException.class)
    public void createUser_wrongStatusCode() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_BAD_GATEWAY, null))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.createUser(TEST_EVENT, null);
    }

    @Test
    public void putUser_basic() throws IOException, InterruptedException {
        UserRepresentation expected = mockKeycloakUser("user");
        doReturn(mockResponse(HttpURLConnection.HTTP_NO_CONTENT, null))
                .when(mockClient).send(requestCaptor.capture(), any(BodyHandler.class));
        UserRepresentation actual = api.putUser(TEST_EVENT, expected);
        assertRequest(requestCaptor.getValue(), "PUT", endpoint("/users/" + expected.getId()), Utils.toJson(expected));
        assertUsersEqual(expected, actual);
    }

    @Test(expected = RuntimeException.class)
    public void putUser_wrongStatusCode() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_BAD_GATEWAY, null))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.putUser(TEST_EVENT, mockKeycloakUser("user"));
    }

    @Test
    public void deleteUser_basic() throws IOException, InterruptedException {
        UserRepresentation expected = mockKeycloakUser("user");
        doReturn(mockResponse(HttpURLConnection.HTTP_NO_CONTENT, null))
                .when(mockClient).send(requestCaptor.capture(), any(BodyHandler.class));
        // createUser calls getUser, so mock getUser to return expected
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(List.of(expected))))
                .when(mockClient).send(argThat((request) -> "GET".equals(request.method())), any(BodyHandler.class));
        UserRepresentation actual = api.deleteUser(TEST_EVENT, expected.getUsername());
        assertRequest(requestCaptor.getValue(), "DELETE", endpoint("/users/" + expected.getId()), null);
        assertUsersEqual(expected, actual);
    }

    @Test(expected = RuntimeException.class)
    public void deleteUser_wrongStatusCode() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_BAD_GATEWAY, null))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.deleteUser(TEST_EVENT, "anyUser");
    }

    @Test
    public void getAdminGroupPath_basic() throws IOException, InterruptedException {
        final String adminGroupName = "admin";
        String expectedPath = "/admin";
        Map<String, Object> mockAdminGroup = Map.of("name", adminGroupName, "path", expectedPath);
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(List.of(mockAdminGroup))))
                .when(mockClient).send(requestCaptor.capture(), any(BodyHandler.class));
        String actualPath = api.getAdminGroupPath(TEST_EVENT);
        assertRequest(requestCaptor.getValue(), "GET", endpoint("/groups?search=" + adminGroupName), null);
        assertEquals("Path should match", expectedPath, actualPath);
    }

    @Test(expected = RuntimeException.class)
    public void getAdminGroupPath_wrongStatusCode() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_BAD_GATEWAY, null))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.getAdminGroupPath(TEST_EVENT);
    }

    @Test(expected = RuntimeException.class)
    public void getAdminGroupPath_invalidResponse() throws IOException, InterruptedException {
        doReturn(mockResponse(HttpURLConnection.HTTP_OK, Utils.toJson(null)))
                .when(mockClient).send(any(HttpRequest.class), any(BodyHandler.class));
        api.getAdminGroupPath(TEST_EVENT);
    }

    @Test
    public void toKeycloakUser_basic() {
        UserRepresentation expectedUser = mockKeycloakUser("user");
        assertUsersEqual(expectedUser, KeycloakApi.toKeycloakUser(
                (Map<String, Object>) Utils.fromJson(
                    Utils.toJson(expectedUser), HashMap.class)));
    }

    private void assertRequest(HttpRequest request, String method, String endpoint, String body) {
        HttpHeaders headers = request.headers();
        assertNotNull(headers);
        List<String> authHeaders = headers.allValues("Authorization");
        assertNotNull(authHeaders);
        assertEquals("Request should only have 1 Authorization header", 1, authHeaders.size());
        assertEquals("Request Authorization header should match event", EXAMPLE_AUTH_HEADER, authHeaders.get(0));
        assertEquals("Request URI should match", endpoint, request.uri().toString());
        assertEquals("Request method should match", method, request.method());
        if (body != null) {
            request.bodyPublisher().ifPresentOrElse(
                    publisher -> {
                        assertEquals("Request body content length should match", 
                                (long) body.length(), publisher.contentLength());
                        StringBodySubscriber bodySubscriber = new StringBodySubscriber();
                        publisher.subscribe(bodySubscriber);
                        try {
                            String actualBody = (String) bodySubscriber.getBody().toCompletableFuture().get();
                            assertEquals(body, actualBody);
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    () -> fail("Expected body but found no BodyPublisher in request."));
        }
    }

    

    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        doReturn(statusCode).when(mockResponse).statusCode();
        doReturn(body).when(mockResponse).body();
        return mockResponse;
    }

    private String endpoint(String suffix) {
        return TEST_HOST + "/admin/realms/" + TEST_REALM + suffix;
    }

    /*
     * We need to adapt Flow.Subscriber<List<ByteBuffer>> to Flow.Subscriber<ByteBuffer>
     * to read the body from the captured HttpRequest. Thanks, java.net
     */
    private static class StringBodySubscriber implements Flow.Subscriber<ByteBuffer> {

        final BodySubscriber<String> wrapped;

        public StringBodySubscriber() {
            wrapped = BodySubscribers.ofString(StandardCharsets.UTF_8);
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            wrapped.onSubscribe(subscription);
        }

        @Override
        public void onNext(ByteBuffer item) {
            wrapped.onNext(List.of(item));
        }

        @Override
        public void onError(Throwable throwable) {
            wrapped.onError(throwable);
        }

        @Override
        public void onComplete() {
            wrapped.onComplete();
        }

        CompletionStage<String> getBody() {
            return wrapped.getBody();
        }

    }
}
