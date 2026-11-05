package com.yuka.gamecube.tools.hooks.overlay;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class OverlayAccess {
    private OverlayAccess() {}

    static Object enumValue(Class<?> type, String name) throws ReflectiveOperationException {
        // OEM enum fields are obfuscated; Enum.name() retains the constructor name.
        Object[] values = type.getEnumConstants();
        if (values != null) for (Object value : values)
            if (((Enum<?>) value).name().equals(name)) return value;
        throw new NoSuchFieldException(type.getName() + ": enum " + name);
    }

    static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        Field field = com.yuka.gamecube.tools.hooks.Reflect.field(owner, name);
        if (field.getType() != type) throw new NoSuchFieldException(owner.getName() + "." + name + ": type");
        return field;
    }

    static Object invoke(Method method, Object target, Object... args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
}
