package com.yuka.gamecube.tools.core;

import java.util.Set;

/** Manager-only framework observations, not acknowledgements from individual hook families. */
public record FrameworkState(boolean connected, Set<String> scopes, Set<String> loadedProcesses,
        String scopeError, String targetError) {
    public FrameworkState {
        scopes=Set.copyOf(scopes); loadedProcesses=Set.copyOf(loadedProcesses);
    }
    public static FrameworkState disconnected() { return new FrameworkState(false,Set.of(),Set.of(),"",""); }
}
