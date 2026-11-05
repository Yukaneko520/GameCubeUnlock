package com.yuka.gamecube.tools.hooks.backend;

import java.util.List;

/** Actual competing engines/state in each pinned system-server binary. */
record BackendLayout(String alternativePredicate,List<String> competingCapabilities,List<String> competingState,
                     String disabledPackages) {
    static BackendLayout forSdk(int sdk) {
        return switch(sdk) {
            case 35 -> new BackendLayout("isVSRWhitelistApp",List.of("SUPPORT_VSR"),List.of("mVSRRunning"),null);
            case 36 -> new BackendLayout("isVSRWhitelistAppFromGameWatch",
                    List.of("SUPPORT_VSR_GLES","SUPPORT_VSR_VULKAN","SUPPORT_VSR_VNSS_GLES",
                            "SUPPORT_VSR_VNSS_VULKAN","SUPPORT_VSR_VNSS_ECOSDK"),
                    List.of("mVSRPipelineRunning","mVNSSRunning"),"mDisabledVSRPostProcessingWhitelistApp");
            default -> throw new IllegalArgumentException("Unsupported backend SDK: "+sdk);
        };
    }
}
