package com.yuka.gamecube.tools.compat;

/** Exact names in the pinned APKs; no fallback to a different ROM's descriptors. */
public record SuperControlLayout(String touchHelper,String gyroHelper,String ordinaryWriter,
                                 String pacing,String monitor,String instance,String refresh,
                                 String flags,String baseFlags,String switchMap) {
    public static SuperControlLayout forSdk(int sdk) {
        return switch(sdk) {
            case 34 -> new SuperControlLayout("h8.d",null,"w6.a",
                    "b0.u","b0.h","v","B","j","b0.f","j");
            case 35 -> new SuperControlLayout("k8.f","k8.e","p6.a",
                    "e0.v","e0.h","C","I","k","e0.f","k");
            case 36 -> new SuperControlLayout("com.vivo.common.gamemanipulation.b",
                    "com.vivo.gameassistant.gamemanipulation.e","com.vivo.common.gamemanipulation.a",
                    "e0.f0","e0.n","M","S","r","e0.l","r");
            default -> throw new IllegalArgumentException("Unsupported Super control SDK: "+sdk);
        };
    }
    public String enabled() { return pacing.equals("b0.u")?"t":pacing.equals("e0.v")?"v":"D"; }
    public String dispatch() { return pacing.equals("b0.u")?"s":pacing.equals("e0.v")?"z":"J"; }
    public String valid() { return pacing.equals("b0.u")?"s":pacing.equals("e0.v")?"u":"C"; }
    public String adjust() { return pacing.equals("b0.u")?"D":pacing.equals("e0.v")?"K":"V"; }
    public String foreground() { return pacing.equals("b0.u")?"p":pacing.equals("e0.v")?"e":"f"; }
    public String gameList() { return pacing.equals("e0.v")?"l.d":"k.d"; }
    public String gameMembership() { return pacing.equals("b0.u")?"I":pacing.equals("e0.v")?"K":"L"; }
    public String remove() { return pacing.equals("b0.u")?"L":pacing.equals("e0.v")?"S":"f0"; }
    public String deathForward() { return pacing.equals("b0.u")?"l":pacing.equals("e0.v")?"i":"n"; }
    public String baseMethod() { return pacing.equals("b0.u")?"c":"d"; }
    public String ordinaryController() { return pacing.equals("b0.u")?"h8.c":pacing.equals("e0.v")?"k8.c":"com.vivo.gameassistant.gamemanipulation.c"; }
}
