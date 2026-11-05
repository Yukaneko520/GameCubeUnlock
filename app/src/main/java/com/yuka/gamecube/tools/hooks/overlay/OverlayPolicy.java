package com.yuka.gamecube.tools.hooks.overlay;

/** Pure policy; the stock capability value is never cached or changed here. */
public final class OverlayPolicy {
    private OverlayPolicy() {}

    public static boolean fps(boolean enabled, int mode, boolean stock) {
        if (!enabled || mode == 0) return stock;
        return mode == 1 || (mode != 2 && stock);
    }

    public static boolean hideCenter(boolean enabled, boolean requested) {
        return enabled && requested;
    }
}
