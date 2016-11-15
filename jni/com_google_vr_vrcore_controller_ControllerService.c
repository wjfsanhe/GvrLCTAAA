
#include "com_google_vr_vrcore_controller_ControllerService.h"

#define HIDRAW_BUFFER_SIZE 32
#define OPEN_ERR_DEVICE -1
#define OPEN_SUCCESS_DEVICE_0 0
#define OPEN_SUCCESS_DEVICE_1 1
#define OPEN_SUCCESS_DEVICE_2 2

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "ControllerService_jni"

#define DEBUG 1

#define JOYSTICK_TO_HOST 8
#define HOST_TO_JOYSTICK 7

#define GET_DATA_TIMEOUT -1
#define GET_INVALID_DATA -2
#define REPORT_TYPE_ORIENTATION 1
#define REPORT_TYPE_SENSOR		2
#define REPORT_TYPE_VERSION		3

typedef unsigned char byte;

const char *device[3] = {"/dev/hidraw0", "/dev/hidraw1","/dev/hidraw2"};
static const char values_str[] = "01";
static int hidraw_fd = 0;
#ifdef DEBUG
static int out_fd = 0;
#endif
static jclass clsBt_node_data = NULL;

JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeOpenFile
  (JNIEnv *env, jobject jclass) {
	int ret = -1; // not -1, 0, 1, 2
	ALOGD("call native_open_file");
	for (int i = 0; i < 3; i++) {
		hidraw_fd = open(device[i], O_RDWR);
		if (hidraw_fd < 0) {
			ALOGE("Open %s failed, %s\n", device[i], strerror(errno));
			continue;
		}
		ALOGD("Open %s Success!\n", device[i]);
		ret = 0;
		break;
	}
#ifdef DEBUG
	out_fd = open("/sys/class/gpio/gpio126/value", O_RDWR);
#endif
//	clsBt_node_data = (*env)->FindClass(env, "com/google/vr/vrcore/controller/bt_node_data");
	return ret;
}
JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeCloseFile
  (JNIEnv *env, jobject jclass)  {
	if(hidraw_fd >=0){
		ALOGD("Close device fd");
		close(hidraw_fd);
	}
#ifdef DEBUG
	if(out_fd >= 0){
		ALOGD("Close gpio fd");
		close(out_fd);
	}
#endif
	return 0;
}

