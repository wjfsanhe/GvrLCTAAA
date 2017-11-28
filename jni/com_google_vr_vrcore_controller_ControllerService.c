
#include "com_google_vr_vrcore_controller_ControllerService.h"

#define IQIYI_HIDRAW_BUFFER_SIZE 32
#define OPEN_ERR_DEVICE -1
#define OPEN_SUCCESS_DEVICE_0 0
#define OPEN_SUCCESS_DEVICE_1 1
#define OPEN_SUCCESS_DEVICE_2 2

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "ControllerService_jni"

// #define DEBUG 0

#define JOYSTICK_TO_HOST 8
#define HOST_TO_JOYSTICK 7

#define GET_DATA_TIMEOUT -1
#define GET_INVALID_DATA -2
#define REPORT_TYPE_ORIENTATION 1
#define REPORT_TYPE_SENSOR		2
#define REPORT_TYPE_VERSION		3
#define REPORT_TYPE_SHAKE		4

#define POLL_TIMEOUT_TIME 12

#define IQIYI_HAND_VENDOR_ID  0x1915
#define IQIYI_HAND_PRODUCTION_ID 0xeeee

#define FILE_EXIST 0
#define FILE_NOT_EXIST -1

#define DEVICE_ORDER_NUMBER 5

#define IQIYI_HID_DIR	"/dev"

typedef unsigned char byte;

const char *device[DEVICE_ORDER_NUMBER] = {"/dev/hidraw-iqiyi0", "/dev/hidraw-iqiyi1","/dev/hidraw-iqiyi2", "/dev/hidraw-iqiyi3", "/dev/hidraw-iqiyi4"};
static int hidraw_fd = -1;
#ifdef DEBUG
static const char values_str[] = "01";
static int out_fd = 0;
#endif
//static jclass clsBt_node_data = NULL;

int is_file_existed(const char *file_path){
	if(file_path == NULL){
		return FILE_NOT_EXIST;
	}
	if(access(file_path, F_OK) == 0){
		return FILE_EXIST;
	}
	return FILE_NOT_EXIST;
}
#if 0
JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeOpenFile
  (JNIEnv *env, jobject jclass) {
	char deviceFile[PATH_MAX];
	int fd = -1;
	int ret = -1;
	DIR * devDir;
	struct dirent * devDirEntry;
	struct hidraw_devinfo info;

	devDir = opendir("/dev");
	ALOGD("mshuai Open /dev \n");
	if (!devDir)
		return -1;
	ALOGD("mshuai Open /dev success\n");
	while ((devDirEntry = readdir(devDir)) != NULL) {
		ALOGD("mshuai dev dir entry name:%s\n", devDirEntry->d_name);
		if (strstr(devDirEntry->d_name, "hidraw-iqiyi")) {
			char rawDevice[PATH_MAX];
			strncpy(rawDevice, devDirEntry->d_name, PATH_MAX);
			snprintf(deviceFile, PATH_MAX, "/dev/%s", devDirEntry->d_name);
			fd = open(deviceFile, O_RDWR);
			if (fd < 0) {
							ALOGE("Open %s failed, %s\n", deviceFile, strerror(errno));
				continue;
			} else {
				ALOGD("Open %s Success!\n", deviceFile);
				/* Get Raw Info */
				int res = ioctl(fd, HIDIOCGRAWINFO, &info);
				if (res < 0) {
					ALOGE(
							"get hidraw info err, can't verify if it is iQIYI hand");
					continue;
				} else {
					// here we get IQIYI hand device
					if (IQIYI_HAND_VENDOR_ID == (unsigned short) info.vendor
							&& IQIYI_HAND_PRODUCTION_ID
									== (unsigned short) info.product) {
						ALOGD(
								"we get IQIYI hand device, service build time is: %s,%s\n", __DATE__, __TIME__);
						ret = 0;
						hidraw_fd = fd;
						break;
					} else {
						ALOGD(
								"not IQIYI hand device, get hidraw info, vendor: %d, %d\n", info.vendor, info.product);
						if (fd >= 0) {
							ALOGD("Close device %s\n", deviceFile);
							close(fd);
						}
						continue;
					}
				}
			}
		}
	}
	closedir(devDir);
	return ret;
}

