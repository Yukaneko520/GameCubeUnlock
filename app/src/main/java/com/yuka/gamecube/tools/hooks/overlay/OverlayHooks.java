package com.yuka.gamecube.tools.hooks.overlay;

import com.yuka.gamecube.tools.hooks.HookRuntime;

public final class OverlayHooks {
    private OverlayHooks() {}

    public static void install(HookRuntime runtime) throws Throwable {
        runtime.installFamily("monster-plus", () -> new MonsterHooks(runtime).install());
        if(com.yuka.gamecube.tools.compat.RomFeatures.available(runtime.adapter.baseline().sdk(),"fps"))
            runtime.installFamily("fps", () -> new FpsHooks(runtime).install());
        runtime.installFamily("embedded-game-center", () -> {
            if(runtime.adapter.hideOnlyGameCenter()) new Os5GameCenterHooks(runtime).install();
            else new GameCenterHooks(runtime).install();
        });
    }
}
