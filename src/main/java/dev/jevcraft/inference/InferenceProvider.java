package dev.jevcraft.inference;

import java.util.concurrent.CompletableFuture;

public interface InferenceProvider extends AutoCloseable {
    String id();
    CompletableFuture<InferenceResponse> evaluate(InferenceRequest request, CancellationToken cancellation);
    @Override default void close() {}
}
