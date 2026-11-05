package com.yuka.gamecube.tools.hooks.profiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.reflect.Field;
import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import android.os.Handler;
import android.os.Looper;

/** Runtime-only cache overlay. Room and OEM configuration are never modified. */
final class ProfileStore {
    static final String SERVICE = "com.vivo.gameassistant.AssistantUIService";
    static final String UTILS = "com.vivo.gameassistant.utils.e0";
    final HookRuntime r;
    final boolean frame;
    final MainThreadGate main;
    private final Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Object> clones = new LinkedHashMap<>();
    private final Map<String, Object> sources = new LinkedHashMap<>();
    private boolean reconciling;
    private long loggedRevision = Long.MIN_VALUE;
    private ProfilePolicy.CacheStamp observed;
    private Field cache;
    @FunctionalInterface interface Retirement { void close(Object profile) throws Throwable; }
    Retirement beforeRemoval = profile -> {};

    ProfileStore(HookRuntime runtime, boolean frameFamily) {
        r = runtime; frame = frameFamily;
        Handler handler = new Handler(Looper.getMainLooper());
        main = new MainThreadGate(() -> Looper.myLooper() == Looper.getMainLooper(), handler::post,
                failure -> r.error(feature()+"_main_refresh",failure));
    }
    private boolean legacy() { return r.adapter.baseline().sdk()<=35; }
    private boolean os4() { return r.adapter.baseline().sdk()==34; }
    private boolean legacySr() { return !frame && legacy(); }
    String fieldName() { return frame ? r.adapter.frameProfile().cache() : legacySr()?"j":"i"; }
    String utils() { return os4()?"mb.n0":legacy()?"com.vivo.gameassistant.utils.z0":UTILS; }
    String frameSupport() { return os4()?"y0":legacy()?"w0":"n0"; }
    String srSupport() { return legacySr()?"e1":"U0"; }
    String feature() { return frame ? "frame" : "sr"; }
    synchronized void preflight() throws Exception {
        cache = ProfileAccess.field(r.type(SERVICE), fieldName());
        if (!List.class.isAssignableFrom(cache.getType()))
            throw new NoSuchFieldException(SERVICE + "." + fieldName());
        r.method(r.adapter.commonType(),r.adapter.support().commonInstance());
        r.method(r.adapter.commonType(), frame ? (os4()?"a0":legacy()?"x0":"W0") : legacySr()?"X0":"E1");
        if (frame) {
            var layout=r.adapter.frameProfile();String entity=layout.entity(),triplet=layout.triplet();
            ProfileCopies.frameConstructor(r.loader,entity);
            r.type(triplet).getConstructor(int.class, int.class, int.class);
            for (String getter : new String[]{"a", "b", "c"}) require(triplet, getter, int.class);
            for (String getter : os4()?new String[]{"b","f","h"}:new String[]{"b", "g", "k"}) require(entity, getter, String.class);
            for (String getter : os4()?new String[]{"d","e"}:new String[]{"f", "h", "l"}) require(entity, getter, int.class);
            if(!os4()) for (String getter : new String[]{"c", "j"}) require(entity, getter, double.class);
            require(entity, os4()?"i":"m", boolean.class);
            require(entity, "a", List.class); require(entity, os4()?"g":"i", List.class);
            require(entity, os4()?"c":"d", Map.class);
            r.method(utils(),frameSupport(), String.class);
            String factory=layout.factory();
            for(String method:List.of("c","a","b")) r.method(factory,method);
        } else {
            String entity=legacySr()?"v6.e":"y7.e";
            ProfileCopies.srConstructor(r.loader,entity,legacySr());
            for (String getter : new String[]{"a", "j", "p"}) require(entity, getter, String.class);
            if(!legacySr()) require(entity,"r",String.class);
            for (String getter : new String[]{"d", "e", "f", "g", "h", "k", "l", "m", "q"}) require(entity, getter, int.class);
            for (String getter : new String[]{"b", "c", "n", "o"}) require(entity, getter, double.class);
            r.method(utils(),srSupport(), android.content.Context.class, String.class);
            if(legacySr()) {
                r.method("z6.b","e",String.class,String.class,r.type("x6.b"));
                ProfileAccess.field(r.type(SERVICE),"h");
                return;
            }
            r.method("com.vivo.common.utils.h0", "h"); r.method("com.vivo.common.utils.h0", "i");
            r.method("com.vivo.common.utils.l", "W1"); r.method("com.vivo.gameassistant.utils.p", "a");
            r.method("c8.b", "f", String.class, String.class, r.type("a8.b"));
        }
    }
    private void require(String owner, String name, Class<?> result) throws Exception {
        if (r.method(owner, name).getReturnType() != result) throw new NoSuchMethodException(owner + "." + name);
    }
    synchronized Object donated(String pkg) { return clones.get(pkg); }
    synchronized boolean stockSrSupport(String pkg) throws Throwable {
        List<Object> nativeList = ProfilePolicy.nativeEntries(current(), owned);
        if (!nativeList.isEmpty()) return find(nativeList, pkg) != null;
        if(legacySr()) return Boolean.TRUE.equals(r.original(r.method("z6.b","e",String.class,String.class,r.type("x6.b")),
                null,"super_resolution",pkg,ProfileAccess.get(r.type(SERVICE),"h")));
        boolean hd = (Boolean) ProfileAccess.call(r.type("com.vivo.common.utils.l"), "W1");
        Object config = ProfileAccess.call(r.type("com.vivo.gameassistant.utils.p"), "a");
        return (Boolean) r.original(r.method("c8.b", "f", String.class, String.class, r.type("a8.b")),
                null, hd ? "game_super_hd_engine" : "super_resolution", pkg, config);
    }
    synchronized void reconcile() throws Throwable {
        if (!main.check(this::reconcile)) return;
        if (reconciling || r.context() == null) return;
        ConfigSnapshot snapshot = r.snapshot();
        Object raw = cache.get(null);
        if(raw==null && frame && legacy() && owned.isEmpty()) {
            r.log("feature=frame stage=profile baseline="+r.adapter.baseline().id()+" reason=native_cache_not_loaded cache="+fieldName());return;
        }
        if (!(raw instanceof List<?> list)) throw new IllegalStateException("Invalid " + feature() + " cache");
        if (observed != null && observed.matches(snapshot, raw, list.size())) return;
        reconciling = true;
        try {
            List<Object> oldList = current();
            List<Object> nativeList = ProfilePolicy.nativeEntries(oldList, owned);
            List<Object> next = new ArrayList<>(nativeList);
            Map<String, Object> nextClones = new LinkedHashMap<>();
            Map<String, Object> nextSources = new LinkedHashMap<>();
            boolean capable;
            try { capable = snapshot.enabled && hardware(); }
            catch (Throwable failure) {
                capable = false;
                if (loggedRevision != snapshot.revision) r.error(feature()+"_hardware_query",failure);
            }
            for (Map.Entry<String, AppRule> entry : snapshot.apps.entrySet()) {
                String target = entry.getKey();
                AppRule rule = entry.getValue();
                String donorName = frame ? rule.frameDonor : rule.srDonor;
                boolean candidate = snapshot.allows(target, feature());
                if (!candidate) continue;
                if (!capable) {
                    decision(target, donorName, nativeList.size(), "hardware_false", ""); continue;
                }
                try {
                    NativeProfileResolver.Result selected = NativeProfileResolver.resolve(nativeList, frame,
                            target, donorName, this::eligible);
                    Object donor = selected.profile();
                    if (donor == null) {
                        decision(target, donorName, nativeList.size(), selected.reason(), selected.donor()); continue;
                    }
                    if (!ProfilePolicy.canClone(candidate, capable, find(nativeList, target) != null,
                            target, selected.donor(), true)) continue;
                    Object clone = sources.get(target) == donor ? clones.get(target) : null;
                    if (clone == null) clone = frame ? ProfileCopies.frame(donor, target) : ProfileCopies.sr(donor, target);
                    next.add(clone);
                    nextClones.put(target, clone);
                    nextSources.put(target, donor);
                    decision(target, donorName, nativeList.size(), "ready", selected.donor()
                            + "," + NativeProfileResolver.describe(donor, frame));
                } catch (Throwable failure) {
                    r.error(feature()+"_profile_resolution:"+target,failure);
                    decision(target, donorName, nativeList.size(), "resolution_error", failure.getClass().getSimpleName());
                }
            }
            for (Object retired : ProfilePolicy.retiredProfiles(clones, nextClones)) beforeRemoval.close(retired);
            // Close old sessions before removing their profiles from the cache.
            if (!sameIdentities(oldList, next))
                cache.set(null, new CopyOnWriteArrayList<>(next));
            owned.clear(); owned.addAll(nextClones.values());
            clones.clear(); clones.putAll(nextClones);
            sources.clear(); sources.putAll(nextSources);
            Object published = cache.get(null);
            observed = new ProfilePolicy.CacheStamp(snapshot, published, ((List<?>) published).size());
            if (loggedRevision != snapshot.revision) {
                loggedRevision = snapshot.revision;
                r.log(feature() + ": runtime profiles=" + clones.keySet() + ", revision=" + snapshot.revision
                        + "; unresolved donors remain stock; activation belongs to Vivo overlay");
            }
        } finally { reconciling = false; }
    }
    private void decision(String target, String explicit, int nativeSize, String reason, String detail) {
        r.log(feature() + ": stage=profile,package=" + target + ",revision=" + r.snapshot().revision
                + ",mode=" + (explicit == null || explicit.isEmpty() ? "auto" : "explicit")
                + ",requested=" + explicit + ",nativeSize=" + nativeSize + ",reason=" + reason + ",donor=" + detail);
    }
    private boolean hardware() throws Throwable {
        Object common = ProfileAccess.call(r.type(r.adapter.commonType()),r.adapter.support().commonInstance());
        boolean supported = (Boolean) ProfileAccess.call(common, frame ? (os4()?"a0":legacy()?"x0":"W0") : legacySr()?"X0":"E1");
        if (!supported) return false;
        if(legacySr()) return true; // OS5 e1 uses q.g().h(), which delegates to the exact same X0 capability.
        if (frame) {
            var layout=r.adapter.frameProfile();
            Object strategies = ProfileAccess.call(r.type(layout.factory()), "c");
            Object primary=ProfileAccess.call(strategies,"a"),secondary=ProfileAccess.call(strategies,"b");
            boolean matches=primary.getClass()==r.type(layout.strategy()) && secondary.getClass()==r.type(layout.values());
            if(legacy()) r.log("feature=frame stage=strategy baseline="+r.adapter.baseline().id()+" primary="+primary.getClass().getName()
                    +" secondary="+secondary.getClass().getName()+" expected="+layout.strategy()+"/"+layout.values()+" matched="+matches+" cache="+fieldName());
            return matches;
        }
        return (Boolean) ProfileAccess.call(ProfileAccess.call(r.type("com.vivo.common.utils.h0"), "h"), "i");
    }
    private boolean eligible(String pkg) throws Throwable {
        if (frame) return (Boolean) r.original(r.method(utils(),frameSupport(), String.class), null, pkg);
        return (Boolean) r.original(r.method(utils(),srSupport(), android.content.Context.class, String.class),
                null, r.context(), pkg);
    }
    @SuppressWarnings("unchecked") private List<Object> current() throws Exception {
        Object current = cache.get(null);
        if (!(current instanceof List<?>)) throw new IllegalStateException("Invalid " + feature() + " cache");
        return new ArrayList<>((List<Object>) current);
    }
    Object find(List<Object> entries, String pkg) throws Throwable {
        return NativeProfileResolver.find(entries, frame, pkg);
    }
    private static boolean sameIdentities(List<Object> one, List<Object> two) {
        if (one.size() != two.size()) return false;
        for (int i = 0; i < one.size(); i++) if (one.get(i) != two.get(i)) return false;
        return true;
    }
    synchronized void restore() throws Throwable {
        if (cache == null) return;
        if (cache.get(null)==null && owned.isEmpty()) return;
        List<Object> list = ProfilePolicy.nativeEntries(current(), owned);
        cache.set(null, new CopyOnWriteArrayList<>(list));
        clones.clear(); sources.clear(); owned.clear();
        observed = null;
    }
    synchronized Object retainForCleanup(Object profile) throws Throwable {
        Object old = cache.get(null);
        List<Object> retained = current();
        retained.removeIf(entry -> entry == profile);
        retained.add(0, profile);
        cache.set(null, new CopyOnWriteArrayList<>(retained));
        return old;
    }
    synchronized void restoreAfterCleanup(Object old) throws Throwable {
        cache.set(null, old);
    }
}
