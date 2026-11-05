package com.yuka.gamecube.tools.hooks.overlay;

import android.view.View;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.util.HashMap;
import java.util.Map;

final class GameCenterHooks {
    private final HookRuntime r;
    private final GameCenterState state;
    private volatile boolean active;

    GameCenterHooks(HookRuntime runtime) throws Throwable { r = runtime; state = new GameCenterState(r); }

    void install() throws Throwable {
        String dock = GameCenterState.DOCK;
        r.method(dock, "F0"); r.method(dock, "onAttachedToWindow");
        r.method(dock, "onUnionStateEvent", r.type("ja.d"));
        r.method(dock, "c", int.class, boolean.class); r.method(dock, "b", int.class);
        r.method(dock, "f", int.class); r.method(dock, "setChooseItem", int.class);
        r.method(GameCenterState.PRESENTER, "c", int.class);
        r.method(GameCenterState.PRESENTER, "f", int.class, int.class);
        r.method(GameCenterState.UNION, "m0", String.class, int.class);
        r.method("com.vivo.common.a", "p");
        r.hook(dock, "F0", void.class, chain -> {
            Object result = chain.proceed();
            if (active) visibility((View) chain.getThisObject());
            return result;
        });
        r.hook(dock, "onAttachedToWindow", void.class, chain -> {
            if (!active || !state.hide()) return chain.proceed();
            View view = (View) chain.getThisObject();
            Object baseline = null;
            boolean swapped = false;
            try {
                state.beforeAttach(view);
                baseline = state.remembered.get(view);
                if (baseline instanceof Map<?, ?>) {
                    Map<Object, Object> local = new HashMap<>((Map<?, ?>) baseline);
                    if (Integer.valueOf(3).equals(local.get(r.currentPackage()))) {
                        state.track(view).rememberedCenter = true;
                        local.put(r.currentPackage(), 2);
                    }
                    state.remembered.set(view, local); swapped = true;
                }
            } catch (Throwable error) { state.failure(error); }
            Object result;
            boolean before = state.synthetic.get(); state.synthetic.set(true);
            try { result = chain.proceed(); }
            finally {
                state.synthetic.set(before);
                if (swapped) try { state.remembered.set(view, baseline); }
                catch (Throwable error) { state.failure(error); }
            }
            try {
                boolean wasCenter = state.selected(view);
                state.visibility(view);
                if (wasCenter) state.redirect(view, 0, false);
            } catch (Throwable error) { state.failure(error); }
            return result;
        });
        r.hook(dock, "onUnionStateEvent", void.class, chain -> {
            boolean before = state.synthetic.get();
            if (active && state.hide()) state.synthetic.set(true);
            Object result;
            try { result = chain.proceed(); } finally { state.synthetic.set(before); }
            if (active) visibility((View) chain.getThisObject());
            return result;
        }, r.type("ja.d"));
        r.hook(dock, "c", void.class, chain -> {
            if (active && state.hide()) {
                try { state.redirect((View) chain.getThisObject(), (Integer) chain.getArg(0), false); return null; }
                catch (Throwable error) { state.failure(error); }
            }
            return chain.proceed();
        }, int.class, boolean.class);
        r.hook(dock, "b", void.class, chain -> {
            if (active && state.hide()) {
                try { state.redirect((View) chain.getThisObject(), (Integer) chain.getArg(0), true); return null; }
                catch (Throwable error) { state.failure(error); }
            }
            return chain.proceed();
        }, int.class);
        r.hook(dock, "f", void.class, chain -> {
            if (!active || !state.hide()) return chain.proceed();
            View view = (View) chain.getThisObject();
            boolean redirected;
            try {
                redirected = (Integer) chain.getArg(0) == 2 || state.selected(view) || state.rememberedCenter(view);
                if (redirected) state.normalize(view);
            } catch (Throwable error) { state.failure(error); return chain.proceed(); }
            boolean before = state.synthetic.get(); state.synthetic.set(true);
            try {
                Object result = redirected ? chain.proceed(new Object[] {0}) : chain.proceed();
                if (redirected) try { state.redirect(view, 0, false); }
                catch (Throwable error) { state.failure(error); }
                return result;
            } finally { state.synthetic.set(before); }
        }, int.class);
        r.hook(dock, "setChooseItem", void.class, chain -> {
            if (active && state.synthetic.get()) return null;
            return chain.proceed();
        }, int.class);
        r.hook(GameCenterState.PRESENTER, "c", void.class, chain -> {
            if (active && state.hide()) {
                try {
                    OverlayAccess.invoke(r.method(GameCenterState.PRESENTER, "f", int.class, int.class),
                        chain.getThisObject(), 0, chain.getArg(0));
                    return null;
                } catch (Throwable error) { state.failure(error); }
            }
            return chain.proceed();
        }, int.class);
        r.hook(GameCenterState.UNION, "m0", void.class, chain -> {
            if (active && state.hide()) {
                try {
                    state.closeEmbedded(chain.getThisObject(), (String) chain.getArg(0), (Integer) chain.getArg(1));
                    for (View view : new java.util.ArrayList<>(state.views.keySet()))
                        if (view.isAttachedToWindow()) state.redirect(view, (Integer) chain.getArg(1), false);
                    return null;
                } catch (Throwable error) { state.failure(error); }
            }
            return chain.proceed();
        }, String.class, int.class);
        r.addListener(new ConfigListener() {
            @Override public void afterChange(ConfigSnapshot old, ConfigSnapshot next) {
                if (active && (old.enabled != next.enabled || old.hideGameCenter != next.hideGameCenter)) state.changed();
            }
        });
        active = true;
    }

    private void visibility(View view) {
        try { state.visibility(view); } catch (Throwable error) { state.failure(error); }
    }
}
