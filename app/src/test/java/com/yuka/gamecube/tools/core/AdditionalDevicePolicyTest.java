package com.yuka.gamecube.tools.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdditionalDevicePolicyTest {
    @Test public void rejectsNearMatchesAndUnavailableProperties() {
        for (String model : new String[]{null,"","v2352ga","V2352GA ","V2352G","V2352GB"})
            assertFalse(DevicePolicy.deviceAllowed(model,"PD2352",36,""));
        for (String internal : new String[]{null,"","PD2352A","pd2352"," PD2352"})
            assertFalse(DevicePolicy.deviceAllowed("V2352A",internal,36,""));
        for (int sdk : new int[]{0,33,37,Integer.MAX_VALUE})
            assertFalse(DevicePolicy.deviceAllowed("V2352GA","PD2352",sdk,""));
        for (String ic : new String[]{null," ","unknown","0"})
            assertFalse(DevicePolicy.deviceAllowed("V2352GA","PD2352",36,ic));
    }

    @Test public void processAndApkChecksRejectPrefixSuffixAndPartialIdentity() {
        for (String process : new String[]{null,"com.vivo.gamecube:daemon:extra","com.vivo.gamecube:assistantui2",
                "com.vivo.gamecube:assistantui ","other:daemon","com.vivo.gamecube:remote"})
            assertFalse(DevicePolicy.processAllowed(process));
        assertFalse(DevicePolicy.apkAllowed(1401001,"14.0.10.00_de",DevicePolicy.APK_SHA256));
        assertFalse(DevicePolicy.apkAllowed(1401000,"14.0.10.00",DevicePolicy.APK_SHA256));
        assertFalse(DevicePolicy.apkAllowed(1401000,"14.0.10.00_de",DevicePolicy.APK_SHA256.toUpperCase()));
        assertFalse(DevicePolicy.apkAllowed(1401000,"14.0.10.00_de",null));
    }
}
