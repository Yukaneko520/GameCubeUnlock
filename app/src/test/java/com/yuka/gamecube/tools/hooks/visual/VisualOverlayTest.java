package com.yuka.gamecube.tools.hooks.visual;

import com.yuka.gamecube.tools.core.VisualTuning;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisualOverlayTest {
    private VisualTuning hawkeye(int color) {
        return new VisualTuning(true,1,-1,0,13,0,128,50,0,color);
    }
    @Test public void onlyEligibleHawkeyeBlocksGeneralFilters() {
        assertTrue(VisualOverlay.blocksGeneral(true,hawkeye(273),false));
        assertTrue(VisualOverlay.blocksGeneral(true,hawkeye(273),true));
        assertFalse(VisualOverlay.blocksGeneral(true,VisualTuning.defaults().withEnabled(true),true));
        assertFalse(VisualOverlay.blocksGeneral(true,VisualTuning.defaults().withEnabled(true),false));
    }
    @Test public void disabledRulesAndOtherPackagesRetainStockGate() {
        assertFalse(VisualOverlay.blocksGeneral(false,hawkeye(272),false));
        assertTrue(VisualOverlay.blocksGeneral(false,hawkeye(272),true));
    }
    @Test public void descriptionShowsSavedColorRatherThanClaimingActivation() {
        assertEquals("GameCube Unlock · Hawkeye · Tiêu chuẩn",VisualOverlay.description(hawkeye(270),true));
        assertEquals("GameCube Unlock · Hawkeye · Professional",VisualOverlay.description(hawkeye(271),false));
        assertEquals("GameCube Unlock · Hawkeye · Tăng cường",VisualOverlay.description(hawkeye(272),true));
        assertEquals("GameCube Unlock · Hawkeye · ZEISS",VisualOverlay.description(hawkeye(273),false));
    }
    @Test public void demonstrationExposesGeneralFilterEvenForSavedHawkeyeOrStockBlock() {
        for(boolean eligible:new boolean[]{false,true}) for(boolean stock:new boolean[]{false,true})
            assertFalse(VisualOverlay.blocksGeneral(eligible,hawkeye(273),stock,true));
        assertTrue(VisualOverlay.blocksGeneral(true,hawkeye(273),false,false));
    }
}
