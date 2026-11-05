package com.yuka.gamecube.tools.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class DevicePolicyTest {
    @Test public void admitsOnlyExactTargets() {
        for(String model:new String[]{"V2352A","V2352GA"}) assertTrue(DevicePolicy.deviceAllowed(model,"PD2352",36,""));
        for(String ic:new String[]{"iqoo0301_x_q1.0","iqoo0301_x_q2.0","iqoo0301_x_q3.0","unknown"}) assertFalse(DevicePolicy.deviceAllowed("V2352GA","PD2352",36,ic));
        assertFalse(DevicePolicy.deviceAllowed("V2352GB","PD2352",36,""));
        assertFalse(DevicePolicy.deviceAllowed("V2352GA","PD2352X",36,""));
        assertTrue(DevicePolicy.deviceAllowed("V2352GA","PD2352",35,""));
        assertFalse(DevicePolicy.deviceAllowed("V2352GA","PD2352",36,null));
    }
    @Test public void rejectsWrongProcessOrApk() {
        assertTrue(DevicePolicy.processAllowed("com.vivo.gamecube:assistantui"));
        assertTrue(DevicePolicy.processAllowed("com.vivo.gamecube:daemon"));
        assertFalse(DevicePolicy.processAllowed("system_server"));
        assertFalse(DevicePolicy.processAllowed("com.vivo.gamecube"));
        assertTrue(DevicePolicy.apkAllowed(1401000,"14.0.10.00_de",DevicePolicy.APK_SHA256));
        assertFalse(DevicePolicy.apkAllowed(1401000,"14.0.10.00_de","changed"));
    }
}
