package com.yuka.gamecube.tools.core;

/** Thermal policy and CPU/GPU clock requests are separate OEM resources. */
public final class BenchmarkMode {
    public static final int OFF=0, AUTO=1, MAXIMUM=2;
    private BenchmarkMode() {}
    public static void validate(int mode) {
        if(mode<OFF || mode>MAXIMUM) throw new IllegalArgumentException("Invalid benchmark mode");
    }
    public static int[] resources(int mode) {
        validate(mode);
        return switch(mode) {
            case AUTO -> new int[]{0xF0100000,100};
            case MAXIMUM -> new int[]{0xF0100000,100,
                    0x40804000,4095,0x40804100,4095,0x40804200,4095,
                    0x40800000,4095,0x40800100,4095,0x40800200,4095,
                    0x42804000,0}; // GPU minimum power level0 is the highest frequency on both target ROMs.
            default -> new int[0];
        };
    }
}
