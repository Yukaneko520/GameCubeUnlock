package com.yuka.gamecube.tools.hooks.profiles;

import android.content.Context;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;

final class FrameHooks {
    static final String CONTROLLER = "com.vivo.gameassistant.frameinterpolation.h";
    static final String STATE = "com.vivo.gameassistant.frameinterpolation.FrameInterpolationState";
    static final String REASON = "com.vivo.gameassistant.frameinterpolation.ChangeFrameReason";
    static void install(HookRuntime r) throws Throwable {
        ProfileAccess group = new ProfileAccess(r);
        ProfileStore store = new ProfileStore(r, true);
        FrameSessions sessions = new FrameSessions(r, store);
        boolean[] installed = { false };
        try {
            store.preflight();
            Class<?> state = r.type(STATE), reason = r.type(REASON);
            for (String field : new String[]{"a", "d", "e", "h", "i", "j", "p", "q", "s"})
                ProfileAccess.field(r.type(CONTROLLER), field);
            ProfileAccess.field(r.type("c9.h"), "b");
            for (String name : new String[]{"i1", "t0", "z0", "x0", "h0"}) r.method(CONTROLLER, name);
            r.method(CONTROLLER, "N0", state, reason);
            r.method(CONTROLLER, "J0", state, reason, Integer.class);
            r.method("com.vivo.common.utils.l", "A0", String.class);
            group.hook(ProfileStore.SERVICE, "j", void.class, chain -> {
                Object result = chain.proceed(); sessions.afterChange(); return result;
            });
            group.hook("c9.j", "s", r.type(ProfileCopies.FRAME), chain -> {
                store.reconcile(); return chain.proceed();
            }, String.class);
            for (String name : new String[]{"R0", "i1"}) {
                group.hook(CONTROLLER, name, name.equals("i1") ? boolean.class : void.class, chain -> {
                    sessions.prepare(chain.getThisObject()); return chain.proceed();
                });
            }
            group.hook(CONTROLLER, "o0", int.class, chain -> {
                store.reconcile(); return chain.proceed();
            }, state, String.class);
            group.hook(CONTROLLER, "q0", int[].class, chain -> {
                store.reconcile(); return chain.proceed();
            }, state, String.class);
            group.hook(CONTROLLER, "h0", void.class, chain -> {
                if (sessions.owns(chain.getThisObject())) sessions.cancel(chain.getThisObject());
                return chain.proceed();
            });
            group.hook(CONTROLLER, "W0", void.class, chain -> {
                Object controller = chain.getThisObject();
                ProfilePolicy.Ticket ticket = sessions.prepare(controller);
                if (ticket == null) return sessions.revoked() ? null : chain.proceed();
                sessions.schedule(controller, (Integer) chain.getArg(0), chain.getArg(1), chain.getArg(2), ticket);
                return null;
            }, int.class, state, reason);
            group.hook(CONTROLLER, "i0", boolean.class, chain -> {
                ProfilePolicy.Ticket ticket = sessions.prepare(chain.getThisObject());
                boolean close = "CLOSE".equals(String.valueOf(chain.getArg(0)));
                if (ticket != null && !close && !sessions.valid(ticket)) return false;
                ProfilePolicy.Ticket previous = sessions.applying.get();
                sessions.applying.set(ticket);
                try { return chain.proceed(); }
                finally { if (previous == null) sessions.applying.remove(); else sessions.applying.set(previous); }
            }, state, String.class, reason);
            group.hook("c9.h", "l", void.class, chain -> {
                ProfilePolicy.Ticket ticket = sessions.applying.get();
                if (ticket == null) return chain.proceed();
                if (!sessions.valid(ticket)) return null;
                boolean revised = (Boolean) r.original(r.method(ProfileStore.UTILS, "V0", String.class), null, ticket.pkg);
                if (revised) {
                    int realPid = (Integer) ProfileAccess.call(r.type("com.vivo.common.utils.l"), "A0", ticket.pkg);
                    if (realPid != ticket.pid) { r.log("frame: PID changed; waiting for original game rebind"); return null; }
                }
                return chain.proceed();
            }, int.class, int.class, int.class, int.class, state);
            group.hook(ProfileStore.UTILS, "V0", boolean.class, chain -> {
                // Only the PID-resolution scheduler is suppressed; original triplet/SDK/Monster code still runs.
                if (sessions.applying.get() != null) return false;
                return chain.proceed();
            }, String.class);
            group.hook("com.vivo.common.utils.l0", "b", void.class, chain -> {
                ProfilePolicy.Ticket ticket = sessions.applying.get();
                if ((Boolean) chain.getArg(1) && ticket != null
                        && (!sessions.valid(ticket) || (Integer) chain.getArg(3) != ticket.pid)) return null;
                Object result = chain.proceed();
                if ((Boolean) chain.getArg(1)) sessions.emitted();
                else sessions.closed();
                return result;
            }, Context.class, boolean.class, int.class, int.class, int.class, int.class);
            group.hook(CONTROLLER, "b", void.class, chain -> {
                sessions.cleanup(chain.getThisObject()); return chain.proceed();
            });
            r.addListener(new ConfigListener() {
                @Override public void beforeChange(ConfigSnapshot old, ConfigSnapshot next) throws Throwable {
                    if (installed[0]) sessions.beforeChange(old, next);
                }
                @Override public void afterChange(ConfigSnapshot old, ConfigSnapshot next) throws Throwable {
                    if (installed[0]) sessions.afterChange();
                }
            });
            installed[0] = true;
        } catch (Throwable failure) {
            installed[0] = false; group.close(); store.restore(); throw failure;
        }
    }
}
