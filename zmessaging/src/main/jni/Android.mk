TOP_DIR := $(call my-dir)
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := lzw-decoder
LOCAL_SRC_FILES := LzwDecoder.cpp
LOCAL_LDLIBS    := -L$(SYSROOT)/usr/lib -llog
LOCAL_CFLAGS    := -O2 -Wall -pedantic -Wno-variadic-macros
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := jnidispatch
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libjnidispatch.so
include $(PREBUILT_SHARED_LIBRARY)

