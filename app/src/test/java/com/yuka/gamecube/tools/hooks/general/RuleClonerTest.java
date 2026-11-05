package com.yuka.gamecube.tools.hooks.general;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RuleClonerTest {
    @Test public void os4ClonesBaseRulesWithoutRequiringAnAbsentExtendedEntity() throws Throwable {
        var cloner=new RuleCloner(Game.class,null,Coupling.class,null);
        var game=new Game("touch","white","com.native","Label","#",false,3,false,90);
        var copy=(Coupling)cloner.cloneCoupling(new Coupling(game,List.of(),List.of()),"com.target","#");
        assertEquals("com.target",copy.c().g());
        assertEquals(3,copy.c().e());
        assertEquals("com.native",game.g());
        assertThrows(IllegalArgumentException.class,()->cloner.cloneCoupling(
                new Coupling(new UnknownGame(),List.of(),List.of()),"com.target","#"));
    }
    private RuleCloner cloner() throws Exception {
        return new RuleCloner(Game.class, ExtraGame.class, Coupling.class);
    }

    @Test public void onlyLocalPackageAndChannelChange() throws Throwable {
        Game source = new Game("touch", "white", "com.native.game", "Label", "vivo", true, 7, true, 120);
        List<Object> hardware = new ArrayList<>(List.of(new Object()));
        List<Object> software = new ArrayList<>(List.of(new Object()));
        Coupling original = new Coupling(source, hardware, software);
        Coupling cloned = (Coupling) cloner().cloneCoupling(original, "com.selected.game", "#");
        assertNotSame(original, cloned);
        assertNotSame(source, cloned.c());
        assertSame(hardware, cloned.a()); assertSame(software, cloned.b());
        assertEquals("com.native.game", source.g()); assertEquals("vivo", source.a());
        assertEquals("com.selected.game", cloned.c().g()); assertEquals("#", cloned.c().a());
        assertEquals(source.c(), cloned.c().c()); assertEquals(source.h(), cloned.c().h());
        assertEquals(source.f(), cloned.c().f()); assertEquals(source.i(), cloned.c().i());
        assertEquals(source.e(), cloned.c().e()); assertEquals(source.j(), cloned.c().j());
        assertEquals(source.b(), cloned.c().b());
    }

    @Test public void recognizedSubtypeKeepsItsPayload() throws Throwable {
        ExtraGame source = new ExtraGame("touch", "white", "com.native", "Label", "*", false, 2, false, 60, 99);
        Coupling cloned = (Coupling) cloner().cloneCoupling(new Coupling(source, List.of(), List.of()), "com.target", "*");
        assertEquals(ExtraGame.class, cloned.c().getClass());
        assertEquals(99, ((ExtraGame) cloned.c()).n());
    }

    @Test public void unknownSubtypeCannotLoseMetadataSilently() throws Throwable {
        UnknownGame source = new UnknownGame();
        try {
            cloner().cloneCoupling(new Coupling(source, List.of(), List.of()), "com.target", "#");
            fail("unknown subtype accepted");
        } catch (IllegalArgumentException expected) { assertEquals("unknown native game subtype", expected.getMessage()); }
    }

    // Fixture getter/constructor descriptors mirror the audited b8.b, b8.a and pojo.a values.
    public static class Game {
        private final String function, type, pkg, label, channel;
        private final boolean oversea, sdk;
        private final int index, frame;
        public Game(String function, String type, String pkg, String label, String channel,
                    boolean oversea, int index, boolean sdk, int frame) {
            this.function = function; this.type = type; this.pkg = pkg; this.label = label;
            this.channel = channel; this.oversea = oversea; this.index = index; this.sdk = sdk; this.frame = frame;
        }
        public String c() { return function; } public String h() { return type; }
        public String g() { return pkg; } public String f() { return label; }
        public String a() { return channel; } public boolean i() { return oversea; }
        public int e() { return index; } public boolean j() { return sdk; } public int b() { return frame; }
    }
    public static class ExtraGame extends Game {
        private final int extra;
        public ExtraGame(String function, String type, String pkg, String label, String channel,
                         boolean oversea, int index, boolean sdk, int frame, int extra) {
            super(function, type, pkg, label, channel, oversea, index, sdk, frame); this.extra = extra;
        }
        public int n() { return extra; }
    }
    public static class UnknownGame extends Game {
        UnknownGame() { super("touch", "white", "com.native", "Label", "*", false, 2, false, 60); }
    }
    public static class Coupling {
        private final Game game;
        private final List<?> hardware, software;
        public Coupling(Game game, List<?> hardware, List<?> software) {
            this.game = game; this.hardware = hardware; this.software = software;
        }
        public Game c() { return game; } public List<?> a() { return hardware; } public List<?> b() { return software; }
    }
}
