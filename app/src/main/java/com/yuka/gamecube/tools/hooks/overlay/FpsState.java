package com.yuka.gamecube.tools.hooks.overlay;

import android.view.View;
import android.widget.TextView;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class FpsState {
    static final String COMMON = "com.vivo.common.a";
    static final String OWNER = "com.vivo.gameassistant.o";
    static final String FRAME = "com.vivo.gameassistant.framedisplay.e";
    static final String STRATEGY = "com.vivo.gameassistant.framedisplay.a";
    static final String PRESENTER = "com.vivo.gameassistant.homegui.sideslide.controlcenter.statuspanel.e";
    static final String PANEL = "com.vivo.gameassistant.homegui.sideslide.controlcenter.statuspanel.PhoneStatusPanelLayout";
    final HookRuntime r;
    final com.yuka.gamecube.tools.compat.RomAdapter.Fps api;
    final String ownerType;
    final Field panelPresenter, fpsView, label, presenterController, presenterStrategy, sampler;
    final Method common, capability, owner, controller, enterExit, strategy, bind, stop, toggle;
    final Method dispose, reset, strategyLabel, startPresenter;
    final Map<View, Boolean> panels = Collections.synchronizedMap(new WeakHashMap<>());
    final Map<Object, Boolean> requested = Collections.synchronizedMap(new WeakHashMap<>());
    boolean intervened;
    private long failureRevision = Long.MIN_VALUE;
    private long pendingRevision = Long.MIN_VALUE;

    FpsState(HookRuntime runtime) throws Throwable {
        r = runtime;
        api=r.adapter.fps(); ownerType=r.adapter.owner().type();
        Class<?> frame = r.type(api.controller()), strategyType = r.type(STRATEGY);
        Class<?> panel = r.type(PANEL), presenter = r.type(PRESENTER);
        common = r.method(COMMON, r.adapter.support().commonInstance()); capability = r.method(COMMON, api.capability());
        owner = r.method(ownerType, r.adapter.owner().instance()); controller = r.method(ownerType, api.controllerGetter());
        enterExit = r.method(ownerType, api.enterExit(), boolean.class);
        strategy = r.method(api.controller(), api.strategy()); bind = r.method(api.controller(), api.bind(), String.class);
        stop = r.method(api.controller(), api.stop()); toggle = r.method(api.controller(), api.toggle(), boolean.class);
        reset = r.method(STRATEGY, "l", int.class); strategyLabel = r.method(STRATEGY, "a");
        startPresenter = r.method(PRESENTER, "k");
        panelPresenter = OverlayAccess.field(panel, "z", presenter);
        fpsView = OverlayAccess.field(panel, api.container(), android.view.ViewGroup.class);
        label = OverlayAccess.field(panel, api.label(), TextView.class);
        presenterController = OverlayAccess.field(presenter, "g", frame);
        presenterStrategy = OverlayAccess.field(presenter, "a", strategyType);
        sampler = OverlayAccess.field(presenter, "f", r.type("io.reactivex.disposables.b"));
        dispose = r.method("io.reactivex.disposables.b", "dispose");
    }

    boolean supported(ConfigSnapshot config) throws Throwable {
        Object nativeValue = r.original(capability, OverlayAccess.invoke(common, null));
        return OverlayPolicy.fps(config.enabled, config.fpsMode, (Boolean) nativeValue);
    }

    Object manager() throws Throwable { return OverlayAccess.invoke(owner, null); }
    Object controller() throws Throwable { return OverlayAccess.invoke(controller, manager()); }

    boolean prepare(Object presenter) throws Throwable {
        Object frame = controller();
        if (frame == null && !r.currentPackage().isEmpty()) {
            // The verified owner creates once and retains its observer-owning controller.
            r.original(enterExit, manager(), true);
            frame = controller();
        }
        if (frame == null) return false;
        Object nativeStrategy = OverlayAccess.invoke(strategy, frame);
        if (nativeStrategy == null) return false;
        presenterController.set(presenter, frame);
        presenterStrategy.set(presenter, nativeStrategy);
        return true;
    }

    void stopPresenter(Object presenter) throws Throwable {
        Object subscription = sampler.get(presenter);
        if (subscription != null) {
            OverlayAccess.invoke(dispose, subscription);
            sampler.set(presenter, null);
        }
        Object frame = presenterController.get(presenter);
        if (frame != null) OverlayAccess.invoke(toggle, frame, false);
    }

    void stopController() throws Throwable {
        Object frame = controller();
        if (frame != null) {
            OverlayAccess.invoke(toggle, frame, false);
            OverlayAccess.invoke(stop, frame);
        }
    }

    void refresh(View panel, boolean start) throws Throwable {
        panels.put(panel, true);
        ConfigSnapshot config = r.snapshot();
        if (!intervened && (!config.enabled || config.fpsMode == 0)) return;
        View fps = (View) fpsView.get(panel);
        if (fps == null) return;
        Object presenter = panelPresenter.get(panel);
        boolean show = supported(config);
        if (config.enabled && config.fpsMode != 0) intervened = true;
        if (show && (config.enabled && config.fpsMode == 1 || intervened)) show = prepare(presenter);
        fps.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) { stopPresenter(presenter); return; }
        Object frame = presenterController.get(presenter);
        if (frame != null) {
            Object nativeStrategy = OverlayAccess.invoke(strategy, frame);
            TextView text = (TextView) label.get(panel);
            if (nativeStrategy != null && text != null)
                text.setText((Integer) OverlayAccess.invoke(strategyLabel, nativeStrategy));
        }
        if (start && panel.isAttachedToWindow() && Boolean.TRUE.equals(requested.get(presenter)))
            OverlayAccess.invoke(startPresenter, presenter);
    }

    void changed() {
        try {
            if (supported(r.snapshot())) {
                Object frame = controller();
                if (frame != null) {
                    String pkg = r.currentPackage();
                    if (!pkg.isEmpty()) OverlayAccess.invoke(bind, frame, pkg);
                    Object nativeStrategy = OverlayAccess.invoke(strategy, frame);
                    if (nativeStrategy != null) OverlayAccess.invoke(reset, nativeStrategy, -1);
                }
            }
        } catch (Throwable error) { failure(error); }
        for (View panel : new ArrayList<>(panels.keySet())) {
            try { refresh(panel, true); } catch (Throwable error) { failure(error); }
        }
    }

    void pending() {
        if (pendingRevision == r.snapshot().revision) return;
        pendingRevision = r.snapshot().revision;
        r.log("FPS show pending: no active game/controller");
    }

    void failure(Throwable error) {
        if (failureRevision == r.snapshot().revision) return;
        failureRevision = r.snapshot().revision;
        r.error("fps_lifecycle",error);
    }
}
