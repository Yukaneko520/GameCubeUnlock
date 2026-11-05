package com.yuka.gamecube.tools.hooks.profiles;

import com.yuka.gamecube.tools.hooks.HookRuntime;

/** Called only after the entrypoint has verified the assistantui process and exact APK/device. */
public final class ProfileHooks {
    private ProfileHooks() {}
    public static void install(HookRuntime runtime) throws Throwable {
        runtime.installFamily("frame profiles and lifecycle", () -> {
            if(runtime.adapter.baseline().sdk()<=35) Os5FrameHooks.install(runtime); else FrameHooks.install(runtime);
        });
        if(com.yuka.gamecube.tools.compat.RomFeatures.available(runtime.adapter.baseline().sdk(),"sr"))
          runtime.installFamily("SR profiles and lifecycle", () -> {
            if(runtime.adapter.baseline().sdk()==35) Os5SrHooks.install(runtime); else SrHooks.install(runtime);
        });
        runtime.installFamily("custom frame parameters", () -> FrameTuning.install(runtime));
    }
}
