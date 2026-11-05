package com.yuka.gamecube.tools.hooks.general;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

/** Creates local rule values without copying or altering database identities. */
final class RuleCloner {
    private final Class<?> game;
    private final Constructor<?> couplingConstructor, gameConstructor, extraConstructor;
    private final String extendedValue;

    RuleCloner(Class<?> game, Class<?> extra, Class<?> coupling) throws NoSuchMethodException {
        this(game,extra,coupling,"n");
    }
    RuleCloner(Class<?> game, Class<?> extra, Class<?> coupling,String extendedValue) throws NoSuchMethodException {
        this.game = game;
        this.extendedValue=extendedValue;
        if(extra!=null && extra.getMethod(extendedValue).getReturnType()!=int.class) throw new NoSuchMethodException(extendedValue);
        couplingConstructor = coupling.getConstructor(game, List.class, List.class);
        Class<?>[] arguments = {String.class, String.class, String.class, String.class,
                String.class, boolean.class, int.class, boolean.class, int.class};
        gameConstructor = game.getConstructor(arguments);
        Class<?>[] extended = Arrays.copyOf(arguments, 10);
        extended[9] = int.class;
        extraConstructor = extra==null?null:extra.getConstructor(extended);
    }

    Object cloneCoupling(Object source, String target, String channel) throws Throwable {
        Object row = RuleAccess.get(source, "c");
        Object[] args = {RuleAccess.get(row, "c"), RuleAccess.get(row, "h"), target,
                RuleAccess.get(row, "f"), channel, RuleAccess.get(row, "i"),
                RuleAccess.get(row, "e"), RuleAccess.get(row, "j"), RuleAccess.get(row, "b")};
        Object cloned;
        if (row.getClass() == game) cloned = gameConstructor.newInstance(args);
        else if (extraConstructor!=null && row.getClass() == extraConstructor.getDeclaringClass()) {
            Object[] extended = Arrays.copyOf(args, 10);
            extended[9] = RuleAccess.get(row, extendedValue);
            cloned = extraConstructor.newInstance(extended);
        } else throw new IllegalArgumentException("unknown native game subtype");
        // Coupled lists stay identical; neither their entries nor their database IDs are modified.
        return couplingConstructor.newInstance(cloned, RuleAccess.get(source, "a"), RuleAccess.get(source, "b"));
    }
}
