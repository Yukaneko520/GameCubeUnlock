package com.yuka.gamecube.tools.hooks.backend;

/** Arms only after verified PP-only identity and all earlier unguarded calls have returned. */
final class NativeCallGate {
    @FunctionalInterface interface Call<T> { T run() throws Throwable; }
    private volatile boolean armed;
    private int earlierCalls;
    boolean armed() { return armed; }
    synchronized boolean armIfIdle() {
        if (earlierCalls!=0) return false;
        armed=true; return true;
    }
    <T> T run(Call<T> call) throws Throwable {
        synchronized(this) {
            if(armed) return call.run();
            earlierCalls++;
        }
        try { return call.run(); }
        finally { synchronized(this) { earlierCalls--; } }
    }
}
