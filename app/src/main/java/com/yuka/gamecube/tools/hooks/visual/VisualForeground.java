package com.yuka.gamecube.tools.hooks.visual;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import com.yuka.gamecube.tools.hooks.Reflect;

/** Read live focused-task identity; a cloned package name is not a primary-user session. */
public final class VisualForeground {
    private VisualForeground() {}
    public static int primaryUid(ClassLoader loader,String pkg) {
        try {
            Object service=Reflect.staticCall(Class.forName("android.app.ActivityTaskManager",false,loader),"getService");
            Object task=Reflect.call(service,"getFocusedRootTaskInfo");
            if(task==null || !(Reflect.get(task,"topActivity") instanceof ComponentName top) || !pkg.equals(top.getPackageName())) return -1;
            if(!(Reflect.get(task,"topActivityInfo") instanceof ActivityInfo info) || info.applicationInfo==null
                    || !pkg.equals(info.applicationInfo.packageName)) return -1;
            int uid=info.applicationInfo.uid;
            return uid>=10000 && uid<100000?uid:-1;
        } catch(Throwable unavailable) { return -1; }
    }
}
