/* Header for class com_google_vr_vrcore_controller_ControllerService */

#ifndef _Included_com_google_vr_vrcore_controller_ControllerService
#define _Included_com_google_vr_vrcore_controller_ControllerService



#include <jni.h>
#include <utils/Log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <pthread.h>
#include <signal.h>
#include <errno.h>
#include <dlfcn.h>
#include <linux/ioctl.h>
#include <linux/time.h>
#include <linux/hidraw.h>


int is_file_exist(const char *file_path);

JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeOpenFile(JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeCloseFile(JNIEnv *, jclass);

JNIEXPORT jobject JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeReadFile(JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeWriteFile(JNIEnv *, jclass, jint, jint, jint);
#endif
