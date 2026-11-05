package com.yuka.gamecube.tools.hooks.profiles;

import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.HashSet;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;

final class FrameSessions {
    final HookRuntime r;
    final ProfileStore store;
    final ThreadLocal<ProfilePolicy.Ticket> applying = new ThreadLocal<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Object, ProfilePolicy.Ticket> tickets = new WeakHashMap<>();
    private final Map<Object, Runnable> pending = new WeakHashMap<>();
    private final Set<Object> active = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<String> touched = new HashSet<>();
    private WeakReference<Object> current = new WeakReference<>(null);
    private final MainThreadGate mainThread;
    FrameSessions(HookRuntime runtime, ProfileStore profiles) {
        r = runtime; store = profiles;
        mainThread = new MainThreadGate(() -> Looper.myLooper() == Looper.getMainLooper(), main::post,
                failure -> r.error("frame_main_session_refresh",failure));
        store.beforeRemoval = profile -> {
            for (Object controller : tickets.keySet().toArray())
                if (tickets.get(controller).profile == profile) cleanup(controller);
        };
    }
    private boolean ready() { return mainThread.check(this::afterChange); }
    boolean owns(Object controller) { return ready() && tickets.containsKey(controller); }
    boolean revoked() throws Throwable {
        if (!ready()) return false;
        String pkg = r.currentPackage();
        return touched.contains(pkg) && store.donated(pkg) == null
                && !(Boolean) r.original(r.method(ProfileStore.UTILS, "n0", String.class), null, pkg);
    }
    int pid() throws Throwable {
        return (Integer) ProfileAccess.call(ProfileAccess.call(r.type("com.vivo.gameassistant.o"), "V"), "N0");
    }
    boolean valid(ProfilePolicy.Ticket ticket) throws Throwable {
        return ticket != null && ticket.matches(r.currentPackage(), pid(), r.snapshot().revision,
                store.donated(ticket.pkg));
    }
    ProfilePolicy.Ticket prepare(Object controller) throws Throwable {
        if (!ready()) return null;
        current = new WeakReference<>(controller);
        store.reconcile();
        String pkg = r.currentPackage();
        int pid = pid();
        Object profile = store.donated(pkg);
        ProfilePolicy.Ticket old = tickets.get(controller);
        if (old != null && (!old.pkg.equals(pkg) || old.pid != pid || old.profile != profile)) cleanup(controller);
        if (profile == null) { tickets.remove(controller); return null; }
        touched.add(pkg);
        if (old == null || !old.matches(pkg, pid, r.snapshot().revision, profile)) {
            cancel(controller);
            ProfilePolicy.Ticket ticket = new ProfilePolicy.Ticket(pkg, pid, r.snapshot().revision, profile);
            tickets.put(controller, ticket);
            // Refresh data and thermal thresholds only. R0/e1 would also recover/activate an effect.
            r.original(r.method(FrameHooks.CONTROLLER, "i1"), controller);
            ProfileAccess.call(controller, "z0");
            ProfileAccess.call(controller, "x0");
            r.log("frame: stage=bound,package=" + pkg + ",pid=" + pid + ",revision=" + ticket.revision
                    + "," + NativeProfileResolver.describe(profile, true));
            return ticket;
        }
        return old;
    }
    void cancel(Object controller) throws Throwable {
        if (!ready()) return;
        Runnable task = pending.remove(controller);
        if (task != null) main.removeCallbacks(task);
        ProfileAccess.dispose(controller, "d");
    }
    void cleanup(Object controller) throws Throwable {
        if (!ready()) return;
        ProfilePolicy.Ticket old = tickets.get(controller);
        if (old == null) return;
        cancel(controller);
        ProfileAccess.dispose(controller, "e");
        Object strategy = ProfileAccess.get(controller, "s");
        if (strategy != null && strategy.getClass() == r.type("c9.h")) ProfileAccess.dispose(strategy, "b");
        if (active.contains(controller)) {
            // t0 uses this controller's retained old PID and does not rewrite saved overlay preference.
            ProfileAccess.call(controller, "t0");
            active.remove(controller);
        }
        Object close = ProfileAccess.get(r.type(FrameHooks.STATE), "CLOSE");
        Object disable = ProfileAccess.get(r.type(FrameHooks.REASON), "DISABLE");
        ProfileAccess.call(controller, "N0", close, disable);
        ProfileAccess.set(controller, "h", null);
        ProfileAccess.set(controller, "q", null);
        tickets.remove(controller);
        r.log("frame: stage=cleanup,package=" + old.pkg + ",pid=" + old.pid + ",revision=" + old.revision);
    }
    void emitted() {
        if (!ready()) return;
        Object controller = current.get();
        if (controller != null && applying.get() != null) active.add(controller);
    }
    void closed() {
        if (!ready()) return;
        Object controller = current.get();
        if (controller != null && applying.get() != null) active.remove(controller);
    }
    void schedule(Object controller, int delay, Object state, Object reason,
                  ProfilePolicy.Ticket ticket) throws Throwable {
        if (!ready()) return;
        cancel(controller);
        Runnable task = () -> {
            pending.remove(controller);
            try {
                if (tickets.get(controller) != ticket || !valid(ticket)) return;
                r.original(r.method(FrameHooks.CONTROLLER, "J0", r.type(FrameHooks.STATE),
                        r.type(FrameHooks.REASON), Integer.class), controller, state, reason, 0);
            } catch (Throwable failure) { r.error("frame_delayed_recovery",failure); }
        };
        pending.put(controller, task);
        main.postDelayed(task, Math.max(0, delay));
    }
    void beforeChange(ConfigSnapshot old, ConfigSnapshot next) throws Throwable {
        if (!ready()) return;
        for (Object controller : tickets.keySet().toArray()) {
            ProfilePolicy.Ticket ticket = tickets.get(controller);
            if (ticket == null) continue;
            cancel(controller);
            if (!next.allows(ticket.pkg, "frame") || !old.rule(ticket.pkg).frameDonor.equals(next.rule(ticket.pkg).frameDonor)
                    || !old.rule(ticket.pkg).tuning.sameFrame(next.rule(ticket.pkg).tuning))
                cleanup(controller);
        }
    }
    void afterChange() throws Throwable {
        if (!ready()) return;
        store.reconcile();
        Object controller = current.get();
        if (controller != null) prepare(controller);
    }
}
