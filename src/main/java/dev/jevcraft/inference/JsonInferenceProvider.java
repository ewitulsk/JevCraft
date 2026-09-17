package dev.jevcraft.inference;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class JsonInferenceProvider implements InferenceProvider {
    public enum Dialect { VERCEL_V4, TYPESAFE_DIRECT }
    private static final Gson JSON = new Gson();
    private final String id;
    private final String model;
    private final String apiKey;
    private final URI endpoint;
    private final Dialect dialect;
    private final HttpClient client;

    public static JsonInferenceProvider vercel(String apiKey) {
        return new JsonInferenceProvider("vercel_gateway", "typesafe-ai/jev", apiKey,
                URI.create("https://ai-gateway.vercel.sh/v4/ai/evaluation-model"), Dialect.VERCEL_V4, HttpClient.newHttpClient());
    }
    public static JsonInferenceProvider typesafe(String apiKey) {
        return new JsonInferenceProvider("typesafe_direct", "jev-latest", apiKey,
                URI.create("https://api.typesafe.ai/v1/systemone"), Dialect.TYPESAFE_DIRECT, HttpClient.newHttpClient());
    }
    public JsonInferenceProvider(String id, String model, String apiKey, URI endpoint, Dialect dialect, HttpClient client) {
        this.id = Objects.requireNonNull(id); this.model = Objects.requireNonNull(model);
        this.apiKey = Objects.requireNonNull(apiKey); this.endpoint = Objects.requireNonNull(endpoint);
        this.dialect = Objects.requireNonNull(dialect); this.client = Objects.requireNonNull(client);
    }
    @Override public String id() { return id; }

    @Override public CompletableFuture<InferenceResponse> evaluate(InferenceRequest request, CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        Instant started = Instant.now();
        Duration remaining = Duration.between(started, request.deadline());
        if (remaining.isNegative() || remaining.isZero()) return CompletableFuture.failedFuture(new java.util.concurrent.TimeoutException("deadline expired"));
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("state", request.state());
            if (dialect == Dialect.TYPESAFE_DIRECT) body.put("model", model);
            body.put("questions", encodeQuestions(request.questions()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(remaining).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (dialect == Dialect.VERCEL_V4) builder
                    .header("ai-gateway-protocol-version", "0.0.1")
                    .header("ai-gateway-auth-method", "api-key")
                    .header("ai-evaluation-model-specification-version", "4")
                    .header("ai-model-id", model);
            HttpRequest httpRequest = builder.POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body))).build();
            CompletableFuture<HttpResponse<String>> pending = client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
            cancellation.onCancel(() -> pending.cancel(true));
            return pending.thenApply(response -> decode(request, response, started));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> encodeQuestions(Map<String, Question> questions) {
        Map<String, Object> result = new LinkedHashMap<>();
        questions.forEach((key, question) -> {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("instructions", question.instructions());
            if (question instanceof Question.Choice c) { q.put("type", "choice"); q.put("criteria", c.criteria()); }
            else if (question instanceof Question.Score s) { q.put("type", "score"); q.put("criteria", s.criteria()); }
            else if (question instanceof Question.BooleanProbability b) {
                q.put("type", dialect == Dialect.VERCEL_V4 ? "boolean" : "noul");
                if (!b.criteria().isEmpty()) q.put("criteria", b.criteria());
            }
            result.put(key, q);
        });
        return result;
    }

    private InferenceResponse decode(InferenceRequest request, HttpResponse<String> response, Instant started) {
        if (response.statusCode() / 100 != 2) {
            Duration retry = response.headers().firstValue("Retry-After").flatMap(this::parseRetryAfter).orElse(null);
            String detail = response.body() == null ? "" : response.body().replaceAll("(?i)bearer\\s+[^\\s\"]+", "Bearer [redacted]");
            if (detail.length() > 1000) detail = detail.substring(0, 1000);
            throw new ProviderException(response.statusCode(), "provider request failed (HTTP " + response.statusCode() + "): " + detail, retry);
        }
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            Map<String, Answer> answers = new LinkedHashMap<>();
            root.getAsJsonObject("answers").entrySet().forEach(entry -> {
                JsonObject node = entry.getValue().getAsJsonObject();
                String type = node.get("type").getAsString();
                Map<String, Double> probabilities = node.has("probabilities")
                        ? JSON.fromJson(node.get("probabilities"), new TypeToken<Map<String, Double>>() {}.getType()) : Map.of();
                Double confidence = node.has("confidence") ? node.get("confidence").getAsDouble() : null;
                switch (type) {
                    case "choice" -> answers.put(entry.getKey(), new Answer.Choice(node.get("choice").getAsString(), probabilities, confidence));
                    case "score" -> answers.put(entry.getKey(), new Answer.Score(node.get("score").getAsDouble(), probabilities, confidence));
                    case "boolean", "noul" -> answers.put(entry.getKey(), new Answer.BooleanProbability(node.get(type.equals("noul") ? "noul" : "probability").getAsDouble()));
                    default -> throw new IllegalArgumentException("unknown answer type: " + type);
                }
            });
            if (!answers.keySet().equals(request.questions().keySet())) throw new IllegalArgumentException("answer IDs do not match question IDs");
            JsonObject usage = root.has("usage") ? root.getAsJsonObject("usage") : new JsonObject();
            String inputName = dialect == Dialect.VERCEL_V4 ? "inputTokens" : "input_tokens";
            String outputName = dialect == Dialect.VERCEL_V4 ? "outputTokens" : "output_tokens";
            long input = usage.has(inputName) ? usage.get(inputName).getAsLong() : 0;
            long output = usage.has(outputName) ? usage.get(outputName).getAsLong() : 0;
            String resolvedModel = root.has("model") ? root.get("model").getAsString() : model;
            Map<String, Object> metadata = root.has("providerMetadata") ? JSON.fromJson(root.get("providerMetadata"), new TypeToken<Map<String, Object>>() {}.getType()) : Map.of();
            List<String> warnings = new ArrayList<>();
            if (root.has("warnings")) for (JsonElement element : root.getAsJsonArray("warnings")) {
                JsonObject warning = element.getAsJsonObject();
                warnings.add(warning.has("message") ? warning.get("message").getAsString() : warning.toString());
            }
            return new InferenceResponse(request.requestId(), id, resolvedModel, answers,
                    new InferenceResponse.Usage(input, output), metadata, warnings, Duration.between(started, Instant.now()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid provider response", e);
        }
    }
    private Optional<Duration> parseRetryAfter(String value) {
        try { return Optional.of(Duration.ofSeconds(Long.parseLong(value))); } catch (NumberFormatException ignored) { return Optional.empty(); }
    }
}
