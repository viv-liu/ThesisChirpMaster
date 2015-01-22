LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := PlayToneMaster
LOCAL_SRC_FILES := PlayToneMaster.cpp

include $(BUILD_SHARED_LIBRARY)
