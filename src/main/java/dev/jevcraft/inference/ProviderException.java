package dev.jevcraft.inference;

import java.time.Duration;

public final class ProviderException extends RuntimeException {
    private final int status;
    private final Duration retryAfter;
    public ProviderException(int status, String message, Duration retryAfter) {
        super(message); this.status = status; this.retryAfter = retryAfter;
    }
    public int status() { return status; }
    public Duration retryAfter() { return retryAfter; }
    public boolean retryable() { return status == 429 || status == 529 || status >= 500; }
}
