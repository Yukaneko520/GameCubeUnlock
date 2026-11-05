package com.yuka.gamecube.tools.hooks.overlay;

import android.content.Context;
import android.view.View;
import android.widget.RadioButton;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class GameCenterState {
    static final String DOCK = "com.vivo.gameassistant.homegui.sideslide.dock.view.EdgeDockView";
    static final String PRESENTER = "ia.c", UNION = "id.v";
    final HookRuntime r;
    final Field button, switches, presenter, selection, remembered, union;
    final Method tools, close, getGameView, expand, getState, unionMode, rotation, vertical, horizontal;
    final Method owner, getUnion, closeUnion, flag, pending, action;
    final Object toolsState;
    final Map<View, ButtonBaseline> views = Collections.synchronizedMap(new WeakHashMap<>());
    final ThreadLocal<Boolean> synthetic = ThreadLocal.withInitial(() -> false);
    private long failureRevision = Long.MIN_VALUE;

    GameCenterState(HookRuntime runtime) throws Throwable {
        r = runtime;
        Class<?> dock = r.type(DOCK), unionType = r.type(UNION);
        Class<?> state = r.type(DOCK + "$DockClickState");
        button = OverlayAccess.field(dock, "d", r.type("com.vivo.gameassistant.view.TipRadioButton"));
        switches = OverlayAccess.field(dock, "b", RadioButton.class);
        presenter = OverlayAccess.field(dock, "t", r.type(PRESENTER));
        selection = OverlayAccess.field(dock, "w", state);
        remembered = OverlayAccess.field(dock, "J", Map.class);
        union = OverlayAccess.field(dock, "G", unionType);
        toolsState = OverlayAccess.enumValue(state, "STATE_SWITCH_VIEW");
        tools = r.method(DOCK, "W0", View.class, int.class, boolean.class);
        close = r.method(PRESENTER, "a", int.class);
        getGameView = r.method(DOCK, "getNewEdgeGameModeView");
        String gameView = "com.vivo.gameassistant.homegui.sideslide.controlcenter.switches.view.NewEdgeGameModeView";
        expand = r.method(gameView, "N", int.class); getState = r.method(gameView, "getState");
        unionMode = r.method(UNION, "i0");
        owner = r.method("com.vivo.gameassistant.o", "V");
        rotation = r.method("com.vivo.gameassistant.o", "S0");
        getUnion = r.method("com.vivo.gameassistant.o", "e1");
        closeUnion = r.method(UNION, "U", String.class, int.class);
        flag = r.method(UNION, "o0", boolean.class);
        pending = r.method(UNION, "W"); action = r.method("com.vivo.gameassistant.o$u", "a");
        vertical = r.method("com.vivo.common.a", "J1", Context.class);
        horizontal = r.method("com.vivo.gameassistant.utils.e0", "t0", int.class);
    }

    boolean hide() { return OverlayPolicy.hideCenter(r.snapshot().enabled, r.snapshot().hideGameCenter); }
    boolean selected(View dock) throws Exception { return ((Enum<?>) selection.get(dock)).name().equals("STATE_GAME_CENTER"); }
    boolean rememberedCenter(View dock) throws Exception {
        Object saved = remembered.get(dock);
        return saved instanceof Map && Integer.valueOf(3).equals(((Map<?, ?>) saved).get(r.currentPackage()));
    }

    void normalize(View dock) throws Exception {
        if (selected(dock)) {
            ButtonBaseline baseline = track(dock);
            baseline.rememberedCenter = true;
            selection.set(dock, toolsState);
        }
        ((RadioButton) button.get(dock)).setChecked(false);
    }

    ButtonBaseline track(View dock) throws Exception {
        ButtonBaseline baseline = views.get(dock);
        if (baseline == null) {
            baseline = new ButtonBaseline((View) button.get(dock));
            views.put(dock, baseline);
        }
        return baseline;
    }

    void visibility(View dock) throws Throwable {
        ButtonBaseline baseline = track(dock);
        View entry = (View) button.get(dock);
        if (hide()) {
            entry.setVisibility(View.GONE); entry.setClickable(false); entry.setEnabled(false);
            entry.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            normalize(dock);
        } else if (baseline.hidden) {
            entry.setClickable(baseline.clickable); entry.setEnabled(baseline.enabled);
            entry.setImportantForAccessibility(baseline.accessibility);
            Object manager = OverlayAccess.invoke(owner, null);
            Object bridge = union.get(dock);
            int angle = (Integer) OverlayAccess.invoke(rotation, manager);
            boolean visible = bridge != null && (Boolean) OverlayAccess.invoke(unionMode, bridge)
                && ((Boolean) OverlayAccess.invoke(horizontal, null, angle)
                    || (Boolean) OverlayAccess.invoke(vertical, OverlayAccess.invoke(r.method("com.vivo.common.a", "p"), null), dock.getContext()));
            entry.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (baseline.rememberedCenter) {
                Object saved = remembered.get(dock);
                if (saved instanceof Map && Integer.valueOf(3).equals(((Map<?, ?>) saved).get(r.currentPackage())))
                    selection.set(dock, OverlayAccess.enumValue(r.type(DOCK + "$DockClickState"), "STATE_GAME_CENTER"));
            }
        }
        baseline.hidden = hide();
    }

    void closeEmbedded(Object bridge, String pkg, int type) throws Throwable {
        if (bridge == null || pkg == null || pkg.isEmpty()) return;
        OverlayAccess.invoke(flag, bridge, false);
        OverlayAccess.invoke(closeUnion, bridge, pkg, type);
    }

    void redirect(View dock, int type, boolean expandView) throws Throwable {
        normalize(dock);
        Object p = presenter.get(dock);
        if (p != null) OverlayAccess.invoke(close, p, type);
        if (!dock.isAttachedToWindow()) return;
        Object gameView = OverlayAccess.invoke(getGameView, dock);
        if (gameView == null) return;
        if (expandView) OverlayAccess.invoke(expand, gameView, 1);
        if ("STATE_COLLAPSED".equals(((Enum<?>) OverlayAccess.invoke(getState, gameView)).name())) return;
        boolean before = synthetic.get(); synthetic.set(true);
        try {
            RadioButton toolsButton = (RadioButton) switches.get(dock);
            toolsButton.setChecked(true);
            OverlayAccess.invoke(tools, dock, toolsButton, type, true);
        } finally { synthetic.set(before); }
    }

    void beforeAttach(View dock) throws Throwable {
        Object bridge = union.get(dock);
        if (bridge != null) {
            Object list = OverlayAccess.invoke(pending, bridge);
            if (list instanceof java.util.List<?>) {
                java.util.Iterator<?> iterator = ((java.util.List<?>) list).iterator();
                while (iterator.hasNext())
                    if (Integer.valueOf(2).equals(OverlayAccess.invoke(action, iterator.next()))) iterator.remove();
            }
        }
        normalize(dock);
    }

    void changed() {
        if (hide()) try {
            closeEmbedded(OverlayAccess.invoke(getUnion, OverlayAccess.invoke(owner, null)), r.currentPackage(), 0);
        } catch (Throwable error) { failure(error); }
        for (View dock : new ArrayList<>(views.keySet())) {
            try {
                boolean wasCenter = selected(dock);
                visibility(dock);
                if (hide() && wasCenter) redirect(dock, 0, false);
            } catch (Throwable error) { failure(error); }
        }
    }

    void failure(Throwable error) {
        if (failureRevision == r.snapshot().revision) return;
        failureRevision = r.snapshot().revision;
        r.error("embedded_game_center",error);
    }

    static final class ButtonBaseline {
        final boolean enabled, clickable;
        final int accessibility;
        boolean hidden, rememberedCenter;
        ButtonBaseline(View view) {
            enabled = view.isEnabled(); clickable = view.isClickable(); accessibility = view.getImportantForAccessibility();
        }
    }
}
