package com.yuka.gamecube.tools.hooks.demo;

import android.content.Context;
import android.view.View;
import android.widget.RadioButton;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Expose the shipped Q panel and its options; chip/backend capability remains unchanged. */
final class DemonstrationQChip {
    static void install(DemonstrationHooks demo) throws Throwable {
        var r=demo.r;String utils="com.vivo.common.utils.u0";
        demo.predicate(r.adapter.commonType(),"d0",false);
        demo.predicate(r.adapter.commonType(),"e0",false);
        demo.predicate(r.adapter.commonType(),"f0",true);
        var stockVisible=r.method(utils,"f",Context.class);
        demo.predicate(utils,"e",true);
        r.hook(utils,"f",boolean.class,chain->{
            Object stock=chain.proceed();return demo.active()?true:stock;
        },Context.class);
        r.hook(utils,"d",String.class,chain->{
            Object stock=chain.proceed();return demo.active()?"Q3":stock;
        });
        for(String method:new String[]{"a","b"}) demo.predicate("ta.c",method,true,Context.class,String.class);
        demo.predicate("com.vivo.common.utils.m0","f",true,Context.class,String.class);
        demo.predicate("com.vivo.common.utils.m0","e",false,Context.class,String.class);
        demo.predicate("com.vivo.common.utils.h0","j",true,Context.class,String.class);
        demo.predicate("cc.c","b",false);
        demo.predicate("com.vivo.gameassistant.utils.e0","l0",true,String.class);
        demo.predicate("com.vivo.gameassistant.superresolution.i","h1",false);
        demo.predicate("com.vivo.gameassistant.frameinterpolation.h","F0",false);
        String profile="b8.b";var constructor=r.type(profile).getDeclaredConstructor();constructor.setAccessible(true);
        for(String values:new String[]{"c9.i","c9.j"}) {
            for(String method:new String[]{"f","g"}) demo.predicate(values,method,true,String.class);
            r.hook(values,"c",r.type(profile),chain->{
                Object stock=chain.proceed();
                if(!demo.inUi()) return stock;
                Object displayOnly=constructor.newInstance();Reflect.call(displayOnly,"l",10);
                r.event("demonstration",r.currentPackage(),"q_profile_preview","index=10 persisted=false backend_profile_unchanged=true");
                return displayOnly;
            },String.class);
        }
        String sr="com.vivo.gameassistant.superresolution.m";
        demo.predicate(sr,"e",true);demo.predicate(sr,"f",true,String.class);demo.predicate(sr,"d",false);
        r.hook(sr,"b",int.class,chain->{Object stock=chain.proceed();return demo.inUi()?1:stock;});
        String base="com.vivo.gameassistant.homegui.sideslide.panels.qchip.";
        for(String view:new String[]{"QChipContentView","QSuperResolutionView","QSuperFrameView","QLightTrackView","QGameFilterView"})
            demo.scopeView(base+view);
        for(String entry:new String[]{"QSuperResolutionView:setViewEnable","QSuperFrameView:setTopViewEnable",
                "QSuperFrameView:setBottomViewEnable","QLightTrackView:setViewEnable"}) {
            String[] parts=entry.split(":");
            r.hook(base+parts[0],parts[1],void.class,chain->{
                return demo.active()?chain.proceed(new Object[]{true}):chain.proceed();
            },boolean.class);
        }
        String dock="com.vivo.gameassistant.homegui.sideslide.dock.view.EdgeDockView";
        r.hook(dock,"Q0",void.class,demo::ui,View.class);
        var serviceApi=r.adapter.owner();
        r.method(serviceApi.type(),"H0");r.method("cc.b","k0");
        r.hook("va.d0","c",void.class,chain->{
            Object kind=Reflect.call(chain.getArg(0),"o");
            if(!demo.active() || !(kind instanceof Enum<?> type) || !type.name().equals("LIGHT_TRACK")) return chain.proceed();
            Object service=Reflect.staticCall(r.type(serviceApi.type()),serviceApi.instance());
            Object ray=Reflect.call(service,"H0");
            if(ray!=null) Reflect.call(ray,"k0");
            r.event("demonstration",r.currentPackage(),"light_track_panel","controller_available="+(ray!=null));
            return null;
        },r.type(com.yuka.gamecube.tools.compat.DemonstrationLayout.ITEM),View.class);
        int qId=(Integer)Reflect.get(r.type("com.vivo.gameassistant.R$id"),"qchip_area");
        int performanceId=(Integer)Reflect.get(r.type("com.vivo.gameassistant.R$id"),"performance_area");
        Set<View> docks=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
        class Visibility {
            void refresh(View owner) throws Throwable {
                View q=owner.findViewById(qId);if(q==null) return;
                boolean show=demo.active() || Boolean.TRUE.equals(r.original(stockVisible,null,owner.getContext()));
                q.setVisibility(show?View.VISIBLE:View.GONE);
                if(!show && q instanceof RadioButton button && button.isChecked()) {
                    View performance=owner.findViewById(performanceId);if(performance!=null) performance.performClick();
                }
            }
        }
        var visibility=new Visibility();
        for(String method:new String[]{"F0","onAttachedToWindow"}) r.hook(dock,method,void.class,chain->{
            Object stock=chain.proceed();View owner=(View)chain.getThisObject();
            docks.add(owner);visibility.refresh(owner);return stock;
        });
        r.addListener(new ConfigListener() {
            @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
                synchronized(docks) { for(View owner:docks) visibility.refresh(owner); }
            }
        });
        for(String owner:new String[]{utils,"ta.c","c9.i","c9.j",sr,dock}) r.deoptimize(owner);
        r.log("feature=demonstration stage=qchip_bound native_hardware_unchanged=true");
    }
    private DemonstrationQChip() {}
}
