package com.yuka.gamecube.tools.compat;

/** Exact controller/helper symbols from the pinned GameCube APKs. */
public record VisualLayout(int sdk,String controller,String strategy,String helper,String instance,
                           String iris,String flag,String writer,String open,String close,String foreground,
                           String highFps,String bean,String destroy,String light,String night) {
    public static VisualLayout forSdk(int sdk) {
        return switch(sdk) {
            case 34 -> new VisualLayout(34,"e8.e","f8.c","v6.b","e","h","l","k","X","C","N","Q","K","W",null,null);
            case 35 -> new VisualLayout(35,"h8.e","i8.c","o6.b","f","i","m","l","a0","G","S","U","P","Z","y0","H0");
            case 36 -> new VisualLayout(36,"com.vivo.gameassistant.gamefilter.a","l9.c","j7.b","f","i","m","l",
                    "h0","S","a0","d0","X","b","X0","h1");
            default -> throw new IllegalArgumentException("Unsupported visual SDK: "+sdk);
        };
    }
}
