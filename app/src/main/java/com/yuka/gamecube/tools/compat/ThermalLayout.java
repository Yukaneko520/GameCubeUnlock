package com.yuka.gamecube.tools.compat;

/** Names from pinned OS5/OS6 DEX, including synthetic callbacks (not JADX field aliases). */
public record ThermalLayout(String frame,String frameHot,String frameCool,String frameTick,String frameGetter,
        String sr,String srHot,String srCool,String srTick,String srGetter,
        String memc,String memcHot,String memcCurrent,String memcBase,String memcFlag,
        String memcDispatch,String background,String fpsEnter,String fpsDeath,
        String q3,String q3Memc,String q3Sr,String q3Pid,String q3Package) {
    public static ThermalLayout forSdk(int sdk) {
        return switch(sdk) {
            case 34 -> new ThermalLayout("com.vivo.gameassistant.frameinterpolation.f","Q","P","S","t0",
                    null,null,null,null,null,null,null,null,null,null,null,null,"p","l",null,null,null,null,null);
            case 35 -> new ThermalLayout("com.vivo.gameassistant.frameinterpolation.f","g0","b0","i0","z0",
                    "com.vivo.gameassistant.superresolution.b","K0","B0","O0","K0",
                    "j0.k","q0","w","y","r","g0","j2.a","e","i",
                    "j0.j0","M1","R1","x0","y0");
            case 36 -> new ThermalLayout("com.vivo.gameassistant.frameinterpolation.h","G0","B0","K0","j0",
                    "com.vivo.gameassistant.superresolution.i","j1","Z0","q","x0",
                    "j0.l","v0","x","z","s","k0","o2.a","f","n",
                    "j0.s0","I2","N2","K0","L0");
            default -> throw new IllegalArgumentException("Unsupported thermal baseline: "+sdk);
        };
    }
    public boolean os4() { return memc==null; }
    public String fpsOwner() { return os4()?"h0.f":"m0.f"; }
    public String fpsApply() { return os4()?"j":"k"; }
    public String fpsRelease() { return os4()?"k":"l"; }
    public String fpsRefresh() { return os4()?"D":"E"; }
    public String fpsHandle() { return os4()?"l":"m"; }
    public String fpsRow() { return os4()?"n":"o"; }
    public String fpsNextRow() { return os4()?"o":"p"; }
}
