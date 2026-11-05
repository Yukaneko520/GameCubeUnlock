package com.yuka.gamecube.tools.hooks.general;

import com.yuka.gamecube.tools.hooks.HookRuntime;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Exact APK descriptors, resolved before any hook is installed. */
final class RuleAccess {
    final HookRuntime runtime;
    final Class<?> config, coupling, game;
    final Method f, e, j, h, l, channel;
    private final Constructor<?> configConstructor;
    private final RuleCloner cloner;

    RuleAccess(HookRuntime runtime) throws Throwable {
        this.runtime = runtime;
        var api=runtime.adapter.support();
        config = runtime.type(api.config());
        coupling = runtime.type("com.vivo.common.supportlist.pojo.a");
        game = runtime.type(api.game());
        f = runtime.method(api.evaluator(), api.function(), String.class, String.class, config);
        e = runtime.method(api.evaluator(), api.scoped(), String.class, config);
        j = runtime.method(api.evaluator(), api.membership(), String.class, String.class, config);
        h = runtime.method(api.evaluator(), api.capability(), config);
        l = runtime.method(api.evaluator(), api.coupling(), coupling);
        channel = runtime.method(api.channelType(), api.channel(), String.class, String.class, String.class);
        configConstructor = config.getConstructor(String.class, List.class, List.class, List.class);
        cloner = new RuleCloner(game, api.extendedGame()==null?null:runtime.type(api.extendedGame()), coupling,api.extendedValue());
        for (Method method : new Method[]{f, e, j, h, l, channel}) {
            if (method.getReturnType() != boolean.class) throw new NoSuchMethodException(method.toString());
        }
    }

    Object invoke(Method method, Object receiver, Object... args) throws Throwable {
        return runtime.original(method, receiver, args);
    }

    static Object get(Object object, String name) throws Throwable {
        if (object == null) throw new IllegalArgumentException("null native rule");
        try { return object.getClass().getMethod(name).invoke(object); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    static String string(Object object, String name) throws Throwable {
        Object value = get(object, name);
        if (!(value instanceof String)) throw new IllegalArgumentException("missing rule " + name);
        return (String) value;
    }

    static List<?> list(Object object, String name) throws Throwable {
        Object value = get(object, name);
        if (value == null) return java.util.Collections.emptyList();
        if (!(value instanceof List)) throw new IllegalArgumentException("non-list rule " + name);
        return (List<?>) value;
    }

    Object view(String function, List<?> hardware, List<?> software, List<?> games) throws Exception {
        return configConstructor.newInstance(function, hardware, software, games);
    }

    Object cloneCoupling(Object source, String target, String channelValue) throws Throwable {
        return cloner.cloneCoupling(source, target, channelValue);
    }

    List<Object> select(Object source, String getter, String function, boolean software,
                        boolean scoped) throws Throwable {
        List<Object> result = new ArrayList<>();
        for (Object row : list(source, getter)) {
            Object dimen = software ? get(row, "a") : "b".equals(getter) ? get(row, "c") : row;
            String ownFunction = string(dimen, "c");
            if (function.equals(ownFunction)) result.add(row);
            else if (scoped) throw new IllegalArgumentException("mixed scoped function");
        }
        return result;
    }
}
