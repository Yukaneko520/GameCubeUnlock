package com.yuka.gamecube.tools.hooks.profiles;

import com.yuka.gamecube.tools.core.GraphicsTuning;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameTuningTest {
    @Test public void sdkMatchesActualGameFpsAndNonSdkUsesFirstTupleInNativeOrder() {
        var d=GraphicsTuning.defaults();
        var t=new GraphicsTuning(true,GraphicsTuning.parseTuples("60/48/144\n120/60/120"),d.power(),false,0,0,-1,2.5,-40,20);
        assertEquals(144,FrameTuning.choose(t,true,true,60).outputFps());
        assertEquals(120,FrameTuning.choose(t,true,true,120).outputFps());
        assertNull(FrameTuning.choose(t,true,true,90));
        assertEquals(48,FrameTuning.choose(t,true,false,-100).inputFps());
        assertEquals(30,FrameTuning.choose(t,false,false,-100).inputFps());
    }
}
