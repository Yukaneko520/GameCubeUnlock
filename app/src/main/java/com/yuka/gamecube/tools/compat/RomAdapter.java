package com.yuka.gamecube.tools.compat;

/** Generation-specific names remain here; shared policies never guess an alternate descriptor. */
public record RomAdapter(TargetBaseline baseline,boolean hideOnlyGameCenter,
                         Owner owner,Support support,Fps fps,Display display,FrameProfile frameProfile) {
    public record Owner(String type,String instance,String currentPackage,String pid) {}
    public record Support(String config,String game,String extendedGame,String evaluator,String function,
                          String scoped,String membership,String capability,String channelType,String channel,
                          String extendedValue,String commonInstance,String superTouch,String superGyro) {
        public String coupling() { return "d7.b".equals(evaluator)?"m":"l"; }
    }
    public String commonType() { return baseline.sdk()==34?"h6.a":"com.vivo.common.a"; }
    public record Fps(String capability,String controller,String controllerGetter,String enterExit,String strategy,
                      String bind,String stop,String toggle,String panelInit,String label,String container) {}
    public record Display(String prefix,String wms,String process) {}
    public record FrameProfile(String cache,String entity,String triplet,String factory,String strategy,String values) {}
    public record Enrollment(String data,String instance,String games,String master,String manager,String top,
                             String dispatch,String current,String pid,String pairing,String forward,String startup,String died,
                             String handoff,String parameterPackage,String parameterPid) {}
    public Enrollment enrollment() {
        return switch(baseline.sdk()) {
            case 34 -> new Enrollment("com.vivo.gamedaemon.x","t","r","D","com.vivo.gamedaemon.DaemonAbstractManager",
                    "q0","Z","K","J",null,"s0","d0","O","N","f","c");
            case 35 -> new Enrollment("com.vivo.gamedaemon.g0","C","A","M","com.vivo.gamedaemon.DaemonAbstractManager",
                    "I0","o0","T","S","f0","K0","t0","X","W","f","d");
            case 36 -> new Enrollment("com.vivo.gamedaemon.h0","C","A","M","com.vivo.gamedaemon.f",
                    "M0","r0","W","V","i0","P0","w0","a0","Z","e","d");
            default -> throw new IllegalArgumentException("Unsupported enrollment SDK");
        };
    }
    public record Monster(String info,String controller,String helper,String capability,String enter,String exit,
                          String disconnect,String barBind,String flag,String shift,String init,String radio) {}
    public Monster monster() {
        String base="com.vivo.gameassistant.homegui.sideslide.panels.performance.modeshift.";
        if(baseline.sdk()==34) return new Monster("e9.a","e9.k","e9.m","i0","M","L","N","u","v","w","O","p");
        return baseline.equals(TargetProfiles.OS5)
                ? new Monster("i9.a","i9.j","i9.l","M0","P","O","Q","y","w","x","S","s")
                : new Monster(base+"a",base+"k",base+"m","n1","h0","g0","i0","H","v","w","k0","B");
    }
    private static final RomAdapter OS4=new RomAdapter(TargetProfiles.OS4,true,
            new Owner("f7.o","g0","M0","L0"),
            new Support("b7.b","c7.a",null,"d7.b","f","e","k","i","e7.d","h1",null,"k","s0",null),
            null,
            new Display("com.vivo.services.rms.display.","com.vivo.services.rms.RMWms","com.vivo.services.rms.ProcessInfo"),
            new FrameProfile("j","q6.a","o6.b","t7.c","t7.h","t7.j"));
    private static final RomAdapter OS5=new RomAdapter(TargetProfiles.OS5,true,
            new Owner("b7.v","m0","Y0","X0"),
            new Support("x6.b","y6.b","y6.a","z6.b","e","f","j","i","a7.h","H1","q","l","Y0","W0"),
            new Fps("j0","com.vivo.gameassistant.framedisplay.d","A0","j2","C","F","E","J","o0","J","M"),
            new Display("com.vivo.services.rms.display.","com.vivo.services.rms.RMWms","com.vivo.services.rms.ProcessInfo"),
            new FrameProfile("i","h6.a","f6.b","w7.c","w7.h","w7.j"));
    private static final RomAdapter OS6=new RomAdapter(TargetProfiles.OS6,false,
            new Owner("com.vivo.gameassistant.o","V","O0","N0"),
            new Support("a8.b","b8.b","b8.a","c8.b","f","e","j","h","com.vivo.common.utils.l","t2","n","p","F1","D1"),
            new Fps("E0","com.vivo.gameassistant.framedisplay.e","k0","h2","S","V","U","Z","u0","I","K"),
            new Display("com.vivo.services.rrm.","com.vivo.services.rrm.RMWms","com.vivo.services.rms.appmng.ProcessInfo"),
            new FrameProfile("h","com.vivo.common.frameintersupportlist.entity.a","c7.b","c9.c","c9.h","c9.j"));
    public static RomAdapter forBaseline(TargetBaseline baseline) {
        if(TargetProfiles.OS4.equals(baseline)) return OS4;
        if(TargetProfiles.OS5.equals(baseline)) return OS5;
        if(TargetProfiles.OS6.equals(baseline)) return OS6;
        throw new IllegalArgumentException("Unknown baseline");
    }
}
