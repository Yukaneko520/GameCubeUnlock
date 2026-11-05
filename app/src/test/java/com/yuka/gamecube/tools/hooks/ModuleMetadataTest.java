package com.yuka.gamecube.tools.hooks;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import com.yuka.gamecube.tools.BuildConfig;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual LSPosed 7854 routes system_server via system; android selects system:ui. */
public class ModuleMetadataTest {
    @Test public void appNamespaceAndJniBridgeUseTheRequestedIdentity() throws Exception {
        String expected="com.yuka.gamecube.tools";
        assertEquals(expected,BuildConfig.APPLICATION_ID);
        assertEquals(expected+".hooks",getClass().getPackageName());
        String gradle=Files.readString(Path.of("build.gradle.kts"));
        assertTrue(gradle.contains("namespace = \""+expected+"\""));
        String jni=Files.readString(Path.of("src/main/cpp/native-sr-jni.cpp"));
        for(String method:List.of("metadata","candidate","install","status","configure","selectTarget"))
            assertTrue(jni.contains("Java_"+expected.replace('.','_')+"_hooks_graphics_NativeSrBridge_"+method+"("));
        try(var sources=Files.walk(Path.of("src/main/java"))) {
            for(Path file:sources.filter(Files::isRegularFile).toList()) {
                if(file.toString().endsWith(".java") || file.toString().endsWith(".kt"))
                    assertTrue(file.toString(),java.util.regex.Pattern.compile("(?m)^package "+
                            java.util.regex.Pattern.quote(expected)+"(?:[.;]|$)").matcher(Files.readString(file)).find());
            }
        }
    }
    private InputStream metadata(String name) {
        InputStream stream=getClass().getResourceAsStream("/META-INF/xposed/"+name);
        assertNotNull("Missing packaged module resource: "+name,stream);
        return stream;
    }
    @Test public void modernSystemServerScopeUsesActualFrameworkRoutingKey() throws Exception {
        try(InputStream stream=metadata("scope.list")) {
            List<String> scopes=new String(stream.readAllBytes(),StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(s->!s.isEmpty()).toList();
            assertEquals(List.of("com.vivo.gamecube","com.vivo.gamewatch","system"),scopes);
            assertFalse(scopes.contains("android"));
        }
    }
    @Test public void modernApi102CanRequestGameScopesForNativeSr() throws Exception {
        Properties properties=new Properties();
        try(InputStream stream=metadata("module.prop")) { properties.load(stream); }
        assertEquals("102",properties.getProperty("minApiVersion"));
        assertEquals("102",properties.getProperty("targetApiVersion"));
        assertEquals("false",properties.getProperty("staticScope"));
        assertEquals("protective",properties.getProperty("exceptionMode"));
        try(InputStream stream=metadata("java_init.list")) {
            assertEquals("com.yuka.gamecube.tools.hooks.ModuleEntry",
                    new String(stream.readAllBytes(),StandardCharsets.UTF_8).trim());
        }
        try(InputStream stream=metadata("native_init.list")) {
            assertEquals("libgamecube_tuning.so",new String(stream.readAllBytes(),StandardCharsets.UTF_8).trim());
        }
    }
}
