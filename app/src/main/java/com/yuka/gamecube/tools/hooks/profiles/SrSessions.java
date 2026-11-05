package com.yuka.gamecube.tools.hooks.profiles;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.HashSet;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import android.os.Handler;
import android.os.Looper;

final class SrSessions {
    final HookRuntime r;
    final ProfileStore store;
    final ThreadLocal<ProfilePolicy.Ticket> applying = new ThreadLocal<>();
    final ThreadLocal<ProfilePolicy.Ticket> cleaning = new ThreadLocal<>();
    private final Map<Object, ProfilePolicy.Ticket> tickets = new WeakHashMap<>();
    private final Map<Object, Object> originalStrategies = new WeakHashMap<>();
    private final Set<Object> active = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Object, Boolean> extreme = new WeakHashMap<>();
    private final Set<String> touched = new HashSet<>();
    private WeakReference<Object> current = new WeakReference<>(null);
    private final MainThreadGate mainThread;
    private final SrDialogs dialogs = new SrDialogs();
    SrSessions(HookRuntime runtime, ProfileStore profiles) {
        r = runtime; store = profiles;
        Handler handler = new Handler(Looper.getMainLooper());
        mainThread = new MainThreadGate(() -> Looper.myLooper() == Looper.getMainLooper(), handler::post,
                failure -> r.error("sr_main_session_refresh",failure));
        store.beforeRemoval = profile -> {
            for (Object controller : tickets.keySet().toArray())
                if (tickets.get(controller).profile == profile) cleanup(controller);
        };
    }
    int pid() throws Throwable {
        return (Integer) ProfileAccess.call(ProfileAccess.call(r.type("com.vivo.gameassistant.o"), "V"), "N0");
    }
    boolean valid(ProfilePolicy.Ticket ticket) throws Throwable {
        return ticket != null && ticket.matches(r.currentPackage(), pid(), r.snapshot().revision,
                store.donated(ticket.pkg));
    }
    private boolean ready() { return mainThread.check(this::afterChange); }
    void captureDialog(Object builder, Object dialog) throws Throwable {
        if (!ready() || dialog == null) return;
        for (Object controller : tickets.keySet().toArray())
            if (dialogs.capture(controller, builder, dialog)) return;
    }
    boolean owns(Object controller) { return ready() && tickets.containsKey(controller); }
    boolean wasTouched(String pkg) { return ready() && touched.contains(pkg); }
    boolean active(Object controller) { return ready() && active.contains(controller); }
    void markActive(Object controller, boolean isExtreme) {
        if (ready()) { active.add(controller); extreme.put(controller, isExtreme); }
    }
    void markClosed(Object controller) { if (ready()) active.remove(controller); }
    ProfilePolicy.Ticket prepare(Object controller, String pkg) throws Throwable {
        if (!ready()) return null;
        current = new WeakReference<>(controller);
        store.reconcile();
        Object profile = store.donated(pkg);
        int pid = pid();
        ProfilePolicy.Ticket old = tickets.get(controller);
        if (old != null && (!old.pkg.equals(pkg) || old.pid != pid || old.profile != profile)) cleanup(controller);
        if (profile == null) { tickets.remove(controller); return null; }
        if (!pkg.equals(r.currentPackage()) || pid <= 0) return null;
        if (old != null && old.matches(pkg, pid, r.snapshot().revision, profile)) return old;
        touched.add(pkg);
        Object strategy = ProfileAccess.get(controller, "z");
        if (strategy.getClass() != r.type(SrHooks.STRATEGY)) {
            originalStrategies.putIfAbsent(controller, strategy);
            strategy = r.type(SrHooks.STRATEGY).getDeclaredConstructor().newInstance();
            ProfileAccess.set(controller, "z", strategy);
        }
        ProfileAccess.call(strategy, "p", (Object) null);
        ProfileAccess.call(strategy, "p", profile);
        ProfileAccess.set(controller, "k", pkg);
        ProfilePolicy.Ticket ticket = new ProfilePolicy.Ticket(pkg, pid, r.snapshot().revision, profile);
        tickets.put(controller, ticket);
        ProfileAccess.call(controller, "X0");
        ProfileAccess.call(controller, "Y0");
        r.log("sr: stage=bound,package=" + pkg + ",pid=" + pid + ",revision=" + ticket.revision
                + "," + NativeProfileResolver.describe(profile, false));
        return ticket;
    }
    private void cancel(Object controller) throws Throwable {
        if (!ready()) return;
        for (String field : new String[]{"m", "n", "o", "p"}) ProfileAccess.dispose(controller, field);
        dialogs.cancel(controller);
    }
    void cleanup(Object controller) throws Throwable {
        if (!ready()) return;
        ProfilePolicy.Ticket old = tickets.get(controller);
        if (old == null) return;
        cancel(controller);
        Object strategy = ProfileAccess.get(controller, "z");
        ProfileAccess.call(strategy, "p", old.profile);
        ProfileAccess.set(controller, "k", old.pkg);
        Object list = store.retainForCleanup(old.profile);
        cleaning.set(old);
        try {
            android.content.Context context = (android.content.Context) ProfileAccess.get(controller, "a");
            boolean supported = (Boolean) r.original(r.method(ProfileStore.UTILS, "U0", android.content.Context.class,
                    String.class), null, context, old.pkg);
            // Retained exact profile and old PID let original background cleanup pass its real support check.
            r.original(r.method(SrHooks.CONTROLLER, "T0", String.class, boolean.class), controller, old.pkg, false);
            if (active.contains(controller)) {
                // Support may have disappeared or saved preference changed before background cleanup.
                // The original setter still preserves algorithm and backend selection on CLOSE.
                String closeMethod = Boolean.TRUE.equals(extreme.get(controller)) ? "y1" : "E1";
                r.original(r.method(SrHooks.CONTROLLER, closeMethod, android.content.Context.class,
                        String.class, boolean.class, boolean.class), controller,
                        ProfileAccess.get(controller, "a"), old.pkg, false, false);
                if (active.contains(controller)) throw new IllegalStateException("SR close request rejected; retained for retry");
            }
            if (!supported) {
                ProfileAccess.call(controller, "N1"); ProfileAccess.call(controller, "P1");
                ProfileAccess.set(controller, "b", false); ProfileAccess.set(controller, "l", false);
                android.database.ContentObserver observer = (android.database.ContentObserver) ProfileAccess.get(controller, "w");
                if (observer != null) {
                    context.getContentResolver().unregisterContentObserver(observer);
                    ProfileAccess.set(controller, "w", null);
                }
            }
            active.remove(controller);
        } finally { cleaning.remove(); store.restoreAfterCleanup(list); }
        ProfileAccess.call(strategy, "p", (Object) null);
        Object original = originalStrategies.remove(controller);
        if (original != null) ProfileAccess.set(controller, "z", original);
        tickets.remove(controller);
        extreme.remove(controller);
        r.log("sr: stage=cleanup,package=" + old.pkg + ",pid=" + old.pid + ",revision=" + old.revision);
    }
    void beforeChange(ConfigSnapshot old, ConfigSnapshot next) throws Throwable {
        if (!ready()) return;
        for (Object controller : tickets.keySet().toArray()) {
            ProfilePolicy.Ticket ticket = tickets.get(controller);
            if (ticket == null) continue;
            cancel(controller);
            if (!next.allows(ticket.pkg, "sr") || !old.rule(ticket.pkg).srDonor.equals(next.rule(ticket.pkg).srDonor)
                    || !old.rule(ticket.pkg).tuning.sameSr(next.rule(ticket.pkg).tuning))
                cleanup(controller);
        }
    }
    void afterChange() throws Throwable {
        if (!ready()) return;
        store.reconcile();
        Object controller = current.get();
        if (controller != null) prepare(controller, r.currentPackage());
    }
}
