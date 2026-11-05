package com.yuka.gamecube.tools.hooks.visual;

import java.util.Arrays;

/** Original Iris command IDs, separate from UI enum field names and Nova payloads. */
public final class VisualCommands {
    private static final int[] NEUTRAL={0,128,50,0};
    private VisualCommands() {}
    public record Command(int type,int[] values) {
        public Command {
            if(values==null || !valid(type,values)) throw new IllegalArgumentException("Invalid Iris visual command");
            values=values.clone();
        }
        @Override public int[] values() { return values.clone(); }
    }
    private static boolean valid(int type,int[] values) {
        if(type==267) return values.length==2 && values[0]==3 && values[1]>=0 && values[1]<=26;
        return type==74 && values.length==4 && values[0]>= -90 && values[0]<=90
                && values[1]>=0 && values[1]<=255 && values[2]>=0 && values[2]<=100
                && values[3]>= -64 && values[3]<=64;
    }
    public static boolean positive(int type,int[] values) {
        return valid(type,values) && (type==267?values[1]>0:!Arrays.equals(values,NEUTRAL));
    }
}
