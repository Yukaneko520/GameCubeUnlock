package com.yuka.gamecube.tools.hooks.profiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileCopiesTest {
    @Test public void os4EightFieldFrameCodecPreservesSdkIndexAndDeepCopiesValues() throws Throwable {
        var boost=new ArrayList<>(List.of(new Triple(60,45,90)));
        var tips=new LinkedHashMap<Integer,Map<String,String>>();
        tips.put(60,new LinkedHashMap<>(Map.of("tip","native")));
        var donor=new Os4Frame("com.native","Label","#",7,false,boost,List.of(),tips);
        var clone=(Os4Frame)ProfileCopies.frame(donor,"com.target",Os4Frame.class.getConstructors()[0]);
        assertEquals("com.target",clone.h());assertEquals("com.native",donor.h());
        assertEquals(7,clone.e());assertFalse(clone.i());assertEquals("Label",clone.f());
        assertEquals(45,clone.a().get(0).a());assertEquals(90,clone.a().get(0).b());
        clone.a().clear();clone.c().get(60).put("tip","changed");
        assertEquals(1,donor.a().size());assertEquals("native",donor.c().get(60).get("tip"));
    }
    public static class Os4Frame {
        final String pkg,label,channel;final int index;final boolean sdk;
        final List<Triple> boost,power;final Map<Integer,Map<String,String>> tips;
        public Os4Frame(String pkg,String label,String channel,int index,boolean sdk,
                        List<Triple> boost,List<Triple> power,Map<Integer,Map<String,String>> tips) {
            this.pkg=pkg;this.label=label;this.channel=channel;this.index=index;this.sdk=sdk;
            this.boost=boost;this.power=power;this.tips=tips;
        }
        public String h(){return pkg;} public String f(){return label;} public String b(){return channel;}
        public int e(){return index;} public boolean i(){return sdk;}
        public List<Triple> a(){return boost;} public List<Triple> g(){return power;}
        public Map<Integer,Map<String,String>> c(){return tips;}
    }
    @Test public void os5FrameConstructorUsesTheSameCompleteTwelveFieldCodec() throws Throwable {
        var ctor=ProfileCopies.frameConstructor(getClass().getClassLoader(),Frame.class.getName());
        var donor=new Frame("native.game","Label","#",3,false,List.of(new Triple(60,45,90)),List.of(),Map.of(),46.5,44,0,1);
        var clone=(Frame)ProfileCopies.frame(donor,"selected.game",ctor);
        assertEquals(12,ctor.getParameterCount());assertEquals("selected.game",clone.k());assertEquals(3,clone.f());
        assertEquals(45,clone.a().get(0).a());assertEquals(90,clone.a().get(0).b());
        assertEquals(46.5,clone.j(),0);assertEquals(44,clone.c(),0);assertEquals(1,clone.l());
        assertNotSame(donor.a(),clone.a());assertNotSame(donor.a().get(0),clone.a().get(0));
    }
    @Test public void frameCopiesEveryScalarAndDeepCopiesOrderedTripletsAndTips() throws Throwable {
        List<Triple> boost = new ArrayList<>(List.of(new Triple(60, 45, 90), new Triple(30, 30, 120)));
        List<Triple> optimize = new ArrayList<>(List.of(new Triple(120, 60, 120)));
        Map<Integer, Map<String, String>> tips = new LinkedHashMap<>();
        tips.put(7, new LinkedHashMap<>(Map.of("text", "native tip")));
        Frame donor = new Frame("donor.pkg", "native label", "channel", 3, true, boost, optimize,
                tips, 46.5, 42.5, 1, 2);
        Frame clone = (Frame) ProfileCopies.frame(donor, "target.pkg", Frame.class.getConstructors()[0]);
        assertEquals("donor.pkg", donor.k());
        assertEquals("target.pkg", clone.k());
        assertEquals(donor.g(), clone.g()); assertEquals(donor.b(), clone.b());
        assertEquals(donor.f(), clone.f()); assertEquals(donor.m(), clone.m());
        assertEquals(donor.j(), clone.j(), 0); assertEquals(donor.c(), clone.c(), 0);
        assertEquals(donor.h(), clone.h()); assertEquals(donor.l(), clone.l());
        assertNotSame(boost, clone.a()); assertNotSame(optimize, clone.i());
        assertNotSame(boost.get(0), clone.a().get(0));
        assertEquals(60, clone.a().get(0).c()); assertEquals(45, clone.a().get(0).a());
        assertEquals(90, clone.a().get(0).b()); assertEquals(30, clone.a().get(1).c());
        assertEquals(120, clone.i().get(0).b());
        clone.a().clear(); clone.d().get(7).put("text", "changed");
        assertEquals(2, donor.a().size()); assertEquals("native tip", donor.d().get(7).get("text"));
    }
    @Test public void srPreservesAbsentAssociatedFrameDataAndEveryNativeField() throws Throwable {
        Sr donor = new Sr("donor.pkg", "label", "channel", 3, 0, 0, 0, 0,
                46.5, 43.5, 47.5, 44.5, -1, 0, 0, 1, "");
        Sr clone = (Sr) ProfileCopies.sr(donor, "target.pkg", Sr.class.getConstructors()[0]);
        Object[] expected = donor.values.clone(); expected[0] = "target.pkg";
        assertArrayEquals(expected, clone.values);
        assertEquals("donor.pkg", donor.p());
        assertEquals(-1, clone.f()); assertEquals(0, clone.d()); assertEquals(0, clone.e());
    }
    @Test public void absentFrameCollectionsRemainAbsent() throws Throwable {
        Frame donor = new Frame("donor.pkg", "", "", 0, false, null, null, null, 0, 0, 0, 0);
        Frame clone = (Frame) ProfileCopies.frame(donor, "target.pkg", Frame.class.getConstructors()[0]);
        assertNull(clone.a()); assertNull(clone.i()); assertNull(clone.d());
        assertFalse(clone.m());
    }
    @Test public void os5SixteenArgumentSrCodecNeverReadsAnOs6OnlyGetter() throws Throwable {
        var donor=new LegacySr("donor.pkg",null,"#",2,30,60,45,90,46.5,43.5,47.5,44.5,3,60,120,1);
        var ctor=ProfileCopies.srConstructor(getClass().getClassLoader(),LegacySr.class.getName(),true);
        assertEquals(16,ctor.getParameterCount());
        var clone=(LegacySr)ProfileCopies.sr(donor,"target.pkg",ctor);
        Object[] expected=donor.values.clone();expected[0]="target.pkg";
        assertArrayEquals(expected,clone.values);assertNull(clone.j());assertEquals("donor.pkg",donor.p());
    }

    // Fixture constructor/getter contracts mirror the verified APK entities; these are never production tuning.
    public static final class Triple {
        private final int fps, depress, target;
        public Triple(int fps, int depress, int target) { this.fps=fps; this.depress=depress; this.target=target; }
        public int c() { return fps; } public int a() { return depress; } public int b() { return target; }
    }
    public static final class Frame {
        final Object[] values;
        public Frame(String pkg, String label, String channel, int index, boolean sdk,
                     List<Triple> boost, List<Triple> optimize, Map<Integer, Map<String, String>> tips,
                     double heat, double cool, int monster, int temperature) {
            values=new Object[]{pkg,label,channel,index,sdk,boost,optimize,tips,heat,cool,monster,temperature};
        }
        public String k(){return (String)values[0];} public String g(){return (String)values[1];}
        public String b(){return (String)values[2];} public int f(){return (Integer)values[3];}
        public boolean m(){return (Boolean)values[4];}
        @SuppressWarnings("unchecked") public List<Triple> a(){return (List<Triple>)values[5];}
        @SuppressWarnings("unchecked") public List<Triple> i(){return (List<Triple>)values[6];}
        @SuppressWarnings("unchecked") public Map<Integer, Map<String, String>> d(){return (Map<Integer, Map<String,String>>)values[7];}
        public double j(){return (Double)values[8];} public double c(){return (Double)values[9];}
        public int h(){return (Integer)values[10];} public int l(){return (Integer)values[11];}
    }
    public static class Sr {
        final Object[] values;
        public Sr(String pkg, String label, String channel, int state, int depress, int target,
                  int monsterDepress, int monsterTarget, double heat, double cool, double extremeHeat,
                  double extremeCool, int index, int highDepress, int highTarget, int monster, String ultra) {
            values=new Object[]{pkg,label,channel,state,depress,target,monsterDepress,monsterTarget,
                    heat,cool,extremeHeat,extremeCool,index,highDepress,highTarget,monster,ultra};
        }
        public String p(){return (String)values[0];} public String j(){return (String)values[1];}
        public String a(){return (String)values[2];} public int q(){return (Integer)values[3];}
        public int d(){return (Integer)values[4];} public int e(){return (Integer)values[5];}
        public int k(){return (Integer)values[6];} public int l(){return (Integer)values[7];}
        public double n(){return (Double)values[8];} public double b(){return (Double)values[9];}
        public double o(){return (Double)values[10];} public double c(){return (Double)values[11];}
        public int f(){return (Integer)values[12];} public int g(){return (Integer)values[13];}
        public int h(){return (Integer)values[14];} public int m(){return (Integer)values[15];}
        public String r(){return (String)values[16];}
    }
    public static final class LegacySr extends Sr {
        public LegacySr(String pkg,String label,String channel,int state,int depress,int target,int monsterDepress,int monsterTarget,
                        double heat,double cool,double extremeHeat,double extremeCool,int index,int highDepress,int highTarget,int monster) {
            super(pkg,label,channel,state,depress,target,monsterDepress,monsterTarget,heat,cool,extremeHeat,extremeCool,index,highDepress,highTarget,monster,null);
        }
        @Override public String r() {throw new AssertionError("OS5 has no r() getter");}
    }
}
