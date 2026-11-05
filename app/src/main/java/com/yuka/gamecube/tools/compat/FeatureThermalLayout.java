package com.yuka.gamecube.tools.compat;

import java.util.List;

/** Temperature decisions from the pinned APKs; absent features do not acquire guessed bindings. */
public final class FeatureThermalLayout {
    public enum Argument {
        NONE,INT,DOUBLE,MODE;
        public Class<?>[] types() {
            return switch(this) {
                case NONE -> new Class<?>[0];case INT -> new Class<?>[]{int.class};
                case DOUBLE -> new Class<?>[]{double.class};case MODE -> new Class<?>[]{double.class,int.class};
            };
        }
    }
    public record Decision(String feature,String owner,String hot,String cool,Argument argument,String pkg,String capability) {}
    public static List<Decision> decisions(int sdk) {
        return switch(sdk) {
            case 34 -> List.of(
                    new Decision("image","e8.e","R",null,Argument.INT,"f",null),
                    new Decision("hawkeye","q7.c","G",null,Argument.INT,null,null),
                    new Decision("pad_image","q7.f","J","I",Argument.DOUBLE,"e",null),
                    new Decision("super_hd","com.vivo.gameassistant.superhdengine.a","S","R",Argument.DOUBLE,"d",null),
                    new Decision("legacy_sr","com.vivo.gameassistant.superresolution.a","q0","l0",Argument.MODE,"k","p0"),
                    new Decision("death_replay","com.vivo.gameassistant.recorder.deathreplay.d","d0",null,Argument.NONE,null,null));
            case 35 -> List.of(
                    new Decision("image","h8.e","V",null,Argument.INT,"e",null),
                    new Decision("hawkeye","s7.c","K",null,Argument.INT,null,null),
                    new Decision("pad_image","s7.f","N","M",Argument.DOUBLE,"e",null),
                    new Decision("super_hd","com.vivo.gameassistant.superhdengine.a","Y","W",Argument.DOUBLE,"e",null),
                    new Decision("death_replay","com.vivo.gameassistant.recorder.deathreplay.r","h0",null,Argument.NONE,null,null));
            case 36 -> List.of(
                    new Decision("image","com.vivo.gameassistant.gamefilter.a","g0","e0",Argument.NONE,"e",null),
                    new Decision("hawkeye","com.vivo.gameassistant.displayenhance.c","a0",null,Argument.INT,null,null),
                    new Decision("pad_image","com.vivo.gameassistant.displayenhance.f","d0","c0",Argument.DOUBLE,"e",null),
                    new Decision("ray_tracing","cc.b","g0","f0",Argument.DOUBLE,"b",null),
                    new Decision("death_replay","com.vivo.gameassistant.recorder.deathreplay.r","x0",null,Argument.NONE,null,null));
            default -> throw new IllegalArgumentException("Unsupported feature thermal SDK: "+sdk);
        };
    }
    private FeatureThermalLayout() {}
}
