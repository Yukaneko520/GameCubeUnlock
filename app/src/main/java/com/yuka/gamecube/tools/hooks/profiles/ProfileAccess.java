package com.yuka.gamecube.tools.hooks.profiles;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedInterface;
import com.yuka.gamecube.tools.hooks.HookRuntime;

final class ProfileAccess implements AutoCloseable {
    final HookRuntime r;
    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();
    ProfileAccess(HookRuntime runtime) { r = runtime; }
    void hook(String owner, String name, Class<?> result, XposedInterface.Hooker fn,
              Class<?>... args) throws Throwable {
        Method method = r.method(owner, name, args);
        if (method.getReturnType() != result) throw new NoSuchMethodException(owner + "." + name);
        handles.add(r.module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(fn));
    }
    void constructor(String owner, XposedInterface.Hooker fn, Class<?>... args) throws Throwable {
        handles.add(r.module.hook(r.type(owner).getDeclaredConstructor(args))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(fn));
    }
    @Override public void close() {
        for (int i = handles.size() - 1; i >= 0; i--) handles.get(i).unhook();
        handles.clear();
    }
    static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }
    static Object get(Object owner, String name) throws ReflectiveOperationException {
        return field(owner instanceof Class<?> ? (Class<?>) owner : owner.getClass(), name)
                .get(owner instanceof Class<?> ? null : owner);
    }
    static void set(Object owner, String name, Object value) throws ReflectiveOperationException {
        field(owner instanceof Class<?> ? (Class<?>) owner : owner.getClass(), name)
                .set(owner instanceof Class<?> ? null : owner, value);
    }
    static Object call(Object owner, String name, Object... args) throws Throwable {
        Class<?> type = owner instanceof Class<?> ? (Class<?>) owner : owner.getClass();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                boolean match = true;
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    Class<?> p = params[i];
                    if (p == boolean.class) p = Boolean.class;
                    if (p == int.class) p = Integer.class;
                    if (p == long.class) p = Long.class;
                    if (args[i] != null && !p.isInstance(args[i])) match = false;
                }
                if (!match) continue;
                try { m.setAccessible(true); return m.invoke(owner instanceof Class<?> ? null : owner, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    static void dispose(Object owner, String name) throws Throwable {
        Object disposable = get(owner, name);
        if (disposable != null) { call(disposable, "dispose"); set(owner, name, null); }
    }
}