#else
static int open_exiting_hid(const char *path)
{
	int fd = -1;
	struct hidraw_devinfo info;

	// at first check if file is existed
	if(FILE_NOT_EXIST == is_file_existed(path)) {
#ifdef DEBUG
		ALOGD("file %s is not existed", path);
#endif
		return 0;
	}

	fd = open(path, O_RDWR);
	if (fd < 0) {
#ifdef DEBUG
		ALOGE("Open %s failed, %s\n", path, strerror(errno));
#endif
		return 0;
	} else {
#ifdef DEBUG
		ALOGD("Open %s Success!\n", path);
#endif
		/* Get Raw Info */
		int res = ioctl(fd, HIDIOCGRAWINFO, &info);
		if (res < 0) {
#ifdef DEBUG
			ALOGE("get hidraw info err, can't verify if it is iQIYI hand");
#endif
			close(fd);
			return 0;
		} else {
			// here we get IQIYI hand device
			if (IQIYI_HAND_VENDOR_ID == (unsigned short) info.vendor
					&& IQIYI_HAND_PRODUCTION_ID ==  (unsigned short) info.product)  {
#ifdef DEBUG
				ALOGD("we get IQIYI hand device, service build time is: %s,%s\n",__DATE__, __TIME__);
#endif
				hidraw_fd = fd;
			} else {
#ifdef DEBUG
				ALOGD("not IQIYI hand device, get hidraw info, vendor: %d, %d\n", info.vendor, info.product);
#endif
				if(fd >=0) {
#ifdef DEBUG
					ALOGD("Close device %s\n", path);
#endif
					close(fd);
				}
				return 0;
			}
		}
	}

	return 1;
}

JNIEXPORT jint JNICALL Java_com_google_vr_vrcore_controller_ControllerService_nativeOpenFile
  (JNIEnv *env, jobject jclass) {
	int ret = -1; // not -1, 0, 1, 2
#if 0
	int fd = -1;
	struct hidraw_devinfo info;
#endif

#ifdef DEBUG
	ALOGD("call native_open_file");
#endif
	char devname[32];
	DIR *busdir;
	struct dirent *de;
	int done = 0;
	busdir = opendir(IQIYI_HID_DIR);
	if (busdir == 0) {
		ret = -1;
#ifdef DEBUG
		ALOGD("error open dir");
#endif
		return ret;
	}

	while (((de = readdir(busdir))  !=  0)  && ( !done)) {
#ifdef DEBUG
		ALOGD("error d_name is %s", de->d_name);
#endif
		if(strncmp(de->d_name,  "hidraw",  6) != 0) continue;

		snprintf(devname, sizeof(devname), IQIYI_HID_DIR "/%s", de->d_name);
#ifdef DEBUG
		ALOGD("error devname is %d", devname);
#endif
		done = open_exiting_hid(devname);
	}
	closedir(busdir);
	ret = 0;

#if 0
	for (int i = 0; i < DEVICE_ORDER_NUMBER; i++) {
		// at first check if file is existed
		if(FILE_NOT_EXIST == is_file_existed(device[i])){
//			ALOGD("file %s is not existed", device[i]);
			continue;
		}
		fd = open(device[i], O_RDWR);
		if (fd < 0) {
//			ALOGE("Open %s failed, %s\n", device[i], strerror(errno));
			continue;
		} else {
			ALOGD("Open %s Success!\n", device[i]);
			/* Get Raw Info */
			int res = ioctl(fd, HIDIOCGRAWINFO, &info);
			if (res < 0) {
				ALOGE("get hidraw info err, can't verify if it is iQIYI hand");
				continue;
			} else {
				// here we get IQIYI hand device
				if (IQIYI_HAND_VENDOR_ID == (unsigned short) info.vendor
						&& IQIYI_HAND_PRODUCTION_ID
								== (unsigned short) info.product) {
					ALOGD("we get IQIYI hand device, service build time is: %s,%s\n",__DATE__, __TIME__);
					ret = 0;
					hidraw_fd = fd;
					break;
				} else {
					ALOGD(
							"not IQIYI hand device, get hidraw info, vendor: %d, %d\n", info.vendor, info.product);
					if(fd >=0){
						ALOGD("Close device %s\n", device[i]);
						close(fd);
					}
					continue;
				}
			}
		}
	}
#endif

#ifdef DEBUG
	out_fd = open("/sys/class/gpio/gpio126/value", O_RDWR);
#endif
//	clsBt_node_data = (*env)->FindClass(env, "com/google/vr/vrcore/controller/bt_node_data");
	return ret;
}
#endif
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

/*
 * if return 0 ,it means fd is useness, so java thread should close fd
 */
