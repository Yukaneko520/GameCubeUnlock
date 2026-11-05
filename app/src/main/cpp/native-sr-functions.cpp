#include "native-sr-state.h"

namespace gamecube {
using GetConfig=GameConfig(*)();
using RenderInit=bool(*)(void*,int,float,float,float);
static GetConfig originalConfig=nullptr;
static RenderInit originalRender=nullptr;
static const NativeTarget* activeTarget=nullptr;
static GameConfig getConfig() {
    auto original=__atomic_load_n(&originalConfig,__ATOMIC_ACQUIRE);
    if(!original) return {};
    GameConfig value=original(); Policy p=policy();
    GameConfig result=overlay(value,p);
    static int64_t last=-1;
    static int lastEnabled=-1;
    bool changed=__atomic_exchange_n(&last,p.revision,__ATOMIC_RELAXED)!=p.revision;
    changed|=__atomic_exchange_n(&lastEnabled,p.enabled,__ATOMIC_RELAXED)!=p.enabled;
    if(changed)
        log("stage=parameters baseline=%s enabled=%d old_width=%u new_width=%u old_algorithm=%u new_algorithm=%u old_flags=%u new_flags=%u revision=%lld",
                activeTarget?activeTarget->name:"unknown",p.enabled,value.width,result.width,value.algorithm,result.algorithm,
                value.flags,result.flags,static_cast<long long>(p.revision));
    return result;
}
static bool renderInit(void* self,int algorithm,float sharpness,float dark,float white) {
    auto original=__atomic_load_n(&originalRender,__ATOMIC_ACQUIRE);
    if(!original) return false;
    Policy p=policy();
    log("stage=renderer_parameters baseline=%s enabled=%d algorithm=%d old=%.3f/%.3f/%.3f new=%.3f/%.3f/%.3f revision=%lld",
            activeTarget?activeTarget->name:"unknown",p.enabled,algorithm,sharpness,dark,white,
            p.enabled?p.sharpness:sharpness,p.enabled?p.dark:dark,p.enabled?p.white:white,static_cast<long long>(p.revision));
    if(p.enabled) {
        sharpness=p.sharpness; dark=p.dark; white=p.white;
    }
    bool result=original(self,algorithm,sharpness,dark,white);
    log("stage=renderer_init enabled=%d result=%d revision=%lld",p.enabled,result,static_cast<long long>(p.revision));
    return result;
}
bool installFunctions(void* base,const NativeTarget& contract) {
    activeTarget=&contract;
    void* config=static_cast<uint8_t*>(base)+contract.getter;
    void* render=static_cast<uint8_t*>(base)+contract.renderer;
    int first=hookFunction(config,reinterpret_cast<void*>(getConfig),reinterpret_cast<void**>(&originalConfig));
    if(first!=0) { log("stage=install_config failed=%d",first); return false; }
    int second=hookFunction(render,reinterpret_cast<void*>(renderInit),reinterpret_cast<void**>(&originalRender));
    // Never unhook a trampoline while renderer threads might still be using it. Partial families stay passthrough.
    if(second!=0) { log("stage=install_render failed=%d config_hook=passthrough",second); return false; }
    return true;
}
}
