package q6;

import com.yuka.gamecube.tools.hooks.profiles.ProfileCopiesTest;
import java.util.List;
import java.util.Map;

/** Fixture for the exact OS4 eight-argument entity, not an OEM implementation. */
public final class a extends ProfileCopiesTest.Os4Frame {
    public a(String pkg,String label,String channel,int index,boolean sdk,
             List<ProfileCopiesTest.Triple> boost,List<ProfileCopiesTest.Triple> power,
             Map<Integer,Map<String,String>> tips) {
        super(pkg,label,channel,index,sdk,boost,power,tips);
    }
}
