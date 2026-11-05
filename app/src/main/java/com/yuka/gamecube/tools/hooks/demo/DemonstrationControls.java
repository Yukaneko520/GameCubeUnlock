package com.yuka.gamecube.tools.hooks.demo;

import android.content.Context;
import android.view.View;
import com.yuka.gamecube.tools.compat.SuperControlLayout;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.ArrayList;
import java.util.List;

/** Native control tabs are mutually gated; expose their union without changing stored tuning. */
final class DemonstrationControls {
    static void install(DemonstrationHooks demo) throws Throwable {
        var r=demo.r;int sdk=r.adapter.baseline().sdk();var api=SuperControlLayout.forSdk(sdk);
        String view="com.vivo.gameassistant.gamemanipulation.widget.GameManipulationView";
        demo.predicate(api.touchHelper(),"f",true,String.class);
        if(api.gyroHelper()!=null) demo.predicate(api.gyroHelper(),"h",true,String.class);
        demo.predicate(api.ordinaryController(),"k",true,String.class);
        if(sdk==34) {
            String superTouch="com.vivo.gameassistant.gamemanipulation.widget.SuperTouchView";
            var constructor=r.type(superTouch).getDeclaredConstructor(Context.class);constructor.setAccessible(true);
            demo.predicate("com.vivo.gameassistant.gamemanipulation.widget.TouchAdjustmentView","q",true);
            r.hook(view,"d",void.class,chain->{
                Object stock=chain.proceed();if(!demo.active()) return stock;
                View owner=(View)chain.getThisObject();
                if(Reflect.get(owner,"i")==null) Reflect.set(owner,"i",constructor.newInstance(owner.getContext()));
                for(String id:new String[]{"tv_tab_super_touch","tv_tab_cdm","ll_logo_layout"}) {
                    int value=(Integer)Reflect.get(r.type("com.vivo.gameassistant.R$id"),id);
                    View child=owner.findViewById(value);if(child!=null) child.setVisibility(View.VISIBLE);
                }
                return stock;
            });
        } else {
            var inTabs=ThreadLocal.withInitial(()->false);
            r.hook(api.ordinaryController(),"j",boolean.class,chain->{
                Object stock=chain.proceed();return demo.inUi() && inTabs.get()?false:stock;
            },String.class);
            String tab="com.vivo.gameassistant.gamemanipulation.widget.e";
            var constructor=r.type(tab).getDeclaredConstructor(String.class,int.class);constructor.setAccessible(true);
            r.hook(view,"getTabData",List.class,chain->{
                boolean before=inTabs.get();inTabs.set(true);
                Object stock;
                try { stock=demo.ui(chain); }
                finally { if(!before) inTabs.remove();else inTabs.set(true); }
                if(!demo.active()) return stock;
                @SuppressWarnings("unchecked") var tabs=new ArrayList<>((List<Object>)stock);
                View owner=(View)chain.getThisObject();Context context=owner.getContext();
                int title=(Integer)Reflect.get(r.type("com.vivo.gameassistant.R$string"),"manipulation_match_parameter");
                tabs.add(constructor.newInstance(context.getString(title),1));
                r.event("demonstration",r.currentPackage(),"control_tabs","count="+tabs.size()+" saved_tuning_unchanged=true");
                return tabs;
            });
        }
        demo.scopeView(view);
        for(String name:new String[]{"TouchAdjustmentView","SuperTouchView"})
            demo.scopeView("com.vivo.gameassistant.gamemanipulation.widget."+name);
        if(sdk!=34) demo.scopeView("com.vivo.gameassistant.gamemanipulation.widget.SuperGyroscopeView");
        r.log("feature=demonstration stage=controls_bound sdk="+sdk);
    }
    private DemonstrationControls() {}
}
