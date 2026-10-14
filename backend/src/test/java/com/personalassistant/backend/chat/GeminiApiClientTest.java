package com.personalassistant.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiApiClientTest {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void sendsOfficialGenerateContentRequestAndReadsText() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text")
                        .value("Tell me a short joke"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text")
                        .exists())
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"A test reply."}]}}]}
                        """, MediaType.APPLICATION_JSON));

        GeminiApiClient client = new GeminiApiClient(
                restClient, "test-key", "gemini-3.8-flash");

        assertThat(client.generateReply("Tell me a short joke"))
                .isEqualTo("A test reply.");
    }

    @Test
    void reportsProviderErrorsWithoutExposingProviderDetails() {
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo(API_URL))
                    .andRespond(withServerError()
                            .body("provider error response"));
        }

        GeminiApiClient client = new GeminiApiClient(
                restClient, "test-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generateReply("hello"))
                .isInstanceOf(GeminiApiException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageNotContaining("test-key");
    }

    @Test
    void reportsTimeoutsAsProviderFailures() throws Exception {
        server.expect(requestTo(API_URL))
                .andRespond(withException(
                        new SocketTimeoutException("timed out")));

        GeminiApiClient client = new GeminiApiClient(
                restClient, "test-key", "gemini-3.8-flash");

        assertThatThrownBy(() -> client.generateReply("hello"))
                .isInstanceOf(GeminiApiException.class)
                .hasMessage("Gemini request failed.");
    }

    @Test
    void doesNotSendRequestWhenApiKeyIsMissing() {
        GeminiApiClient client = new GeminiApiClient(
                restClient, "  ", "gemini-3.8-flash");

        assertThat(client.isConfigured()).isFalse();

        assertThatThrownBy(() -> client.generateReply("hello"))
                .isInstanceOf(GeminiApiException.class)
                .hasMessage("Gemini API key is not configured.");
    }
}
