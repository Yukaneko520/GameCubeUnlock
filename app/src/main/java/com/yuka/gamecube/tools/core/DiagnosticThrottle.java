package com.yuka.gamecube.tools.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Update time only on emission, so frequent sampling cannot silence diagnostics indefinitely. */
public final class DiagnosticThrottle {
    private final int capacity;
    private final Map<String, Long> emitted = new LinkedHashMap<>(16, 0.75f, true);
    public DiagnosticThrottle(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public synchronized boolean allow(String key, long now, long interval) {
        Long previous = emitted.get(key);
        if (previous != null && now >= previous && now - previous < interval) return false;
        emitted.put(key, now);
        if (emitted.size() > capacity) emitted.remove(emitted.keySet().iterator().next());
        return true;
    }
    public synchronized int size() { return emitted.size(); }
}
