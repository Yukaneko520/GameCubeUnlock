package com.yuka.gamecube.tools.hooks.general;

import android.content.Context;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Preflight is local to audio, so an unavailable member cannot withdraw manipulation hooks. */
final class LegacyAudioAccess {
    final AudioLayout layout;
    final Method resolver, command, common, version, spatializer, capable, region, setAudio;
    final Method[] applies;
    final Class<?> equalizer, enhancer, reason;
    final Constructor<?> audioConstructor;

    LegacyAudioAccess(HookRuntime r) throws Throwable {
        layout=AudioLayout.forSdk(r.adapter.baseline().sdk());
        resolver = r.method(layout.resolverOwner(),layout.resolverMethod(), String.class);
        command = r.method(layout.controller(),layout.command(), String.class, Boolean.class, Boolean.class);
        equalizer = r.type(layout.equalizer()); enhancer = r.type(layout.enhancer());
        reason = r.type("com.vivo.gameassistant.gamecustomsound.GameSoundReason");
        applies=new Method[]{r.method(layout.controller(),layout.apply(),
                String.class, Boolean.class, Boolean.class, Boolean.class, equalizer, enhancer, reason),
                r.method(layout.controller(),layout.alternate(), String.class, Boolean.class,
                        enhancer, equalizer, boolean.class)};
        for (Method apply : applies)
            if (apply.getReturnType() != void.class) throw new NoSuchMethodException("audio apply descriptor");
        common = r.method(r.adapter.commonType(),layout.common());
        version = r.method(r.adapter.commonType(),layout.version());
        spatializer = layout.spatializer()==null?null:r.method(r.adapter.commonType(),layout.spatializer());
        capable = r.method(r.adapter.commonType(),layout.capable(), Context.class);
        region = r.method(r.adapter.commonType(),layout.region());
        Class<?> audio = r.type("android.media.AudioFeatures");
        audioConstructor = audio.getConstructor(Context.class, String.class, Object.class);
        setAudio = audio.getMethod("setAudioFeature", String.class, Object.class);
        if (command.getReturnType() != String.class
                || resolver.getReturnType() != r.type(layout.preset()) || version.getReturnType()!=int.class
                || capable.getReturnType()!=boolean.class || region.getReturnType()!=boolean.class
                || (spatializer!=null && spatializer.getReturnType()!=boolean.class))
            throw new NoSuchMethodException("audio descriptor mismatch");
    }
    boolean conflict(HookRuntime r,Object values) throws Throwable {
        // The pinned OS5 audio route has no spatializer predicate; do not require an OS6-only method.
        return spatializer!=null && Boolean.TRUE.equals(r.original(spatializer,values));
    }
}

