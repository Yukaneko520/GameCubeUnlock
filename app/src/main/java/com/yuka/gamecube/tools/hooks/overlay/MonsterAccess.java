package com.yuka.gamecube.tools.hooks.overlay;

import android.widget.RadioGroup;
import com.yuka.gamecube.tools.compat.RomAdapter;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.lang.reflect.Method;
import java.util.List;

final class MonsterAccess {
    static final String BAR="com.vivo.gameassistant.homegui.sideslide.panels.performance.modeshift.ModeShiftBar";
    static final String MODE="com.vivo.gameassistant.homegui.sideslide.panels.performance.modeshift.ModeType";
    static final String PANEL="com.vivo.gameassistant.homegui.sideslide.panels.performance.common.panel.PerformancePanelPanelView";
    final HookRuntime r;
    final RomAdapter.Monster api;
    final Method info,connected,helper,flag,disconnect,bar,shift;
    final List<Method> callers;
    final boolean supported;
    final String infoSetter;

    MonsterAccess(HookRuntime runtime) throws Throwable {
        r=runtime;api=r.adapter.monster();
        info=expect(api.info(),"b",r.type(api.info()));
        connected=expect(api.info(),"a",boolean.class);
        boolean os4=r.adapter.baseline().sdk()==34;
        infoSetter=os4?"e":"f";
        Method setter=expect(api.info(),infoSetter,void.class,boolean.class);
        helper=expect(api.helper(),"f",r.type(api.helper()));
        expect(api.helper(),"n",boolean.class);
        expect(api.info(),os4?"d":"e",r.type(MODE));
        flag=expect(api.helper(),api.flag(),void.class,boolean.class);
        shift=expect(api.helper(),api.shift(),void.class,r.type(MODE));
        disconnect=expect(api.controller(),api.disconnect(),void.class,boolean.class);
        bar=expect(BAR,api.barBind(),void.class,boolean.class);
        expect(api.controller(),api.enter(),void.class,String.class);
        expect(api.controller(),api.exit(),void.class);
        String common=r.adapter.support().commonInstance();
        String commonType=r.adapter.commonType();
        Object values=r.original(expect(commonType,common,r.type(commonType)),null);
        supported=Boolean.TRUE.equals(r.original(expect(commonType,api.capability(),boolean.class),values));
        String confirmation=os4?"e0":r.adapter.hideOnlyGameCenter()?"f0":"z0";
        callers=List.of(expect(api.controller(),api.init(),void.class),
                expect(api.controller(),confirmation,void.class),
                expect(api.controller(),"onModeSettingsChange",void.class,r.type("com.vivo.gameassistant.entity.ObserverEvent")),
                expect(BAR,api.radio(),void.class,RadioGroup.class,int.class),
                expect(PANEL,os4?"a":"f",void.class),shift,setter);
    }
    private Method expect(String owner,String name,Class<?> returns,Class<?>...args) throws Exception {
        Method value=r.method(owner,name,args);
        if(value.getReturnType()!=returns) throw new NoSuchMethodException("Unexpected Monster+ descriptor: "+value);
        return value;
    }
    boolean physical() throws Throwable { return Boolean.TRUE.equals(r.original(connected,r.original(info,null))); }
    void connection(Object controller,boolean connected) throws Throwable { Reflect.call(controller,api.disconnect(),connected); }
    void bind(Object view,boolean connected) throws Throwable { Reflect.call(view,api.barBind(),connected); }
}
