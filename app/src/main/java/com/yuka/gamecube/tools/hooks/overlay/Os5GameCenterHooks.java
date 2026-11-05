package com.yuka.gamecube.tools.hooks.overlay;

import android.view.View;
import android.widget.CompoundButton;
import com.yuka.gamecube.tools.hooks.ConfigListener;
import com.yuka.gamecube.tools.hooks.HookRuntime;
import com.yuka.gamecube.tools.core.ConfigSnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Legacy dock hiding; OS4 redirects through its original switch-panel/union cleanup. */
final class Os5GameCenterHooks {
    private final HookRuntime r;
    private final Field button,selection;
    private final Object center,tools;
    private final String unionEvent,click;
    private final Field switches;
    private final Method switchPanel;
    private final WeakHashMap<View,Baseline> views=new WeakHashMap<>();
    private record Baseline(int visibility,boolean enabled,boolean clickable,int accessibility) {
        static Baseline read(View v) { return new Baseline(v.getVisibility(),v.isEnabled(),v.isClickable(),v.getImportantForAccessibility()); }
        void restore(View v) {
            v.setVisibility(visibility); v.setEnabled(enabled); v.setClickable(clickable); v.setImportantForAccessibility(accessibility);
        }
    }
    Os5GameCenterHooks(HookRuntime runtime) throws Exception {
        r=runtime;
        boolean os4=r.adapter.baseline().sdk()==34;
        unionEvent=os4?"z8.d":"d9.d";click=os4?"u0":"c";
        Class<?> dock=r.type(GameCenterState.DOCK),state=r.type(GameCenterState.DOCK+"$DockClickState");
        button=OverlayAccess.field(dock,os4?"i":"h",r.type("com.vivo.gameassistant.view.TipRadioButton"));
        selection=OverlayAccess.field(dock,os4?"n":"m",state);
        center=OverlayAccess.enumValue(state,"STATE_GAME_CENTER");
        tools=OverlayAccess.enumValue(state,"STATE_SWITCH_VIEW");
        switches=os4?OverlayAccess.field(dock,"h",r.type("android.widget.RadioButton")):null;
        switchPanel=os4?r.method(GameCenterState.DOCK,"w0",View.class,int.class,boolean.class):null;
        r.method(GameCenterState.DOCK,"onAttachedToWindow"); r.method(GameCenterState.DOCK,"onDetachedFromWindow");
        r.method(GameCenterState.DOCK,"onUnionStateEvent",r.type(unionEvent));
        r.method(GameCenterState.DOCK,click,int.class,boolean.class);
        if(switchPanel!=null && switchPanel.getReturnType()!=void.class)
            throw new NoSuchMethodException("Unexpected legacy switch-panel declaration");
    }
    void install() throws Throwable {
        r.hook(GameCenterState.DOCK,"onAttachedToWindow",void.class,chain->{
            restore((View)chain.getThisObject(),false);
            Object result=chain.proceed(); after((View)chain.getThisObject()); return result;
        });
        r.hook(GameCenterState.DOCK,"onUnionStateEvent",void.class,chain->{
            View dock=(View)chain.getThisObject(); restore(dock,false);
            try { return chain.proceed(); } finally { after(dock); }
        },r.type(unionEvent));
        r.hook(GameCenterState.DOCK,click,void.class,chain->{
            if(hide()) {
                try {
                    View dock=(View)chain.getThisObject();
                    conceal(dock);
                    showTools(dock,(Integer)chain.getArg(0));
                    return null;
                } catch(Throwable failure) { r.error("legacy_game_center_click",failure); }
            }
            return chain.proceed();
        },int.class,boolean.class);
        r.hook(GameCenterState.DOCK,"onDetachedFromWindow",void.class,chain->{
            try { return chain.proceed(); } finally { restore((View)chain.getThisObject(),true); }
        });
        r.addListener(new ConfigListener() {
            @Override public void afterChange(ConfigSnapshot old,ConfigSnapshot next) {
                for(View dock:new ArrayList<>(views.keySet())) {
                    if(hide()) concealSafely(dock); else restore(dock,false);
                }
            }
        });
        if(switchPanel!=null) {
            // Private u0 may be inlined into both the saved-state and click callbacks.
            for(Method caller:new Method[]{r.method(GameCenterState.DOCK,"A0"),
                    r.method(GameCenterState.DOCK,"t0",View.class),r.method("y8.c","onClick",View.class)})
                r.log("feature=game_center stage=deopt method="+caller+" result="+r.module.deoptimize(caller));
        }
        r.log("feature=game_center stage=bindings sdk="+r.adapter.baseline().sdk()
                +" enum_resolution=runtime_name click="+click+" switch_redirect="+(switchPanel!=null));
    }
    private boolean hide() { return r.snapshot().enabled && r.snapshot().hideGameCenter; }
    private void after(View dock) {
        try {
            views.put(dock,Baseline.read((View)button.get(dock)));
            if(hide()) concealSafely(dock);
        } catch(Throwable failure) { r.error("legacy_game_center_visibility",failure); }
    }
    private void concealSafely(View dock) {
        try { if(conceal(dock)) showTools(dock,0); }
        catch(Throwable failure) { r.error("legacy_game_center_visibility",failure); }
    }
    private boolean conceal(View dock) throws Exception {
        CompoundButton entry=(CompoundButton)button.get(dock);
        views.putIfAbsent(dock,Baseline.read(entry));
        boolean selected=selection.get(dock)==center;
        if(selected) selection.set(dock,tools);
        entry.setChecked(false); entry.setVisibility(View.GONE); entry.setEnabled(false); entry.setClickable(false);
        entry.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        r.event("game_center",r.currentPackage(),"hidden","sdk="+r.adapter.baseline().sdk()
                +" visibility="+entry.getVisibility()+" was_selected="+selected);
        return selected;
    }
    private void showTools(View dock,int type) throws Throwable {
        if(switchPanel==null) return;
        CompoundButton entry=(CompoundButton)switches.get(dock);
        entry.setChecked(true);
        OverlayAccess.invoke(switchPanel,dock,entry,type,true);
        r.event("game_center",r.currentPackage(),"redirected","route=oem_switch_panel union_close=oem");
    }
    private void restore(View dock,boolean forget) {
        try {
            Baseline baseline=forget?views.remove(dock):views.get(dock);
            if(baseline!=null) {
                baseline.restore((View)button.get(dock));
                r.event("game_center",r.currentPackage(),"restored","visibility="+baseline.visibility()+" detached="+forget);
            }
        } catch(Throwable failure) { r.error("legacy_game_center_restore",failure); }
    }
}
