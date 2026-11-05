#include "native-sr-state.h"
#include <dlfcn.h>
#include <link.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <pthread.h>

namespace gamecube {
HookFunction hookFunction=nullptr;
static pthread_mutex_t mutex=PTHREAD_MUTEX_INITIALIZER;
static Candidate loaded;
static void* installedBase=nullptr;
static int state=-1;
static const NativeTarget* selected=nullptr;
static int installMemory(Candidate c);
static bool matches(const char* name) {
    if(!name) return false;
    size_t n=strlen(name); const char* suffix="libVSR.so";
    return n>=9 && !strcmp(name+n-9,suffix) && (n==9 || name[n-10]=='/');
}
static void record(void* base,const char* path) {
    if(!base || !matches(path) || strlen(path)>=sizeof(loaded.path)) return;
    pthread_mutex_lock(&mutex);
    if(installedBase && installedBase!=base) state=-5;
    loaded.base=base; snprintf(loaded.path,sizeof(loaded.path),"%s",path);
    pthread_mutex_unlock(&mutex);
}
static void onLibraryLoaded(const char* name,void* handle) {
    if(!matches(name) || !handle) return;
    void* entry=dlsym(handle,"getVSRBufferParams"); Dl_info info{};
    if(entry && dladdr(entry,&info)) {
        record(info.dli_fbase,info.dli_fname);
        installMemory(candidate()); // No Java/file I/O under the loader callback.
    }
}
Candidate candidate() { pthread_mutex_lock(&mutex); Candidate c=loaded; pthread_mutex_unlock(&mutex); return c; }
int status() { pthread_mutex_lock(&mutex); int s=state; pthread_mutex_unlock(&mutex); return s; }
bool selectTarget(int sdk) {
    const auto* target=nativeTarget(sdk);
    pthread_mutex_lock(&mutex);
    bool allowed=target && (!selected || selected==target);
    if(allowed) selected=target;
    pthread_mutex_unlock(&mutex);
    if(allowed) log("stage=target_selected baseline=%s mode=tuning",target->name);
    return allowed;
}
void discoverLoaded() {
    if(candidate().base) return;
    dl_iterate_phdr([](dl_phdr_info* info,size_t,void*)->int {
        if(!matches(info->dlpi_name)) return 0;
        record(reinterpret_cast<void*>(info->dlpi_addr),info->dlpi_name); return 1;
    },nullptr);
    if(candidate().base) installMemory(candidate());
}
int install(const char* path,const char* sha) {
    pthread_mutex_lock(&mutex);
    Candidate c=loaded;
    if(!c.base || strcmp(path,c.path)) { pthread_mutex_unlock(&mutex); return 0; }
    if(!selected) { pthread_mutex_unlock(&mutex); return 0; }
    if(strcmp(sha,selected->sha)) { state=-4; pthread_mutex_unlock(&mutex); return -4; }
    pthread_mutex_unlock(&mutex);
    return installMemory(c);
}
static int installMemory(Candidate c) {
    if(!c.base) return 0;
    pthread_mutex_lock(&mutex);
    const NativeTarget* target=selected;
    if(!target) { pthread_mutex_unlock(&mutex); return 0; }
    if(state==1 || state==2 || state==-3 || state==-5 || state==-4) {
        int s=state; pthread_mutex_unlock(&mutex); return s;
    }
    if(!hookFunction) { state=-1; pthread_mutex_unlock(&mutex); return -1; }
    state=2; pthread_mutex_unlock(&mutex);
    timespec begin{},end{}; clock_gettime(CLOCK_MONOTONIC,&begin);
    bool identity=targetIdentity(c.base,*target); clock_gettime(CLOCK_MONOTONIC,&end);
    log("stage=memory_identity baseline=%s result=%d duration_ms=%lld",target->name,identity,
            static_cast<long long>((end.tv_sec-begin.tv_sec)*1000+(end.tv_nsec-begin.tv_nsec)/1000000));
    if(!identity) {
        pthread_mutex_lock(&mutex); state=-2; pthread_mutex_unlock(&mutex);
        log("stage=identity_rejected build_id_or_prologue_mismatch"); return -2;
    }
    bool rendererExists=__atomic_load_n(reinterpret_cast<void**>(static_cast<uint8_t*>(c.base)+target->rendererPointer),__ATOMIC_ACQUIRE)!=nullptr;
    log("stage=install_timing renderer_exists=%d restart_for_complete_parameters=%d",rendererExists,rendererExists);
    bool ready=installFunctions(c.base,*target);
    pthread_mutex_lock(&mutex);
    installedBase=c.base;
    state=loaded.base!=c.base?-5:(ready?1:-3);
    int result=state; pthread_mutex_unlock(&mutex);
    log("stage=install result=%d immutable_load_segments_sha256=true",result); return result;
}
void initializeApi(const NativeApi* api) {
    pthread_mutex_lock(&mutex);
    hookFunction=api && api->version>=1?api->hook:nullptr;
    if(!installedBase) state=hookFunction?0:-1;
    pthread_mutex_unlock(&mutex);
    log("stage=native_api version=%u available=%d",api?api->version:0,hookFunction!=nullptr);
}
}
extern "C" __attribute__((visibility("default"),used))
gamecube::LibraryCallback native_init(const gamecube::NativeApi* api) {
    gamecube::initializeApi(api); return gamecube::onLibraryLoaded;
}
