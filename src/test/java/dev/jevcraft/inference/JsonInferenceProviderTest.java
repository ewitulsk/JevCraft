package dev.jevcraft.inference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JsonInferenceProviderTest {
    private HttpServer server;
    private URI endpoint;
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/evaluate", exchange -> {
            headers.set(exchange.getRequestHeaders());
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"model\":\"resolved-jev\",\"answers\":{" +
                    "\"route\":{\"type\":\"choice\",\"choice\":\"mine\",\"probabilities\":{\"mine\":0.8,\"wait\":0.2},\"confidence\":0.6}," +
                    "\"risk\":{\"type\":\"score\",\"score\":0.25,\"probabilities\":{\"0\":0.75,\"1\":0.25},\"confidence\":0.5}," +
                    "\"safe\":{\"type\":\"noul\",\"noul\":0.91}},\"usage\":{\"input_tokens\":17,\"output_tokens\":3}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start(); endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/evaluate");
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void directDialectMapsMixedQuestionsAndPreservesDistribution() throws Exception {
        var provider = new JsonInferenceProvider("typesafe_direct", "jev-latest", "secret", endpoint,
                JsonInferenceProvider.Dialect.TYPESAFE_DIRECT, HttpClient.newHttpClient());
        InferenceResponse result = provider.evaluate(request(), new CancellationToken()).get(2, TimeUnit.SECONDS);
        assertEquals("resolved-jev", result.model());
        assertEquals(17, result.usage().inputTokens());
        assertEquals(0.8, ((Answer.Choice) result.answers().get("route")).probabilities().get("mine"));
        assertEquals(0.91, ((Answer.BooleanProbability) result.answers().get("safe")).probability());
        assertTrue(request.get().contains("\"model\":\"jev-latest\""));
        assertTrue(request.get().contains("\"type\":\"noul\""));
        assertEquals("Bearer secret", headers.get().getFirst("Authorization"));
    }

    @Test void gatewayDialectUsesV4HeadersAndBooleanName() throws Exception {
        server.removeContext("/evaluate");
        server.createContext("/evaluate", exchange -> {
            headers.set(exchange.getRequestHeaders()); request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"answers\":{\"route\":{\"type\":\"choice\",\"choice\":\"mine\"},\"risk\":{\"type\":\"score\",\"score\":0.0},\"safe\":{\"type\":\"boolean\",\"probability\":0.7}},\"usage\":{\"inputTokens\":9}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        var provider = new JsonInferenceProvider("vercel_gateway", "typesafe-ai/jev", "secret", endpoint,
                JsonInferenceProvider.Dialect.VERCEL_V4, HttpClient.newHttpClient());
        InferenceResponse result = provider.evaluate(request(), new CancellationToken()).get(2, TimeUnit.SECONDS);
        assertEquals(9, result.usage().inputTokens());
        assertFalse(request.get().contains("\"model\":"));
        assertTrue(request.get().contains("\"type\":\"boolean\""));
        assertEquals("4", headers.get().getFirst("ai-evaluation-model-specification-version"));
        assertEquals("0.0.1", headers.get().getFirst("ai-gateway-protocol-version"));
        assertEquals("typesafe-ai/jev", headers.get().getFirst("ai-model-id"));
    }

    private InferenceRequest request() {
        return new InferenceRequest(UUID.randomUUID(), UUID.randomUUID(), 1, 2, 3, Map.of("health", 20), Map.of(
                "route", new Question.Choice("Choose a feasible action", Map.of("mine", "Mine visible block", "wait", "Do nothing")),
                "risk", new Question.Score("Rate risk", List.of("safe", "dangerous")),
                "safe", new Question.BooleanProbability("Is this safe?", Map.of())), Instant.now().plusSeconds(5));
    }
}
