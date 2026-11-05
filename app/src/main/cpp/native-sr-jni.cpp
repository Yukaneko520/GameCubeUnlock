#include "native-sr-state.h"
#include <jni.h>

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*,void*) { return JNI_VERSION_1_6; }
extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuka_gamecube_tools_hooks_graphics_NativeSrBridge_selectTarget(JNIEnv*,jclass,jint sdk) {
    return gamecube::selectTarget(sdk);
}
extern "C" JNIEXPORT void JNICALL
Java_com_yuka_gamecube_tools_hooks_graphics_NativeSrBridge_metadata(JNIEnv* env,jclass,jstring pkg,jstring build) {
    if(!pkg || !build) return;
    const char* p=env->GetStringUTFChars(pkg,nullptr);
    if(!p) return;
    const char* b=env->GetStringUTFChars(build,nullptr);
    if(b) { gamecube::metadata(p,b); env->ReleaseStringUTFChars(build,b); }
    env->ReleaseStringUTFChars(pkg,p);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_yuka_gamecube_tools_hooks_graphics_NativeSrBridge_candidate(JNIEnv* env,jclass) {
    gamecube::discoverLoaded(); auto c=gamecube::candidate();
    return c.base?env->NewStringUTF(c.path):nullptr;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_gamecube_tools_hooks_graphics_NativeSrBridge_install(JNIEnv* env,jclass,jstring path,jstring digest) {
    if(!path || !digest) return -4;
    const char* p=env->GetStringUTFChars(path,nullptr);
    if(!p) return -4;
    const char* d=env->GetStringUTFChars(digest,nullptr);
    int result=-4;
    if(d) { result=gamecube::install(p,d); env->ReleaseStringUTFChars(digest,d); }
    env->ReleaseStringUTFChars(path,p); return result;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_yuka_gamecube_tools_hooks_graphics_NativeSrBridge_status(JNIEnv*,jclass) { return gamecube::status(); }
extern "C" JNIEXPORT void JNICALL
Java_com_yuka_gamecube_tools_hooks_graphics_NativeSrBridge_configure(JNIEnv*,jclass,jboolean enabled,
        jint algorithm,jint width,jint source,jdouble sharpness,jdouble dark,jdouble white,jlong revision) {
    gamecube::configure({static_cast<bool>(enabled),algorithm,width,source,static_cast<float>(sharpness),
            static_cast<float>(dark),static_cast<float>(white),revision});
}
