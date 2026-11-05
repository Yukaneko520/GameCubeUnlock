package com.yuka.gamecube.tools.hooks.backend;

import android.hardware.SensorAdditionalInfo;
import android.os.Message;
import com.yuka.gamecube.tools.hooks.Reflect;
import java.util.Arrays;

/** Carries request IDs across the OEM handler/factory queues; no log implies physical improvement. */
final class SuperGyroTrace {
    private final SuperGyroHooks owner;
    private final SuperControlRuntime r;
    private final ThreadLocal<ControlTickets.Ticket> producing;
    private final ThreadLocal<ControlTickets.Ticket> consuming=new ThreadLocal<>();
    private final ControlTickets messages=new ControlTickets(),factory=new ControlTickets();
    SuperGyroTrace(SuperGyroHooks owner,ThreadLocal<ControlTickets.Ticket> producing) {
        this.owner=owner;r=owner.r;this.producing=producing;
    }
    void install() {
        try { delay(); } catch(Throwable failure) { r.error("gyro_delay_trace_unavailable",failure); }
        try { shake(); } catch(Throwable failure) { r.error("gyro_factory_trace_unavailable",failure); }
        try { touchSystem(); } catch(Throwable failure) { r.error("touch_system_trace_unavailable",failure); }
    }
    private void delay() throws Throwable {
        String base="com.vivo.sensor.NativeNotification.";
        r.hook(base+"GyroNotification","sendGyroMessage",void.class,chain->{
            var ticket=producing.get();
            if(ticket!=null && (Integer)chain.getArg(0)==4 && !messages.put(chain.getArg(1),ticket)) {
                event(ticket,"message_queue_full","positive_owned_command_suppressed="+(ticket.owned()&&ticket.gear()>0));
                if(ticket.owned()&&ticket.gear()>0) return null;
            }
            return chain.proceed();
        },int.class,int[].class);
        r.hook(base+"NativeNotificationManager$NotifyHandler","handleMessage",void.class,chain->{
            Message message=(Message)chain.getArg(0);
            var ticket=message.what==5 && message.arg1==4?messages.take(message.obj):null;
            var previous=consuming.get();
            if(ticket==null)consuming.remove();else consuming.set(ticket);
            try { return chain.proceed(); }
            finally { if(previous==null)consuming.remove();else consuming.set(previous); }
        },Message.class);
        r.hook("android.hardware.SystemSensorManager","setOperationParameterImpl",boolean.class,chain->{
            var ticket=consuming.get();
            if(ticket==null) return chain.proceed();
            SensorAdditionalInfo info=(SensorAdditionalInfo)chain.getArg(0);
            if(info==null || info.type!=0x10000004) return chain.proceed();
            synchronized(r.gate) {
                boolean skip=ControlTickets.suppress(ticket,owner.active(ticket.pkg()));
                Object result=skip?false:chain.proceed();
                event(ticket,skip?"sensor_stale_suppressed":"sensor_returned","args="+Arrays.toString(info.intValues)+
                        " api_success="+result+" side_effects_not_determined_by_status=true physical_effect=unverified");
                return result;
            }
        },SensorAdditionalInfo.class);
        try { nativeOperation(); } catch(Throwable failure) { r.error("gyro_jni_status_trace_unavailable",failure); }
        for(var method:new java.lang.reflect.Method[]{r.method(base+"GyroNotification","notifyGyroPromotionParam",int.class,int.class),
                r.method(base+"NativeNotificationImpl","notifyGyroPromotionParam",int.class,int.class),
                r.method(base+"NativeNotificationManager$NotifyHandler","handleMessage",Message.class)})
            r.log("superGyro","","trace_deopt","method="+method+" result="+r.module.deoptimize(method));
    }
    private void nativeOperation() throws Throwable {
        String type="android.hardware.SystemSensorManager";
        r.hook(type,"nativeSetOperationParameter",int.class,chain->{
            Object result=chain.proceed();var ticket=consuming.get();
            if(ticket!=null&&(Integer)chain.getArg(2)==0x10000004)
                event(ticket,"native_operation_returned","sensor_handle="+chain.getArg(1)+
                        " type=0x10000004 args="+Arrays.toString((int[])chain.getArg(4))+
                        " native_return="+result+" side_effects_not_determined_by_status=true physical_effect=unverified");
            return result;
        },long.class,int.class,int.class,float[].class,int[].class);
        var caller=r.method(type,"setOperationParameterImpl",SensorAdditionalInfo.class);
        r.log("superGyro","","jni_status_deopt","method="+caller+" result="+r.module.deoptimize(caller));
    }
    private void shake() throws Throwable {
        String base="com.vivo.sensor.sensoroperate.";
        Class<?> resultType=r.type(base+"SensorTestResultV2");
        r.hook(base+"FactoryOperationImpl","vivoSensorTestV2",void.class,chain->{
            var ticket=producing.get();
            if(ticket!=null && (Integer)chain.getArg(0)==352 && !factory.put(chain.getArg(1),ticket)) {
                event(ticket,"factory_queue_full","positive_owned_command_suppressed="+(ticket.owned()&&ticket.gear()>0));
                if(ticket.owned()&&ticket.gear()>0) return null;
            }
            return chain.proceed();
        },int.class,resultType,int[].class);
        r.hook(base+"VivoSensorTest","vivoSensorTest",int.class,chain->{
            Object payload=chain.getArg(1);var ticket=(Integer)chain.getArg(0)==352?factory.attempt(payload):null;
            if(ticket==null) return chain.proceed();
            synchronized(r.gate) {
                boolean skip=ControlTickets.suppress(ticket,owner.active(ticket.pkg()));
                int result=skip?-1:(Integer)chain.proceed();
                // Keep a rejected ticket through all OEM retries; otherwise the second retry would escape the guard.
                if(result>=0) factory.take(payload);
                event(ticket,skip?"factory_stale_suppressed":"factory_returned","command=352 args="+
                        Arrays.toString((int[])chain.getArg(2))+" native_return="+result+
                        " success="+Reflect.get(payload,"mSuccess")+" info="+bounded(Reflect.get(payload,"mInfo"))+
                        " physical_effect=unverified");
                return result;
            }
        },int.class,resultType,int[].class,int.class);
        var caller=r.method(base+"FactoryManager","sendFactoryCmdV2",r.type(base+"FactoryDataV2"));
        r.log("superGyro","","trace_deopt","method="+caller+" result="+r.module.deoptimize(caller));
    }
    private void touchSystem() throws Throwable {
        String prefix=android.os.Build.VERSION.SDK_INT==35?"com.vivo.services.rms.display.":"com.vivo.services.rrm.";
        r.hook(prefix+"SfUtils","setAppFpsInfo",void.class,chain->{
            Object result=chain.proceed();
            try {
                BackendPolicy policy=r.policy;
                if(!policy.verified || !policy.snapshot.enabled || policy.snapshot.apps.entrySet().stream()
                        .noneMatch(e->policy.control(e.getKey(),"superTouch"))) return result;
                int pid=(Integer)chain.getArg(0);
                int uid=(Integer)Reflect.staticCall(android.os.Process.class,"getUidForPid",pid);
                boolean selected=policy.snapshot.apps.keySet().stream()
                        .anyMatch(pkg->policy.control(pkg,"superTouch")&&Integer.valueOf(uid).equals(policy.installedUid(pkg)));
                if(selected||uid<0) r.log("superTouch","","sf_request_returned","pid="+pid+" uid="+uid+
                        " app_fps="+chain.getArg(1)+" limit_fps="+chain.getArg(2)+" flags=0x"+
                        Integer.toHexString((Integer)chain.getArg(3))+" transaction=110055 attribution="+
                        (selected?"selected_uid":"pid_only_uid_unavailable")+" native_ack=unavailable");
            } catch(Throwable failure) { r.error("touch_system_trace",failure); }
            return result;
        },int.class,int.class,int.class,int.class);
    }
    private void event(ControlTickets.Ticket ticket,String stage,String detail) {
        r.log("superGyro",ticket.pkg(),stage,"id="+ticket.id()+" request_revision="+ticket.revision()+
                " branch="+ticket.branch()+" requested_gear="+ticket.gear()+" current_pkg="+owner.current()+" "+detail);
    }
    private static String bounded(Object value) {
        String text=String.valueOf(value).replace('\n',' ').replace('\r',' ');
        return text.length()>256?text.substring(0,256):text;
    }
}
