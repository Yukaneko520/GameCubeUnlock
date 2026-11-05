LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := gamecube_tuning
LOCAL_SRC_FILES := native-sr-state.cpp native-sr-target.cpp native-sr-functions.cpp native-sr-loader.cpp native-sr-jni.cpp
LOCAL_CPPFLAGS := -std=c++17 -O2 -Wall -Wextra -Werror -fvisibility=hidden -fno-exceptions -fno-rtti -mbranch-protection=standard
LOCAL_LDLIBS := -llog -ldl
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
