package com.yuka.gamecube.tools.hooks.visual;

import android.view.View;
import android.widget.TextView;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import com.yuka.gamecube.tools.core.VisualTuning;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.WeakHashMap;

/** Reuse the original unavailable-filter row for the selected Hawkeye color. */
final class VisualOverlay implements ConfigListener {
    @FunctionalInterface interface Admission { boolean eligible(String pkg) throws Throwable; }
    private static final class State {
        final String pkg;final CharSequence description;boolean owned;
        State(String pkg,CharSequence description) { this.pkg=pkg;this.description=description; }
    }
    private final HookRuntime r;
    private final VisualAccess a;
    private final Admission admission;
    private final WeakHashMap<View,State> views=new WeakHashMap<>();
    private final java.util.Map<String,Integer> ids=new java.util.HashMap<>();
    VisualOverlay(HookRuntime r,VisualAccess a,Admission admission) { this.r=r;this.a=a;this.admission=admission; }
    static boolean blocksGeneral(boolean eligible,VisualTuning tuning,boolean stock) {
        return blocksGeneral(eligible,tuning,stock,false);
    }
    static boolean blocksGeneral(boolean eligible,VisualTuning tuning,boolean stock,boolean demonstration) {
        return !demonstration && (eligible?tuning.route()==1:stock);
    }
    static String description(VisualTuning tuning,boolean vi) {
        String color=switch(tuning.colorMode()) {
            case 270 -> vi?"Tiêu chuẩn":"Standard";
            case 271 -> vi?"Chuyên nghiệp":"Professional";
            case 272 -> vi?"Tăng cường":"Enhanced";
            case 273 -> "ZEISS";
            default -> throw new IllegalArgumentException("Invalid Hawkeye color");
        };
        return "GameCube Unlock · Hawkeye · "+color;
    }
    void bind() throws Throwable {
        Class<?> type=r.type("com.vivo.gameassistant.R$id");
        for(String name:new String[]{"not_support_filter","tv_universal_filter","iv_more","rel_item",
                "sw_universal_filter","rv_name_list","ll_custom"}) {
            // OS4 opens filters in a separate page and has no inline switch/list.
            if(a.n.sdk()==34 && (name.startsWith("sw_") || name.startsWith("rv_") || name.equals("ll_custom"))) continue;
            ids.put(name,(Integer)Reflect.get(type,name));
        }
        String typeName="com.vivo.gameassistant.gamefilter.widget.GameFilterSettingView";
        r.hook(typeName,"onAttachedToWindow",void.class,chain->{
            Object result=chain.proceed();View view=(View)chain.getThisObject();
            TextView description=(TextView)find(view,"not_support_filter");
            String pkg=(String)Reflect.get(view,a.n.sdk()==36?"i":"j");
            views.putIfAbsent(view,new State(pkg,description.getText()));
            update(view,views.get(view));return result;
        });
        r.addListener(this);
    }
    private View find(View view,String name) { Integer id=ids.get(name);return id==null?null:view.findViewById(id); }
    private void update(View view,State state) throws Throwable {
        if(!state.pkg.equals(r.currentPackage())) return;
        var tuning=r.snapshot().rule(state.pkg).visual;
        boolean demonstration=r.snapshot().demonstrates(state.pkg);
        boolean eligible=admission.eligible(state.pkg),hawkeye=!demonstration && eligible && tuning.route()==1;
        if(!hawkeye && !state.owned && !demonstration) return;
        Object controller=Reflect.get(view,a.n.sdk()==36?"k":"l");
        boolean stock=(Boolean)r.original(r.method(a.n.controller(),a.n.highFps(),String.class),controller,state.pkg);
        boolean blocked=blocksGeneral(eligible,tuning,stock,demonstration);
        TextView description=(TextView)find(view,"not_support_filter");
        boolean vi="vi".equals(view.getResources().getConfiguration().getLocales().get(0).getLanguage());
        description.setText(hawkeye?description(tuning,vi):state.description);
        description.setVisibility(blocked?View.VISIBLE:View.GONE);
        View control=find(view,a.n.sdk()==34?"rel_item":"sw_universal_filter");
        control.setEnabled(!blocked);
        for(String name:new String[]{"tv_universal_filter","iv_more"}) find(view,name).setAlpha(blocked?0.3f:1f);
        if(hawkeye) for(String name:new String[]{"rv_name_list","ll_custom"}) {
            View panel=find(view,name);if(panel!=null) panel.setVisibility(View.GONE);
        }
        if(hawkeye && a.n.sdk()!=34) Reflect.call(control,"setChecked",false);
        state.owned=hawkeye || demonstration;
        if(eligible) r.event("visual",state.pkg,"overlay","route="+tuning.route()+" color="+tuning.colorMode()
                +" general_filter_blocked="+blocked+" summary_is_saved_config=true");
    }
    @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) throws Throwable {
        for(var entry:views.entrySet()) if(entry.getKey().isAttachedToWindow()) update(entry.getKey(),entry.getValue());
    }
}