JNIEXPORT jobject Java_com_google_vr_vrcore_controller_ControllerService_nativeReadFile(JNIEnv *env, jobject jc){
#ifdef DEBUG
	ALOGD("call nativeReadFile, hidraw_fd:%d, out_fd:%d\n", hidraw_fd, out_fd);
#endif
	if(hidraw_fd <0) return 0;
	unsigned char buf[IQIYI_HIDRAW_BUFFER_SIZE];
	jclass clsBt_node_data = NULL;

	// first to find class ,before read, because read can be blocked
	clsBt_node_data = (*env)->FindClass(env, "com/google/vr/vrcore/controller/Bt_node_data");
	jmethodID mid_init = (*env)->GetMethodID(env, clsBt_node_data, "<init>", "()V");
	jobject data_struct = (*env)->NewObject(env, clsBt_node_data, mid_init);//, qd[0], qd[1], qd[2], qd[3]);

	int ready,res;
	static unsigned short packetNum;
#ifdef DEBUG
	static unsigned int count, totalCount;
	static int pre_packetNum=-1;
#endif
	struct pollfd readfds;
	struct timespec ts1, ts2;

	readfds.fd = hidraw_fd;
	readfds.events = POLLIN;
	//record duration
	clock_gettime(CLOCK_MONOTONIC_RAW, &ts1);

	// poll hidraw_fd ,only 1 fd, block 12ms
	ready = poll(&readfds, 1,POLL_TIMEOUT_TIME);
	if (ready) {
		// read hidraw data
		res = read(hidraw_fd, buf, IQIYI_HIDRAW_BUFFER_SIZE);
		if (res < 0) {
			ALOGE("READERR, res:%d\n", res);
			return 0;
		}
	}else{
		clock_gettime(CLOCK_MONOTONIC_RAW, &ts2);
//		ALOGD("No data to read, native read, block timeout, delta time:%ld\n", (ts2.tv_nsec-ts1.tv_nsec)/1000);
		jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		(*env)->SetIntField(env, data_struct, type, GET_DATA_TIMEOUT);
		return data_struct;
	}
	clock_gettime(CLOCK_MONOTONIC_RAW, &ts2);
	packetNum = buf[1]+buf[2]*256;
#ifdef DEBUG
	count = packetNum - pre_packetNum;
	if ((count > 1) && pre_packetNum != -1 && !(pre_packetNum == 65535 && packetNum == 0)){// lost
		totalCount++;
		ALOGE("EEEEE lose packet ,count is %d, totalCount:%d\n", count, totalCount);
	}
	pre_packetNum = packetNum;
#endif
#ifdef DEBUG
	ALOGD("[%d]:TS %ld.%06ld PN %05d [%d]: delta time:%ld\n", res, ts2.tv_sec, ts2.tv_nsec/1000, packetNum, buf[3], (ts2.tv_nsec-ts1.tv_nsec)/1000);
#endif
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
		/* Transform battery level from 0~255 to 0~100 */
		if(data_battery < 100){
		    data_battery = 0;
		}else if(data_battery >= 230){
		    data_battery = 100;
		}else{
		    data_battery = (int)(100 * (data_battery - 100) / (float)(230 - 100));
		}
		//ALOGD("data_battery after %d",data_battery);
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
    }else if (buf[0] == JOYSTICK_TO_HOST && buf[3] == REPORT_TYPE_SHAKE){
        int time_stamp;
        int shake_event;
        int event_parameter;
        time_stamp = *((int*)(buf+4));
        shake_event = (int)buf[8];
        event_parameter = (int)buf[9];
        ALOGD("time_stamp:%d, shake_event:%d, event_parameter:%d\n", time_stamp, shake_event, event_parameter);
        jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		jfieldID time = (*env)->GetFieldID(env, clsBt_node_data, "timeStamp", "I");
        jfieldID shake = (*env)->GetFieldID(env, clsBt_node_data, "shakeEvent", "I");
        jfieldID parameter = (*env)->GetFieldID(env, clsBt_node_data, "eventParameter", "I");
        if(type != NULL){
            (*env)->SetIntField(env, data_struct, type, REPORT_TYPE_SHAKE);
        }
        if(time != NULL){
		    (*env)->SetIntField(env, data_struct, time, time_stamp);
		}
		if(shake != NULL){
            (*env)->SetIntField(env, data_struct, shake, shake_event);
        }
        if(parameter != NULL){
            (*env)->SetIntField(env, data_struct, parameter, event_parameter);
        }
        return data_struct;
	}else{
		jfieldID type = (*env)->GetFieldID(env, clsBt_node_data, "type", "I");
		(*env)->SetIntField(env, data_struct, type, GET_INVALID_DATA);
		return data_struct;
	}


	return 0;
}

JNIEXPORT jint Java_com_google_vr_vrcore_controller_ControllerService_nativeWriteFile(JNIEnv *env, jobject jclass, jint type, jint data1, jint data2){
	int result = -1;
#ifdef DEBUG
	ALOGD("call nativeWriteFile, hidraw_fd:%d\n", hidraw_fd);
#endif
	if(hidraw_fd <0) return -1;
	unsigned char buf[3];
    if(data1 < 0 || data1 > 7){
        ALOGD("data1 is not crrectly %d\n",data1);
        data1 = 0;
    }
	//buf[0] = 0x07;//report id
	//buf[1] = (char)type;//type
	//buf[2] = (char)data1;
	//buf[3] = (char)data2;
    buf[0] = 0x07;//report id
    buf[1] = ((byte)type) | ((byte)data1<<5);
    buf[2] = (char)data2;


#ifdef DEBUG
		ALOGD("write hidraw node %02X,%02X,%02X\n",
				buf[0],buf[1],buf[2]);
#endif
	result = write(hidraw_fd, buf, 3);
	if(result < 0){
		return -1;
	}
//	lseek(hidraw_fd, 0, SEEK_SET);
		return 0;
}

