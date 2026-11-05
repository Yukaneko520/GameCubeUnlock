package com.yuka.gamecube.tools.compat;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Links runtime layout values to the same member contracts checked against the pinned OEM DEX. */
public class ThermalLayoutTest {
    @Test public void os4UsesIntegerCubeDecisionsAndItsOwnFpsRequestFields() {
        var n=ThermalLayout.forSdk(34);
        assertTrue(n.os4());assertNull(n.sr());assertNull(n.memc());assertNull(n.q3());
        assertEquals("Q",n.frameHot());assertEquals("P",n.frameCool());assertEquals("S",n.frameTick());
        assertEquals("t0",n.frameGetter());assertEquals("h0.f",n.fpsOwner());
        assertEquals("j",n.fpsApply());assertEquals("k",n.fpsRelease());assertEquals("D",n.fpsRefresh());
        assertEquals("l",n.fpsHandle());assertEquals("n",n.fpsRow());assertEquals("o",n.fpsNextRow());
        assertEquals("p",n.fpsEnter());assertEquals("l",n.fpsDeath());
    }
    private JSONObject contract(int os,String app) throws Exception {
        return new JSONObject(Files.readString(Path.of("../tools/contracts/os"+os+"-thermal-"+app+".json")));
    }
    private void has(JSONObject contract,String kind,String owner,String name,String descriptor) throws Exception {
        JSONArray rows=contract.getJSONArray(kind);
        for(int i=0;i<rows.length();i++) {
            JSONArray row=rows.getJSONArray(i);
            if(row.getString(0).equals(owner) && row.getString(1).equals(name) && row.getString(2).equals(descriptor)) return;
        }
        fail(kind+" binding absent: "+owner+"."+name+descriptor);
    }
    @Test public void bothLayoutsMatchTheirExactApkContracts() throws Exception {
        for(int os:new int[]{5,6}) {
            var n=ThermalLayout.forSdk(os+30);var cube=contract(os,"cube");var watch=contract(os,"watch");
            var baseline=os==5?TargetProfiles.OS5:TargetProfiles.OS6;
            assertEquals(baseline.gameCubeSha(),cube.getString("sha256"));
            assertEquals(baseline.packages().get("com.vivo.gamewatch"),watch.getString("sha256"));
            has(cube,"methods",n.frame(),n.frameHot(),"(D)Z");has(cube,"methods",n.frame(),n.frameCool(),"(D)Z");
            has(cube,"methods",n.frame(),n.frameTick(),"(Ljava/lang/Long;)V");
            has(cube,"methods",n.sr(),n.srHot(),os==5?"(DI)Z":"(I)Z");
            has(cube,"methods",n.sr(),n.srCool(),os==5?"(DI)Z":"(I)Z");
            has(cube,"methods",n.sr(),n.srTick(),os==5?"(Ljava/lang/Long;)V":"(DD)V");
            String owner=RomAdapter.forBaseline(baseline).owner().type();
            has(cube,"methods",owner,n.frameGetter(),"()L"+n.frame().replace('.','/')+";");
            has(cube,"methods",owner,n.srGetter(),"()L"+n.sr().replace('.','/')+";");
            has(watch,"methods","m0.f","k",os==5?"(Lm0/f$a;)V":"(Lm0/f$a;)J");
            has(watch,"methods","m0.f",n.fpsEnter(),"(IZILjava/lang/String;)V");
            has(watch,"methods","m0.f",n.fpsDeath(),"(IILjava/lang/String;)V");
            has(watch,"methods",n.memc(),n.memcHot(),"(DZ)Z");
            has(watch,"methods",n.memc(),n.memcDispatch(),"(L"+n.memc().replace('.','/')+"$c;Z)V");
            for(String render:os==5?new String[]{"h0","p0"}:new String[]{"l0","u0"})
                has(watch,"methods",n.memc(),render,"(L"+n.memc().replace('.','/')+"$c;Z)V");
            has(watch,"methods",n.memc(),os==5?"n0":"s0","()V");
            has(watch,"methods",n.memc()+"$a","b","(Ljava/lang/String;Landroid/net/Uri;Ljava/lang/String;"+(os==5?"I":"")+")V");
            for(String name:new String[]{n.memcCurrent(),n.memcBase()})
                has(watch,"fields",n.memc(),name,"L"+n.memc().replace('.','/')+"$c;");
            has(watch,"fields",n.memc(),n.memcFlag(),"Z");
            has(watch,"methods",n.q3(),n.q3Memc(),"(DZ)Z");has(watch,"methods",n.q3(),n.q3Sr(),"(DZ)Z");
            has(watch,"fields",n.q3(),n.q3Pid(),"I");has(watch,"fields",n.q3(),n.q3Package(),"Ljava/lang/String;");
            has(watch,"methods",n.background(),"a","()Landroid/os/Handler;");
        }
        assertThrows(IllegalArgumentException.class,()->ThermalLayout.forSdk(33));
    }
}
