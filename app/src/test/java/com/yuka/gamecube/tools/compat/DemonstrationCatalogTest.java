package com.yuka.gamecube.tools.compat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DemonstrationCatalogTest {
    private JSONObject asset(String name) throws Exception {
        Path path=Path.of("src/main/assets",name);
        if(!Files.exists(path)) path=Path.of("app/src/main/assets",name);
        return new JSONObject(Files.readString(path));
    }
    @Test public void eachRomHasTheCoreLockedEntriesAndItsOwnNativeSymbols() throws Exception {
        var catalog=asset("demonstration-ui.json");
        for(int sdk:new int[]{34,35,36}) {
            var rows=catalog.getJSONObject(Integer.toString(sdk));
            for(String entry:new String[]{"GAME_MANIPULATION","GAME_CUSTOM_SOUND","FRAME_INTERPOLATION",
                    "GAME_SUPER_RESOLUTION","VISUAL_ENHANCEMENT","PERIPHERAL_SETTINGS"})
                assertTrue(sdk+":"+entry,rows.has(entry));
            for(var keys=rows.keys();keys.hasNext();) {
                String name=keys.next();assertTrue(name.matches("[A-Z0-9_]+"));
                assertTrue(rows.getJSONObject(name).getString("title").matches("[a-z0-9_]+"));
            }
        }
        assertTrue(catalog.getJSONObject("36").has("LIGHT_TRACK"));
        assertFalse(catalog.getJSONObject("36").has("GAME_SUPER_HD_ENGINE"));
        assertTrue(catalog.getJSONObject("34").has("GAME_SUPER_HD_ENGINE"));
        assertEquals("a",DemonstrationLayout.forSdk(34).create());
        assertEquals("c",DemonstrationLayout.forSdk(35).create());
        assertEquals("h",DemonstrationLayout.forSdk(36).create());
    }
    @Test public void capabilityCatalogContainsOnlyNamedSupportPredicatesAndExactParameters() throws Exception {
        var catalog=asset("demonstration-capabilities.json");
        for(int sdk:new int[]{34,35,36}) {
            var rows=catalog.getJSONArray(Integer.toString(sdk));var seen=new HashSet<String>();
            assertTrue(rows.length()>0);
            for(int i=0;i<rows.length();i++) {
                var row=rows.getJSONObject(i);assertTrue(row.getString("label").startsWith("isSupport"));
                assertTrue(seen.add(row.getString("method")+row.getJSONArray("parameters")));
                for(int j=0;j<row.getJSONArray("parameters").length();j++)
                    assertTrue(java.util.Set.of("Context","String").contains(row.getJSONArray("parameters").getString(j)));
            }
        }
    }
}
