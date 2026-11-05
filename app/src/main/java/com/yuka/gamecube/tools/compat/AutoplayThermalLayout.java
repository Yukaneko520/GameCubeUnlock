package com.yuka.gamecube.tools.compat;

/** Inline heat branches in the pinned legacy screen-off autoplay implementation. */
public record AutoplayThermalLayout(String controller,String enter,String view,String callback,String battery,
                                    String start,String close,String packageField,String pidField,String contextField,String legacyPanel) {
    public static AutoplayThermalLayout forSdk(int sdk) {
        return switch(sdk) {
            case 34 -> new AutoplayThermalLayout("s7.a","D","s7.b","H","L","I","G","o","q","g","y0");
            case 35 -> new AutoplayThermalLayout("u7.a","L","u7.b","H","L","I","G","n","p","f","e1");
            case 36 -> new AutoplayThermalLayout("a9.a","a0","a9.b","F","J","G","E","n","p","f","N1");
            default -> throw new IllegalArgumentException("Unsupported autoplay SDK");
        };
    }
}
