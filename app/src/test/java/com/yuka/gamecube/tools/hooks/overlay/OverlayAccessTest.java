package com.yuka.gamecube.tools.hooks.overlay;

import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayAccessTest {
    public enum DockState {
        a, b, d, e;
        public static String[] names() {
            return new String[]{"STATE_PERFORMANCE", "STATE_SMALL_WINDOW", "STATE_SWITCH_VIEW", "STATE_GAME_CENTER"};
        }
    }

    @Test public void resolvesRuntimeEnumNamesWithObfuscatedFieldNames() throws Exception {
        Class<?> state = obfuscatedState();
        assertNotNull(state.getField("e"));
        assertThrows(NoSuchFieldException.class, () -> state.getField("STATE_GAME_CENTER"));
        assertSame(state.getField("e").get(null), OverlayAccess.enumValue(state, "STATE_GAME_CENTER"));
        assertSame(state.getField("d").get(null), OverlayAccess.enumValue(state, "STATE_SWITCH_VIEW"));
    }

    @Test public void missingEnumNameFailsPreflight() {
        assertThrows(NoSuchFieldException.class, () -> OverlayAccess.enumValue(DockState.class, "UNKNOWN"));
    }

    @Test public void ordinaryClassesCannotMasqueradeAsEnums() {
        assertThrows(NoSuchFieldException.class, () -> OverlayAccess.enumValue(String.class, "CASE_INSENSITIVE_ORDER"));
    }

    private static Class<?> obfuscatedState() throws Exception {
        byte[] bytes;
        try (var input = DockState.class.getResourceAsStream("OverlayAccessTest$DockState.class")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(8);
        int count = Short.toUnsignedInt(buffer.getShort());
        var utf8 = new HashMap<Integer, String>();
        var strings = new HashMap<Integer, Integer>();
        for (int index = 1; index < count; index++) {
            switch (Byte.toUnsignedInt(buffer.get())) {
                case 1 -> {
                    int start = buffer.position(), length = Short.toUnsignedInt(buffer.getShort());
                    utf8.put(index, new DataInputStream(new ByteArrayInputStream(bytes, start, length + 2)).readUTF());
                    buffer.position(buffer.position() + length);
                }
                case 8 -> { strings.put(buffer.position(), Short.toUnsignedInt(buffer.getShort())); }
                case 7, 16, 19, 20 -> buffer.position(buffer.position() + 2);
                case 3, 4, 9, 10, 11, 12, 17, 18 -> buffer.position(buffer.position() + 4);
                case 5, 6 -> { buffer.position(buffer.position() + 8); index++; }
                case 15 -> buffer.position(buffer.position() + 3);
                default -> throw new AssertionError("Unexpected constant-pool tag");
            }
        }
        var names = Map.of("a", "STATE_PERFORMANCE", "b", "STATE_SMALL_WINDOW",
                "d", "STATE_SWITCH_VIEW", "e", "STATE_GAME_CENTER");
        // Match the OEM bytecode: fields stay a/b/d/e, constructor names stay semantic.
        for (var entry : strings.entrySet()) {
            String name = names.get(utf8.get(entry.getValue()));
            if (name == null) continue;
            int target = utf8.entrySet().stream().filter(item -> item.getValue().equals(name))
                    .mapToInt(Map.Entry::getKey).findFirst().orElseThrow();
            buffer.putShort(entry.getKey(), (short) target);
        }
        return new ClassLoader(OverlayAccessTest.class.getClassLoader()) {
            Class<?> loadFixture() { return defineClass(null, bytes, 0, bytes.length); }
        }.loadFixture();
    }
}
