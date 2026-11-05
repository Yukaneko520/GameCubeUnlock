package com.yuka.gamecube.tools.hooks.profiles;

import android.content.Context;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;

final class SrHooks {
    static final String CONTROLLER = "com.vivo.gameassistant.superresolution.i";
    static final String STRATEGY = "com.vivo.gameassistant.superresolution.o";
    static void install(HookRuntime r) throws Throwable {
        ProfileAccess group = new ProfileAccess(r);
        ProfileStore store = new ProfileStore(r, false);
        SrSessions sessions = new SrSessions(r, store);
        boolean[] installed = { false };
        try {
            store.preflight();
            for (String field : new String[]{"a", "b", "c", "d", "k", "l", "m", "n", "o", "p", "w", "z"})
                ProfileAccess.field(r.type(CONTROLLER), field);
            r.type(STRATEGY).getDeclaredConstructor();
            r.method("com.vivo.gameassistant.superresolution.a", "p", r.type("y7.e"));
            ProfileAccess.field(r.type("com.vivo.gameassistant.superresolution.a"), "b");
            for (String name : new String[]{"X0", "Y0", "N1", "P1"}) r.method(CONTROLLER, name);
            Class<?> builder = r.type("com.originui.widget.dialog.g"), dialog = r.type("com.originui.widget.dialog.f");
            for (String field : new String[]{"c", "d"})
                if (ProfileAccess.field(r.type(CONTROLLER), field).getType() != builder)
                    throw new NoSuchFieldException("SR dialog builder " + field);
            if (!android.app.Dialog.class.isAssignableFrom(dialog)
                    || r.method(dialog.getName(), "dismiss").getReturnType() != void.class)
                throw new NoSuchMethodException("SR shown dialog dismiss");
            group.hook("com.vivo.gameassistant.utils.l", "o", dialog, chain -> {
                Object shown = chain.proceed();
                try { sessions.captureDialog(chain.getArg(0), shown); }
                catch (Throwable failure) { r.error("sr_dialog_capture",failure); }
                return shown;
            }, builder, int.class, android.content.DialogInterface.OnDismissListener.class);
            group.hook(ProfileStore.SERVICE, "k", void.class, chain -> {
                Object result = chain.proceed(); sessions.afterChange(); return result;
            });
            group.constructor(CONTROLLER, chain -> {
                store.reconcile(); return chain.proceed();
            }, Context.class);
            group.hook(ProfileStore.UTILS, "U0", boolean.class, chain -> {
                if (sessions.cleaning.get() == null) store.reconcile();
                return chain.proceed();
            }, Context.class, String.class);
            group.hook("com.vivo.gameassistant.o", "N0", int.class, chain -> {
                ProfilePolicy.Ticket cleanup = sessions.cleaning.get();
                return cleanup == null ? chain.proceed() : cleanup.pid;
            });
            group.hook(STRATEGY, "f", int.class, chain -> {
                ProfilePolicy.Ticket cleanup = sessions.cleaning.get();
                if (cleanup != null && ProfileAccess.get(chain.getThisObject(), "b") == cleanup.profile)
                    return ProfileAccess.call(cleanup.profile, "f");
                Object profile = store.donated(r.currentPackage());
                if (profile != null && ProfileAccess.get(chain.getThisObject(), "b") == profile)
                    return ProfileAccess.call(profile, "f");
                return chain.proceed();
            });
            group.hook(CONTROLLER, "T0", void.class, chain -> {
                Object controller = chain.getThisObject();
                String pkg = (String) chain.getArg(0);
                if (!(Boolean) chain.getArg(1) && sessions.owns(controller)) {
                    sessions.cleanup(controller); return null;
                }
                ProfilePolicy.Ticket ticket = sessions.prepare(controller, pkg);
                // A module-affected controller must never keep the previous package's profile on no match.
                if (ticket == null && sessions.owns(controller)) return null;
                return chain.proceed();
            }, String.class, boolean.class);
            for (String method : new String[]{"E1", "y1"}) {
                group.hook(CONTROLLER, method, method.equals("E1") ? boolean.class : void.class, chain -> {
                    Object controller = chain.getThisObject();
                    boolean enable = (Boolean) chain.getArg(2);
                    if (sessions.cleaning.get() != null) {
                        if (!sessions.active(controller)) return method.equals("E1") ? false : null;
                        return chain.proceed();
                    }
                    String pkg = (String) chain.getArg(1);
                    ProfilePolicy.Ticket ticket = sessions.prepare(controller, pkg);
                    if (enable && (sessions.wasTouched(pkg) || r.snapshot().allows(pkg, "sr"))
                            && !store.stockSrSupport(pkg)
                            && (ticket == null || !sessions.valid(ticket)))
                        return method.equals("E1") ? false : null;
                    ProfilePolicy.Ticket previous = sessions.applying.get();
                    sessions.applying.set(ticket);
                    try { return chain.proceed(); }
                    finally { if (previous == null) sessions.applying.remove(); else sessions.applying.set(previous); }
                }, Context.class, String.class, boolean.class, boolean.class);
            }
            group.hook(CONTROLLER, "D1", boolean.class, chain -> {
                ProfilePolicy.Ticket ticket = sessions.applying.get();
                if ((Boolean) chain.getArg(2) && ticket != null && !sessions.valid(ticket)) return false;
                Object result = chain.proceed();
                if ((Boolean) chain.getArg(2) && ticket != null && Boolean.TRUE.equals(result))
                    sessions.markActive(chain.getThisObject(), (Boolean) chain.getArg(3));
                if (!(Boolean) chain.getArg(2) && (ticket != null || sessions.cleaning.get() != null)
                        && Boolean.TRUE.equals(result))
                    sessions.markClosed(chain.getThisObject());
                return result;
            }, Context.class, String.class, boolean.class, boolean.class);
            for (String method : new String[]{"x1", "z1"}) {
                group.hook(CONTROLLER, method, void.class, chain -> {
                    Object controller = chain.getThisObject();
                    String bound = (String) ProfileAccess.get(controller, "k");
                    ProfilePolicy.Ticket ticket = null;
                    if ((Boolean) chain.getArg(0) && (sessions.wasTouched(bound) || r.snapshot().allows(bound, "sr"))) {
                        ticket = sessions.prepare(controller, bound);
                        if (!store.stockSrSupport(bound) && !sessions.valid(ticket)) return null;
                    }
                    ProfilePolicy.Ticket previous = sessions.applying.get();
                    if (ticket != null) sessions.applying.set(ticket);
                    try { return chain.proceed(); }
                    finally { if (previous == null) sessions.applying.remove(); else sessions.applying.set(previous); }
                }, boolean.class);
            }
            group.hook("com.vivo.common.utils.l0", "a", void.class, chain -> {
                ProfilePolicy.Ticket ticket = sessions.applying.get();
                if ((Boolean) chain.getArg(1) && ticket != null
                        && (!sessions.valid(ticket) || (Integer) chain.getArg(2) != ticket.pid)) return null;
                return chain.proceed();
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
