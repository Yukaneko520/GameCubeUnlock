package com.yuka.gamecube.tools.hooks.backend;

import android.content.Context;
import android.system.Os;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/** Pinned OEM JNI only. No shell, new native code, property or frequency writes. */
final class FlaAccess implements FlaAdmission.Io {
    static final String FLAG="/sys/rsc/fas/fla_enable";
    static final String KERNEL="6.1.145-android14-11-maybe-dirty";
    final Method initialize,bootCompleted;
    private final Method read,write;
    FlaAccess(ClassLoader loader) throws ReflectiveOperationException {
        initialize=method(loader,"com.vivo.services.rms.RmsInjectorImpl","initialize",void.class,false,false,Context.class);
        bootCompleted=method(loader,"com.vivo.services.qos.policy.list.BootUpPolicy","bootCompleted",void.class,false,false);
        String jni="com.vivo.services.rms.util.JniTool";
        read=method(loader,jni,"readFile",String.class,true,true,String.class);
        write=method(loader,jni,"writeFile",int.class,true,true,String.class,String.class);
    }
    private static Method method(ClassLoader loader,String owner,String name,Class<?> result,
                                 boolean isStatic,boolean isNative,Class<?>...args) throws ReflectiveOperationException {
        Method method=Class.forName(owner,false,loader).getDeclaredMethod(name,args);
        int flags=method.getModifiers();
        if(method.getReturnType()!=result || !Modifier.isPublic(flags)
                || Modifier.isStatic(flags)!=isStatic || Modifier.isNative(flags)!=isNative)
            throw new NoSuchMethodException(owner+"."+name+" descriptor/modifiers");
        method.setAccessible(true); return method;
    }
    private String read(String path) throws ReflectiveOperationException {
        Object value=read.invoke(null,path);
        if(!(value instanceof String text)) throw new IllegalStateException("Native reader returned no text: "+path);
        return text.trim();
    }
    void verifyKernel() {
        // system_server cannot read the generic proc label; uname needs no proc-file access.
        requireKernel(Os.uname().release);
    }
    static void requireKernel(String release) {
        if(!KERNEL.equals(release)) throw new IllegalStateException("Unreviewed FLA kernel: "+release);
    }
    @Override public String read() throws ReflectiveOperationException { return read(FLAG); }
    String bootId() throws ReflectiveOperationException { return read("/proc/sys/kernel/random/boot_id"); }
    @Override public int write(String value) throws ReflectiveOperationException {
        if(!value.equals("0") && !value.equals("1")) throw new IllegalArgumentException("Invalid FLA flag");
        return (Integer)write.invoke(null,FLAG,value);
    }
    void diagnostics(Consumer<String> log) {
        for(String path:new String[]{"/proc/sys/kernel/random/boot_id","/proc/uptime",
                "/sys/devices/system/cpu/cpufreq/policy3/scaling_max_freq",
                "/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq",
                "/sys/rsc/fas/fla_freq_qos_req"}) {
            try {
                String value=read(path).replace('\n','|').replace('\r',' ');
                if(value.length()>3000) value=value.substring(0,3000)+" [truncated]";
                log.accept("stage=diagnostic path="+path+" value="+value+" hardware_effect=unverified");
            } catch(Throwable failure) {
                log.accept("stage=diagnostic_unavailable path="+path+" error="+failure.getClass().getSimpleName());
            }
        }
    }
}
