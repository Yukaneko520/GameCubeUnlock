package com.yuka.gamecube.tools.hooks.profiles;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Defers one refresh without capturing a package, revision, or controller snapshot. */
final class MainThreadGate {
    @FunctionalInterface interface Task { void run() throws Throwable; }
    private final BooleanSupplier isMain;
    private final Consumer<Runnable> post;
    private final Consumer<Throwable> failure;
    private final AtomicBoolean queued = new AtomicBoolean();
    MainThreadGate(BooleanSupplier isMain, Consumer<Runnable> post, Consumer<Throwable> failure) {
        this.isMain = isMain; this.post = post; this.failure = failure;
    }
    boolean check(Task refresh) {
        if (isMain.getAsBoolean()) return true;
        if (queued.compareAndSet(false, true)) {
            try {
                post.accept(() -> {
                    queued.set(false);
                    try { refresh.run(); } catch (Throwable error) { failure.accept(error); }
                });
            } catch (Throwable error) { queued.set(false); failure.accept(error); }
        }
        return false;
    }
}
