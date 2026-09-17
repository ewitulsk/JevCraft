package dev.jevcraft.inference;

import java.net.http.HttpRequest;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public boolean isCancelled() { return cancelled.get(); }
    public void throwIfCancelled() {
        if (isCancelled()) throw new java.util.concurrent.CancellationException("decision cancelled");
    }
    public void onCancel(Runnable listener) {
        if (isCancelled()) listener.run();
        else {
            listeners.add(listener);
            if (isCancelled() && listeners.remove(listener)) listener.run();
        }
    }
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) listeners.forEach(Runnable::run);
    }
}
