package com.yuka.gamecube.tools.compat;

/** Exact menu-builder and item descriptors from each pinned assistant APK. */
public record DemonstrationLayout(String creator,String create,String utility,String itemType,
                                  String title,String enabled,String badge,String iconOn,String iconOff) {
    public static final String ITEM="com.vivo.gameassistant.homegui.sideslide.controlcenter.switches.model.j";
    public static final String TYPE="com.vivo.gameassistant.homegui.sideslide.controlcenter.switches.model.QuickSwitchItemType";
    public String presenter() { return creator.equals("p8.a")?"l9.d0":creator.equals("t8.c")?"p9.e0":"va.d0"; }
    public String load() { return creator.equals("ba.b")?"a":"b"; }
    public record Voice(String data,String list,String get,String initialize,String reader,String info,String hardware,
                        String privacyType,String privacy) {}
    public static Voice voice(int sdk) {
        return switch(sdk) {
            case 34 -> new Voice("m7.b","c","g1","m1","m7.c","c","N","e7.d","Z0");
            case 35 -> new Voice("l7.b","c","t1","z1","l7.c","c","d0","a7.h","y1");
            case 36 -> new Voice("t8.b","d","h1","m1","t8.c","a","u0","com.vivo.common.utils.l","c2");
            default -> throw new IllegalArgumentException("Unsupported demonstration voice SDK");
        };
    }
    public static DemonstrationLayout forSdk(int sdk) {
        return switch(sdk) {
            case 34 -> new DemonstrationLayout("p8.a","a","mb.q","m","A","t","y","w","v");
            case 35 -> new DemonstrationLayout("t8.c","c","com.vivo.gameassistant.utils.z","l","A","s","y","w","v");
            case 36 -> new DemonstrationLayout("ba.b","h","com.vivo.gameassistant.utils.u","o","F","w","D","B","z");
            default -> throw new IllegalArgumentException("Unsupported demonstration SDK");
        };
    }
}
