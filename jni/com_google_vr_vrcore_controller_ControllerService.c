
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

const char *device[3] = {"/dev/hidraw0", "/dev/hidraw1","/dev/hidraw2"};
char buf[HIDRAW_BUFFER_SIZE];
static const char values_str[] = "01";
static int hidraw_fd = 0;
static int out_fd = 0;
static jclass clsbt_node_data = NULL;

JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeOpenFile
  (
		JNIEnv *env, jobject jclass) {
	int ret = -1; // not -1, 0, 1, 2
	ALOGD(
			"call Java_com_google_vr_vrcore_controller_ControllerService_native_open_file");
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
	out_fd = open("/sys/class/gpio/gpio126/value", O_RDWR);

//	clsbt_node_data = (*env)->FindClass(env, "com/google/vr/vrcore/controller/bt_node_data");
	return ret;
}
JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeCloseFile
  (JNIEnv *env, jobject jclass)  {
	if(hidraw_fd >=0){
		ALOGD("Close device fd");
		close(hidraw_fd);
	}
	if(out_fd >= 0){
		ALOGD("Close gpio fd");
		close(out_fd);
	}
	return 0;
}
/*class bt_node_data{
        public int type;

        public float quans_x;
        public float quans_y;
        public float quans_z;
        public float quans_w;

        public int gyro_x;
        public int gyro_y;
        public int gyro_z;

        public int acc_x;
        public int acc_y;
        public int acc_z;

        public float touchX;
        public float touchY;

        public byte keymask;

        public int battery;
    }*/
JNIEXPORT jobject Java_com_google_vr_vrcore_controller_ControllerService_nativeReadFile(JNIEnv *env, jobject jclass){
	ALOGD("call Java_com_google_vr_vrcore_controller_ControllerService_native_read_file, hidraw_fd:%d, out_fd:%d\n", hidraw_fd, out_fd);
	if(hidraw_fd <0) return 0;
	int res = read(hidraw_fd, buf, HIDRAW_BUFFER_SIZE);
	if (res < 0) {
		ALOGE("READERR, res:%d\n", res);
		return 0;
	}
	ALOGD("native read, data count is %d\n:", res);
	if(buf[3] == 0x01) {
		float *qd;
		qd = (float*)(buf+4);
		ALOGD("quans data:%f,%f,%f,%f\n",qd[0], qd[1], qd[2], qd[3]);

		clsbt_node_data = (*env)->FindClass(env, "com/google/vr/vrcore/controller/bt_node_data");
		jmethodID mid_init = (*env)->GetMethodID(env, clsbt_node_data, "<init>", "()V");
		ALOGD("quans data2\n");
		jobject data_struct = (*env)->NewObject(env, clsbt_node_data, mid_init);//, qd[0], qd[1], qd[2], qd[3]);
		ALOGD("quans data3\n");

		jfieldID type = (*env)->GetFieldID(env, clsbt_node_data, "type", "I");
		jfieldID quans_x = (*env)->GetFieldID(env, clsbt_node_data, "quans_x", "F");
		jfieldID quans_y = (*env)->GetFieldID(env, clsbt_node_data, "quans_y", "F");
		jfieldID quans_z = (*env)->GetFieldID(env, clsbt_node_data, "quans_z", "F");
		jfieldID quans_w = (*env)->GetFieldID(env, clsbt_node_data, "quans_w", "F");
		ALOGD("quans data4 ,after getFieldID\n");
		(*env)->SetIntField(env, data_struct, type, 1);
		(*env)->SetFloatField(env, data_struct, quans_x, qd[1]);
		(*env)->SetFloatField(env, data_struct, quans_y, qd[2]);
		(*env)->SetFloatField(env, data_struct, quans_z, qd[3]);
		(*env)->SetFloatField(env, data_struct, quans_w, qd[0]);
		ALOGD("quans data4 ,after setFieldID\n");

		if (buf[20] == 0x01) {
			ALOGD("set gpio value to 1\n");
			write(out_fd, &values_str[1], 1);
		}
		else {
			ALOGD("set gpio value to 0\n");
			write(out_fd, &values_str[0], 1);
		}
		ALOGD("quans data4 ,before return to java\n");
		return data_struct;
	}else if(buf[3] == 0x02){


		ALOGD("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
	}

	return 0;
}
