#include "native-sr-state.h"
#include <android/log.h>
#include <stdarg.h>
#include <stdio.h>
#include <math.h>
#include <pthread.h>

namespace gamecube {
static pthread_mutex_t mutex=PTHREAD_MUTEX_INITIALIZER;
static Policy current;
static char packageName[256]="unknown",buildName[64]="unknown";
Policy policy() {
    pthread_mutex_lock(&mutex); Policy value=current; pthread_mutex_unlock(&mutex);
    if(status()!=1) value.enabled=false;
    return value;
}
void configure(Policy value) {
    if(value.algorithm<0 || value.algorithm>6 || value.width<0 || value.width>4000
            || value.source< -1 || value.source>1 || !isfinite(value.sharpness) || value.sharpness<0 || value.sharpness>10
            || !isfinite(value.dark) || value.dark< -255 || value.dark>0 || !isfinite(value.white)
            || value.white<0 || value.white>255) value=Policy{};
    pthread_mutex_lock(&mutex);
    bool changed=current.enabled!=value.enabled || current.revision!=value.revision
            || current.algorithm!=value.algorithm || current.width!=value.width || current.source!=value.source
            || current.sharpness!=value.sharpness || current.dark!=value.dark || current.white!=value.white;
    current=value; pthread_mutex_unlock(&mutex);
    if(changed) log("stage=policy requested_enabled=%d algorithm=%d width=%d source=%d sharpness=%.3f dark=%.3f white=%.3f revision=%lld install_status=%d",
            value.enabled,value.algorithm,value.width,value.source,value.sharpness,value.dark,value.white,
            static_cast<long long>(value.revision),status());
}
void metadata(const char* package,const char* build) {
    pthread_mutex_lock(&mutex);
    snprintf(packageName,sizeof(packageName),"%s",package);
    snprintf(buildName,sizeof(buildName),"%s",build);
    pthread_mutex_unlock(&mutex);
}
void log(const char* format,...) {
    char message[1024],pkg[256],build[64];
    va_list args; va_start(args,format); vsnprintf(message,sizeof(message),format,args); va_end(args);
    pthread_mutex_lock(&mutex);
    snprintf(pkg,sizeof(pkg),"%s",packageName); snprintf(build,sizeof(build),"%s",buildName);
    pthread_mutex_unlock(&mutex);
    __android_log_print(ANDROID_LOG_INFO,"GameCubeUnlock","build=%s source=native feature=sr_tuning pkg=%s %s",build,pkg,message);
}
}
