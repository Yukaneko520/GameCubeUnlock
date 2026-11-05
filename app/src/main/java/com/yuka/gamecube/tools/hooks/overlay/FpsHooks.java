package com.yuka.gamecube.tools.hooks.overlay;

import android.content.Context;
import android.view.View;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import java.util.ArrayList;

final class FpsHooks {
    private final HookRuntime r;
    private final FpsState state;
    private volatile boolean active;

    FpsHooks(HookRuntime runtime) throws Throwable { r = runtime; state = new FpsState(r); }

    void install() throws Throwable {
        // Validate every descriptor before the first registration.
        r.method(FpsState.PRESENTER, "m");
        r.method(FpsState.PANEL, state.api.panelInit(), Context.class);
        r.method(FpsState.PANEL, "onAttachedToWindow");
        r.method(FpsState.PANEL, "onDetachedFromWindow");
        r.method(FpsState.PANEL, "onEdgeStateChanged", r.type("com.vivo.gameassistant.homegui.sideslide.events.EdgeSlideEvent"));
        r.hook(FpsState.COMMON, state.api.capability(), boolean.class, chain -> {
            Object stock = chain.proceed();
            if (!active) return stock;
            ConfigSnapshot config = r.snapshot();
            return OverlayPolicy.fps(config.enabled, config.fpsMode, (Boolean) stock);
        });
        r.hook(state.ownerType, state.api.enterExit(), void.class, chain -> {
            if (active && !(Boolean) chain.getArg(0)) {
                try {
                    if (!state.supported(r.snapshot()) && state.controller() != null) {
                        state.stopController();
                        return null;
                    }
                } catch (Throwable error) { state.failure(error); }
            }
            return chain.proceed();
        }, boolean.class);
        r.hook(FpsState.PRESENTER, "k", void.class, chain -> {
            if (active) {
                state.requested.put(chain.getThisObject(), true);
                try {
                    ConfigSnapshot config = r.snapshot();
                    if (!state.supported(config)) return null;
                    if (config.enabled && config.fpsMode != 0) state.intervened = true;
                    if ((config.enabled && config.fpsMode == 1 || state.intervened) && !state.prepare(chain.getThisObject())) {
                        state.pending();
                        return null;
                    }
                } catch (Throwable error) { state.failure(error); return chain.proceed(); }
            }
            return chain.proceed();
        });
        r.hook(FpsState.PRESENTER, "m", void.class, chain -> {
            if (active) {
                state.requested.put(chain.getThisObject(), false);
                try {
                    if (!state.supported(r.snapshot())) {
                        state.stopPresenter(chain.getThisObject());
                        return null;
                    }
                } catch (Throwable error) { state.failure(error); }
            }
            return chain.proceed();
        });
        r.hook(FpsState.PANEL, state.api.panelInit(), void.class, chain -> {
            Object result = chain.proceed();
            if (active) refresh((View) chain.getThisObject(), false);
            return result;
        }, Context.class);
        r.hook(FpsState.PANEL, "onAttachedToWindow", void.class, chain -> {
            Object result = chain.proceed();
            if (active) refresh((View) chain.getThisObject(), false);
            return result;
        });
        r.hook(FpsState.PANEL, "onDetachedFromWindow", void.class, chain -> {
            try { return chain.proceed(); }
            finally {
                if (active && state.intervened) {
                    try { state.stopPresenter(state.panelPresenter.get(chain.getThisObject())); }
                    catch (Throwable error) { state.failure(error); }
                }
            }
        });
        r.hook(FpsState.PANEL, "onEdgeStateChanged", void.class, chain -> {
            Object result = chain.proceed();
            if (active) refresh((View) chain.getThisObject(), false);
            return result;
        }, r.type("com.vivo.gameassistant.homegui.sideslide.events.EdgeSlideEvent"));
        r.addListener(new ConfigListener() {
            @Override public void beforeChange(ConfigSnapshot old, ConfigSnapshot next) {
                if (!active || mode(old) == mode(next)) return;
                try {
                    if (!state.supported(next)) {
                        for (View panel : new ArrayList<>(state.panels.keySet()))
                            state.stopPresenter(state.panelPresenter.get(panel));
                        state.stopController();
                    }
                } catch (Throwable error) { state.failure(error); }
            }
            @Override public void afterChange(ConfigSnapshot old, ConfigSnapshot next) {
                if (active && mode(old) != mode(next)) state.changed();
            }
        });
        active = true;
    }

    private void refresh(View panel, boolean start) {
        try { state.refresh(panel, start); } catch (Throwable error) { state.failure(error); }
    }

    private static int mode(ConfigSnapshot config) { return config.enabled ? config.fpsMode : 0; }
}