JNIEXPORT jobject Java_com_google_vr_vrcore_controller_ControllerService_nativeReadFile(JNIEnv *env, jobject jclass){
	ALOGD("call nativeReadFile, hidraw_fd:%d, out_fd:%d\n", hidraw_fd, out_fd);
	if(hidraw_fd <0) return 0;
	unsigned char buf[HIDRAW_BUFFER_SIZE];

	// first to find class ,before read, because read can be blocked
	clsBt_node_data = (*env)->FindClass(env, "com/google/vr/vrcore/controller/Bt_node_data");
	jmethodID mid_init = (*env)->GetMethodID(env, clsBt_node_data, "<init>", "()V");
	jobject data_struct = (*env)->NewObject(env, clsBt_node_data, mid_init);//, qd[0], qd[1], qd[2], qd[3]);

	int ready,res;
	struct pollfd readfds;
	struct timespec ts1, ts2;

	readfds.fd = hidraw_fd;
	readfds.events = POLLIN;
	//record duration
	clock_gettime(CLOCK_MONOTONIC_RAW, &ts1);

	// poll hidraw_fd ,only 1 fd, block 12ms
	ready = poll(&readfds, 1,12);
	if (ready) {
		// read hidraw data
		res = read(hidraw_fd, buf, HIDRAW_BUFFER_SIZE);
		if (res < 0) {
			ALOGE("READERR, res:%d\n", res);
			return 0;
		}
	}else{
		clock_gettime(CLOCK_MONOTONIC_RAW, &ts2);
		ALOGD("native read, block timeout, delta time:%ld\n", (ts2.tv_nsec-ts1.tv_nsec)/1000);
		ALOGD("No data to read");
		jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		(*env)->SetIntField(env, data_struct, type, GET_DATA_TIMEOUT);
		return data_struct;
	}
	clock_gettime(CLOCK_MONOTONIC_RAW, &ts2);
	ALOGD("[%d]:TS %ld.%06ld PN %05d [%d]:", res, ts2.tv_sec, ts2.tv_nsec/1000, buf[1]+buf[2]*256, buf[3]);
	ALOGD("native read, delta time:%ld\n", (ts2.tv_nsec-ts1.tv_nsec)/1000);
	//ALOGD("native read, data count is %d, buf[3]:%d,packetNum:%04X\n", res, (int)buf[3],*((short*) (buf+1)));
	if(buf[0] == JOYSTICK_TO_HOST && buf[3] == REPORT_TYPE_ORIENTATION) {
		float *qd;
		qd = (float*)(buf+4);
#if 0//def DEBUG
		ALOGD("quans data(w,x,y,z):%f,%f,%f,%f\n",qd[0], qd[1], qd[2], qd[3]);
#endif

		jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		jfieldID quans_x = (*env)->GetFieldID(env, clsBt_node_data, "quans_x", "F");
		jfieldID quans_y = (*env)->GetFieldID(env, clsBt_node_data, "quans_y", "F");
		jfieldID quans_z = (*env)->GetFieldID(env, clsBt_node_data, "quans_z", "F");
		jfieldID quans_w = (*env)->GetFieldID(env, clsBt_node_data, "quans_w", "F");
		(*env)->SetIntField(env, data_struct, type, REPORT_TYPE_ORIENTATION);
		(*env)->SetFloatField(env, data_struct, quans_x, qd[1]);
		(*env)->SetFloatField(env, data_struct, quans_y, qd[2]);
		(*env)->SetFloatField(env, data_struct, quans_z, qd[3]);
		(*env)->SetFloatField(env, data_struct, quans_w, qd[0]);
#ifdef DEBUG
		if (buf[20] == 0x01) {
			write(out_fd, &values_str[1], 1);
		}
		else {
			write(out_fd, &values_str[0], 1);
		}
#endif
		return data_struct;
	} else if (buf[0] == JOYSTICK_TO_HOST && buf[3] == REPORT_TYPE_SENSOR) {
		float sensor_gyro[3], sensor_accel[3], touch_x, touch_y;
		unsigned char i;
		byte data_keymask;
		int data_battery;

		for (i = 0; i < 3; i++) {
			sensor_gyro[i] = (float) (((short*) (buf + 4))[i]) / 16.0f;
		}
		for (i = 0; i < 3; i++) {
			sensor_accel[i] = (float) (((short*) (buf + 10))[i]) / 4000.0f;
		}
		touch_x = ((float) buf[16]) / 200.0f; //0~200  19~190
		touch_y = ((float) buf[17]) / 200.0f;
		data_keymask = buf[18];
		data_battery = (int) buf[19];
#if 0//def DEBUG
		ALOGD("type2 %02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X\n",
				buf[4],buf[5],buf[6],buf[7],buf[8],buf[9],buf[10],buf[11],buf[12],buf[13],buf[14],buf[15],buf[16]);
#endif
		jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		jfieldID gyro_x = (*env)->GetFieldID(env, clsBt_node_data, "gyro_x",
				"F");
		jfieldID gyro_y = (*env)->GetFieldID(env, clsBt_node_data, "gyro_y",
				"F");
		jfieldID gyro_z = (*env)->GetFieldID(env, clsBt_node_data, "gyro_z",
				"F");
		jfieldID acc_x = (*env)->GetFieldID(env, clsBt_node_data, "acc_x", "F");
		jfieldID acc_y = (*env)->GetFieldID(env, clsBt_node_data, "acc_y", "F");
		jfieldID acc_z = (*env)->GetFieldID(env, clsBt_node_data, "acc_z", "F");
		jfieldID touchX = (*env)->GetFieldID(env, clsBt_node_data, "touchX",
				"F");
		jfieldID touchY = (*env)->GetFieldID(env, clsBt_node_data, "touchY",
				"F");
		jfieldID bat_level = (*env)->GetFieldID(env, clsBt_node_data,
				"bat_level", "I");
		jfieldID keymask = (*env)->GetFieldID(env, clsBt_node_data, "keymask",
				"B");

		if (type == NULL) {
			ALOGE("getFieldID type failed");
		} else {
			(*env)->SetIntField(env, data_struct, type, REPORT_TYPE_SENSOR);
		}
		if (gyro_x == NULL) {
			ALOGE("getFieldID gyro_x failed");
		} else {
			(*env)->SetFloatField(env, data_struct, gyro_x, sensor_gyro[0]);
		}
		if (gyro_y == NULL) {
			ALOGE("getFieldID gyro_y failed");
		} else {
			(*env)->SetFloatField(env, data_struct, gyro_y, sensor_gyro[1]);
		}
		if (gyro_z == NULL) {
			ALOGE("getFieldID gyro_z failed");
		} else {
			(*env)->SetFloatField(env, data_struct, gyro_z, sensor_gyro[2]);
		}
		if (acc_x == NULL) {
			ALOGE("getFieldID acc_x failed");
		} else {
			(*env)->SetFloatField(env, data_struct, acc_x, sensor_accel[0]);
		}
		if (acc_y == NULL) {
			ALOGE("getFieldID acc_y failed");
		} else {
			(*env)->SetFloatField(env, data_struct, acc_y, sensor_accel[1]);
		}
		if (acc_z == NULL) {
			ALOGE("getFieldID acc_z failed");
		} else {
			(*env)->SetFloatField(env, data_struct, acc_z, sensor_accel[2]);
		}
		if (touchX == NULL) {
			ALOGE("getFieldID touchX failed");
		} else {
			(*env)->SetFloatField(env, data_struct, touchX, touch_x);
		}
		if (touchY == NULL) {
			ALOGE("getFieldID touchY failed");
		} else {
			(*env)->SetFloatField(env, data_struct, touchY, touch_y);
		}
		if (bat_level == NULL) {
			ALOGE("getFieldID bat_level failed");
		} else {
			(*env)->SetIntField(env, data_struct, bat_level, data_battery);
		}
		if (keymask == NULL) {
			ALOGE("getFieldID keymask failed");
		} else {
			(*env)->SetByteField(env, data_struct, keymask, data_keymask);
		}
		return data_struct;
	}else if (buf[0] == JOYSTICK_TO_HOST && buf[3] == REPORT_TYPE_VERSION){
		int appVersion, deviceVersion, deviceType;

		appVersion = *((int*)(buf+4));
		deviceVersion = *((short*)(buf+8));
		deviceType = *((short*)(buf+10));
#ifdef DEBUG
		ALOGD("type3 VERSION: %02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X,%02X\n",
				buf[4],buf[5],buf[6],buf[7],buf[8],buf[9],buf[10],buf[11],buf[12],buf[13],buf[14],buf[15],buf[16]);
#endif
		ALOGD("appversion:%d, deviceVerion:%d, deviceType:%d\n", appVersion, deviceVersion, deviceType);
		jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		jfieldID aVersion = (*env)->GetFieldID(env, clsBt_node_data, "appVersion", "I");
		jfieldID dVersion = (*env)->GetFieldID(env, clsBt_node_data, "deviceVersion", "I");
		jfieldID dType = (*env)->GetFieldID(env, clsBt_node_data, "deviceType", "I");
		(*env)->SetIntField(env, data_struct, type, REPORT_TYPE_VERSION);
		(*env)->SetIntField(env, data_struct, aVersion, appVersion);
		(*env)->SetIntField(env, data_struct, dVersion, deviceVersion);
		(*env)->SetIntField(env, data_struct, dType, deviceType);

		return data_struct;
	}else{
		jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		(*env)->SetIntField(env, data_struct, type, GET_INVALID_DATA);
		return data_struct;
	}


	return 0;
}

JNIEXPORT jint Java_com_google_vr_vrcore_controller_ControllerService_nativeWriteFile(JNIEnv *env, jobject jclass, jint type, jint data1, jint data2){
	ALOGD("call nativeWriteFile, hidraw_fd:%d\n", hidraw_fd);
	if(hidraw_fd <0) return 0;
	unsigned char buf[4];

	buf[0] = 0x07;//report id
	buf[1] = (char)type;//type
	buf[2] = (char)data1;
	buf[3] = (char)data2;

#ifdef DEBUG
		ALOGD("write hidraw node %02X,%02X,%02X,%02X,\n",
				buf[0],buf[1],buf[2],buf[3]);
#endif
	write(hidraw_fd, buf, 4);
//	lseek(hidraw_fd, 0, SEEK_SET);
		return 0;
}
