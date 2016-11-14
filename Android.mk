# Copyright 2011 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    src/com/android/qiyicontroller/AIDLController.aidl


LOCAL_JNI_SHARED_LIBRARIES := liblctgetnode
LOCAL_JAVA_LIBRARIES := \
	#gvrlctservice_common\
	android-support-v4 \
	android-support-v7-recyclerview \
	android-support-v7-preference \
	android-support-v7-appcompat \
	android-support-v14-preference \

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
    frameworks/support/v7/preference/res \
    frameworks/support/v14/preference/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/v7/recyclerview/res

LOCAL_STATIC_JAVA_AAR_LIBRARIES:= gvrlctservice_common


LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.v7.preference:android.support.v14.preference:android.support.v17.preference:android.support.v7.appcompat:android.support.v7.recyclerview


LOCAL_PACKAGE_NAME := GvrLctService
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)


####################################
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := gvrlctservice_common:libs/gvrlctservice_common_old.aar
LOCAL_MODULE_TAGS := optional
include $(BUILD_MULTI_PREBUILT)
#####################################

include $(call all-makefiles-under,$(LOCAL_PATH))
