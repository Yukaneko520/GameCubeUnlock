package com.yuka.gamecube.tools.hooks;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
public interface ConfigListener {
    default void beforeChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {}
    default void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {}
}
