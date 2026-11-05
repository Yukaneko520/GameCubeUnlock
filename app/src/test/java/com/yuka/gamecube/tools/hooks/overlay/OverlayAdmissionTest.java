package com.yuka.gamecube.tools.hooks.overlay;

import com.yuka.gamecube.tools.core.AppRule;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayAdmissionTest {
    private ConfigSnapshot config(boolean enabled,boolean app) {
        return new ConfigSnapshot(enabled,0,false,1,Map.of("com.example.game",AppRule.defaults().withEnabled(app)));
    }
    @Test public void enabledAppNeedsNoIndividualFeatureForOverlayAdmission() {
        var c=config(true,true);
        assertTrue(OverlayAdmission.selected(c,"com.example.game"));
        assertEquals(List.of("com.stock.game","com.example.game"),OverlayAdmission.extend(List.of("com.stock.game"),c,null));
    }
    @Test public void returnedExtensionDoesNotMutateOrDuplicateStockSelection() {
        var stock=new java.util.ArrayList<>(List.of("com.stock.game"));
        var c=config(true,true);
        var extended=OverlayAdmission.extend(stock,c,null);
        assertEquals(List.of("com.stock.game"),stock);
        assertSame(extended,OverlayAdmission.extend(extended,c,null));
    }
    @Test public void masterAndAppOffWithdrawOnlyModuleMembership() {
        for(var c:List.of(config(false,true),config(true,false))) {
            var stock=List.of("com.stock.game");
            assertSame(stock,OverlayAdmission.extend(stock,c,null));
            assertFalse(OverlayAdmission.selected(c,"com.example.game"));
        }
        var c=config(false,true);
        var stock=List.of("com.example.game");
        assertSame(stock,OverlayAdmission.extend(stock,c,null));
    }
    @Test public void emptyUninitializedStockListCanStillAdmitAnEnabledApp() {
        assertEquals(List.of("com.example.game"),OverlayAdmission.extend(null,config(true,true),null));
        assertNull(OverlayAdmission.extend(null,config(false,true),null));
    }
    @Test public void secondaryUserExtensionIsExcludedWithoutRemovingStockSupport() {
        var c=config(true,true);
        assertEquals(List.of(),OverlayAdmission.extend(List.of(),c,"com.example.game"));
        var stock=List.of("com.example.game");
        assertSame(stock,OverlayAdmission.extend(stock,c,"com.example.game"));
    }
    @Test public void queuedHandoffMustStillMatchEnabledForegroundPrimarySession() {
        String pkg="com.example.game";var c=config(true,true);
        assertTrue(OverlayAdmission.handoff(c,pkg,pkg,123,123,0,true));
        assertFalse(OverlayAdmission.handoff(config(false,true),pkg,pkg,123,123,0,true));
        assertFalse(OverlayAdmission.handoff(config(true,false),pkg,pkg,123,123,0,true));
        assertFalse(OverlayAdmission.handoff(c,pkg,"com.other.game",123,123,0,true));
        assertFalse(OverlayAdmission.handoff(c,pkg,pkg,123,456,0,true));
        assertFalse(OverlayAdmission.handoff(c,pkg,pkg,-1,-1,0,true));
        assertFalse(OverlayAdmission.handoff(c,pkg,pkg,123,123,99900000,true));
        assertFalse(OverlayAdmission.handoff(c,pkg,pkg,123,123,10,true));
        assertFalse(OverlayAdmission.handoff(c,pkg,pkg,123,123,0,false));
    }
    @Test public void primaryAdmissionAcceptsOemTaskPrefixOrPrimaryFullUid() {
        assertTrue(OverlayAdmission.primaryUser(0));assertTrue(OverlayAdmission.primaryUser(1000));
        assertTrue(OverlayAdmission.primaryUser(10234));
        for(int value:new int[]{-1,10,999,100000,1010234,99900000}) assertFalse(OverlayAdmission.primaryUser(value));
    }
}
