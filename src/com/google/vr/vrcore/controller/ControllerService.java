package com.google.vr.vrcore.controller;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
//import android.support.annotation.Nullable;
import android.util.Log;

import com.android.qiyicontroller.AIDLControllerService;
import com.android.qiyicontroller.EventInstance;
import com.android.qiyicontroller.MessageEvent;
import com.google.vr.vrcore.controller.api.ControllerAccelEvent;
import com.google.vr.vrcore.controller.api.ControllerButtonEvent;
//import com.google.vr.vrcore.controller.api.ControllerEventPacket;
import com.google.vr.vrcore.controller.api.ControllerGyroEvent;
import com.google.vr.vrcore.controller.api.ControllerOrientationEvent;
import com.google.vr.vrcore.controller.api.ControllerServiceConsts;
import com.google.vr.vrcore.controller.api.ControllerStates;
import com.google.vr.vrcore.controller.api.ControllerTouchEvent;
import com.google.vr.vrcore.controller.api.IControllerListener;
import com.google.vr.vrcore.controller.api.IControllerService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

//add by zhangyawen
import android.view.KeyEvent;
import android.app.Instrumentation;
import android.support.v4.content.LocalBroadcastManager;
//end
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
/**
 * Created by mashuai on 2016/9/29.
 */
public class ControllerService extends Service {

    private final static String TAG = "ControllerService";

    public final static String BOOT_COMPLETE_FLAG = "boot_completed";
    public final static String BLUETOOTH_CONNECTED_SUCCESS ="bluetooth_connected";
    public final static String BLUETOOTH_DISCONNECTED = "bluetooth_disconnected";
    public final static String BLUETOOTH_DEVICE_OBJECT = "bluetooth_device";
    private final static boolean DEBUG = false;

    //runAsDaemonService means start when boot, and not allow stop
    public final static boolean runAsDaemonService = true;

    private boolean lastButtonStatus = false;
    private int lastButton = 0;
    private IControllerService.Stub controllerService;
    private static IControllerListener controllerListener;

    private static BluetoothInputDevice mBtInputDeviceService = null;
    private static BluetoothDevice device;
    private static String device_name = null;
    private static String device_address = null;
    private BluetoothAdapter mAdapter;
    private boolean isBtInputDeviceConnected = false;

    public static final int JOYSTICK_CONTROL_TYPE                 = 0x01;
    public static final int JOYSTICK_REQUEST_TYPE                 = 0x02;
    public static final int JOYSTICK_HILLCREST_CALIBRATION_TYPE   = 0x03;
    public static final int JOYSTICK_TOUCH_CALIBRATION_TYPE       = 0x04;
    public static final int JOYSTICK_RESET_QUATERNION_TYPE        = 0x05;

    public static final int JOYSTICK_REQUEST_VERSION = 1;

    public static final int JOYSTICK_ENTER_MODE      = 1;
    public static final int JOYSTICK_EXIT_MODE       = 0;

    public static final int GET_DATA_TIMEOUT = -1;
    public static final int GET_INVALID_DATA = -2;
    public static final int REPORT_TYPE_ORIENTATION = 1;
    public static final int REPORT_TYPE_SENSOR = 2;
    public static final int REPORT_TYPE_VERSION = 3;
    public static final int REPORT_TYPE_SHAKE = 4;

    private static final int POLL_TIMEOUT_COUNT = 100;

    private static final int RAW_DATA_CHANNEL_NONE = -1;
    private static final int RAW_DATA_CHANNEL_JOYSTICK = 0;
    private static final int RAW_DATA_CHANNEL_EMULATOR = 1;

    private static final String ACTION_HAVE_GOT_HAND_VERSION_INFO   = "com.longcheer.net.action.gotHandVersionInfo";
    private static final String HAND_VERSION_INFO_NAME              = "hand_version_info_name";
    private static final String HAND_VERSION_INFO_ADDRESS           = "hand_version_info_address";
    private static final String HAND_VERSION_INFO_APPVERSION        = "hand_version_info_appversion";

    private static final String HAND_VERSION_ZERO                   = "0";
    private static final String HAND_VERSION_READY_TO_RAED          = "1";

    private String iDreamDeviceVersion = null;
    private String iDreamDeviceType = null;

    private Handler handler = new Handler();
    private static int button = 0;


    private static int count = 0;

    private static int controllerId=0;

    private Thread getNodeDataThread = null;

    private Thread getRFCommDataThread = null;

    private int dataChannel = RAW_DATA_CHANNEL_NONE;

    private static int hand_device_ota_status = -1;


    private LocalBroadcastManager localBroadcastManager;
    EventReceiver eventReceiver = new EventReceiver();
    private static boolean mVibrateClose = false;


    private BluetoothServerSocket mServerSocket;
    private BluetoothAdapter mBluetoothAdapter;

    private InputStream inputStream;
    private OutputStream outputStream;

    // for save last quans data
    private float last_quans_x;
    private float last_quans_y;
    private float last_quans_z;
    private float last_quans_w;

    // when connected hand device, we get hand version once
    private boolean needGetHandVersion = false;



    public static void debug_log(String log){
        // setprop log.tag.TAG DEBUG ,we can print log
        if(DEBUG || Log.isLoggable(TAG, Log.DEBUG)){
            Log.d(TAG,log);
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mAdapter.getProfileProxy(getBaseContext(), mServiceListener,
                BluetoothProfile.INPUT_DEVICE)) {
            Log.w(TAG, "Cannot obtain profile proxy");
            return;
        }

        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction("OPEN_VIBRATOR_ACTION");
        filter.addAction("OPEN_VIBRATOR_TIME_ACTION");
        filter.addAction("OPEN_VIBRATOR_MODE_ACTION");
        filter.addAction("OPEN_VIBRATOR_REPEAT_ACTION");
        filter.addAction("CLOSE_VIBRATOR_ACTION");
        filter.addAction(AIDLControllerService.ACTION_GET_HAND_DEVICE_VERSION_INFO);
        filter.addAction("ENABLE_HOME_KEY_EVNET_ACTION");
        filter.addAction(AIDLControllerService.ACTION_CONTROL_HAND_DEVICE);
        Log.d(TAG,"registerReceiver");
        localBroadcastManager.registerReceiver(eventReceiver,filter);

        if(runAsDaemonService){
            startGetNodeDataThread();//begin to run thread
        }
        Log.d("myControllerService", "onCreate");
    }

    public class EventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if("OPEN_VIBRATOR_ACTION".equals(action)) {
                controlJoystickVibrate(80, 5);
            }else if("OPEN_VIBRATOR_TIME_ACTION".equals(action)) {
                long time = intent.getLongExtra("time", -1);
                controlJoystickVibrate(80, (int)time);
            }else if("OPEN_VIBRATOR_MODE_ACTION".equals(action)) {
                long time = intent.getLongExtra("time", -1);
                int mode = intent.getIntExtra("mode", -1);
                int power = 0;
                if(mode == 0){
                    power = 100;
                }else if(mode == 1){
                    power = 150;
                }else if(mode == 2){
                    power = 200;
                }
                controlJoystickVibrate(power, (int)time);
            }else if("OPEN_VIBRATOR_REPEAT_ACTION".equals(action)){
                mVibrateClose = false;
                if (DEBUG) {
                    Log.d("[GGG]","<<<<mVibrateClose:"+mVibrateClose);
                }
                long[] pattern = intent.getLongArrayExtra("pattern");
                int repeat = intent.getIntExtra("repeat", -1);
                if(pattern.length > 0 && repeat < pattern.length){
                    final long time = pattern[repeat];
                    new Thread(){
                        public void run(){
                            while(!mVibrateClose){
                                if (DEBUG) {
                                    Log.d("[GGG]", "time = " + time + " mVibrateClose:" + mVibrateClose);
                                }
                                controlJoystickVibrate(80, (int)time);
                                try {
                                    if (DEBUG) {
                                        Log.d("[GGG]", "Thread.sleep  time = " + time);
                                    }
                                    Thread.sleep(time*200);
                                }catch (Exception exception){
                                }
                            }
                        }
                    }.start();

                }
            }else if("CLOSE_VIBRATOR_ACTION".equals(action)){
                mVibrateClose = true;
                if (DEBUG) {
                    Log.d("[GGG]", "<<<CLOSE_VIBRATOR_ACTION mVibrateClose22:" + mVibrateClose);
                }
                controlJoystickVibrate(0, 0);
            }else if (AIDLControllerService.ACTION_GET_HAND_DEVICE_VERSION_INFO.equals(action)){
                getHandDeviceVersionInfo();
            }else if (AIDLControllerService.ACTION_CONTROL_HAND_DEVICE.equals(action)){
                int type = intent.getIntExtra(AIDLControllerService.CONTROL_HAND_DEVICE_TYPE, -1);
                int data1 = intent.getIntExtra(AIDLControllerService.CONTROL_HAND_DEVICE_DATA1, -1);
                int data2 = intent.getIntExtra(AIDLControllerService.CONTROL_HAND_DEVICE_DATA2, -1);
                controlHandDevice(type, data1, data2);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d("myControllerService", "onDestroy");
        localBroadcastManager.unregisterReceiver(eventReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null) return super.onStartCommand(intent,flags,startId);
        if(intent.getBooleanExtra(BOOT_COMPLETE_FLAG, false)){
            isCancel = false;
            startGetNodeDataThread();
        }else if(intent.getBooleanExtra(BLUETOOTH_CONNECTED_SUCCESS, false)){
            isCancel = false;
            device = intent.getParcelableExtra(ControllerService.BLUETOOTH_DEVICE_OBJECT);
            if(device!=null){
                device_name = device.getName();
                device_address = device.getAddress();
                Log.i(TAG,"get device name is:"+device.getName() + ", address:"+device_address);
            }else{
                debug_log("get device is null");
            }
            // we use broadcast to check if bt device is connected
            isBtInputDeviceConnected = true;
            resetHandDeviceVersionInfo();// reset recorded hand version info.
            startGetNodeDataThread();
            //start read hidrawx node
        }else if(intent.getBooleanExtra(BLUETOOTH_DISCONNECTED,false)){
            //stop read node
            debug_log("onStartCommand intent BLUETOOTH_DISCONNECTED, set isCancel=false");
            if (!runAsDaemonService) {
                isCancel = true;
            }
            device=null;
            device_name = null;
            device_address = null;
            isBtInputDeviceConnected = false;
			//AIDLControllerUtil.mBatterLevel = "";
            batterLevelEvent(-1);
        }else if(intent.getBooleanExtra("test_vibrate", false)){//for test vibrate
            controlJoystickVibrate(80, 5);
        }else if(intent.getBooleanExtra(ControllerRec.TEST_GET_HAND_VERSION, false)){
            getHandDeviceVersionInfo();
        }else if(intent.getBooleanExtra(ControllerRec.TEST_RESET_QUATERNION, false)){
            requestHandDeviceResetQuternion();
        }else if(intent.getBooleanExtra(ControllerRec.TEST_REQUEST_CALIBRATION, false)){
            int type = intent.getIntExtra(ControllerRec.REQUEST_CALIBRATION_TYPE, -1);
            int mode = intent.getIntExtra(ControllerRec.REQUEST_CALIBRATION_MODE, -1);
            requestHandDeviceCalibration(type, mode);
        } else if (intent.getBooleanExtra(ControllerRec.HAND_OTA_ACTION, false)) {
            hand_device_ota_status = intent.getIntExtra(ControllerRec.HAND_OTA_STATUS, -1);
        }
        return Service.START_REDELIVER_INTENT;
    }

 //   @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("myControllerService", "onBind " + intent.getAction());
        this.startService(new Intent(this, ControllerService.class));
        if (ControllerServiceConsts.BIND_INTENT_ACTION.equals(intent.getAction())) {
            return controllerService.asBinder();
        }
        return null;
    }


    public ControllerService() {
    this.controllerService = new IControllerService.Stub() {

        @Override
        public int initialize(int targetApiVersion) throws RemoteException {
            Log.d("ControllerService", "initialize(" + targetApiVersion + ")");
            return 0; //com.google.vr.sdk.controller.ControllerManager.ApiStatus.OK = 0
        }

        @Override
        public void recenter(int controllerId) throws RemoteException {
            Log.d("ControllerService", "recenter(" + controllerId + ")");
            ControllerService.this.controllerId = controllerId;
        }

        @Override
        public boolean registerListener(final int controllerId, final String key, final IControllerListener listener) throws RemoteException {
            Log.d("ControllerService", "registerListener(" + controllerId + ", " + key + ", " + listener.getClass().getName() + ")");
            if (controllerId != 0) {
                return false;
            }
            ControllerService.this.controllerId = controllerId;
            handler.post(new Runnable() {
                @Override
                public final void run() {
                    ControllerService.this.registerListener(listener);
                }
            });
            return true;
        }

        @Override
        public boolean unregisterListener(String key) throws RemoteException {
            Log.d("ControllerService", "unregisterListener(" + key + ")");
            return ControllerService.this.unregisterListener();
        }
    };
    }
    public final void registerListener(IControllerListener listener) {
        Log.d(TAG,"registerListener,set controllerListener");
        controllerListener = listener;

//        isCancel = false;
//        startGetNodeDataThread();
    }

    public final boolean unregisterListener(){
        Log.d(TAG,"unregisterListener, set controllerListener to null");
        controllerListener = null;
//        isCancel = true;
        return true;
    }

    private final ServiceListener mServiceListener = new ServiceListener() {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            debug_log("serivceconnected profile:"+profile);
            if (profile != BluetoothProfile.INPUT_DEVICE) return;

            Log.i(TAG, "Profile proxy connected");

//            isBtInputDeviceConnected = true;
            mBtInputDeviceService = (BluetoothInputDevice) proxy;
        }

        @Override
        public void onServiceDisconnected(int profile) {
            debug_log("serivcedisconnected profile:"+profile);
            if (profile != BluetoothProfile.INPUT_DEVICE) return;

            /* if still has devices connected, don't set isBtInputDeviceConnected=false
             * we check when open hidraw, use product & vendor id
            */
            if(mBtInputDeviceService != null){
                List<BluetoothDevice> deviceList = mBtInputDeviceService.getConnectedDevices();
                if(deviceList!=null && deviceList.size()>0){
                    return;
                }
            }
//            isBtInputDeviceConnected = false;
            Log.i(TAG, "Profile proxy disconnected");
            mBtInputDeviceService = null;
        }
    };

    private boolean isCancel = false;

    private String[] device_path = new String[]{"/dev/hidraw0", "/dev/hidraw1", "/dev/hidraw2"};// read 32Byte/time

    static{
        try{
            System.loadLibrary("lctgetnode");
        }catch(Exception e){
            e.printStackTrace();
            Log.d(TAG,e.getMessage());
        }
    }

    /*
     * -1, is ok
     * 0 is hidraw0
     * 1 is hidraw1
     * 2 is hidraw2
     *
     * only getNodeDataThread use it .other thread can't use!!!!!!!
     */
    public native int nativeOpenFile();
    public native Bt_node_data nativeReadFile();
    public native int nativeWriteFile(int type, int data1, int data2);
    public native int nativeCloseFile();

//    private void scheduleNext(){
//
//    }

    private static void controllerServiceSleep(int flag, long ms){
//        Log.d(TAG,"sleep "+ms+"s"+", flag:"+flag);;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void setControllerListenerConnected(){
        try {
            if (controllerListener != null) {
                debug_log("set Controller state CONNECTED!");
                controllerListener.onControllerStateChanged(controllerId,
                        ControllerStates.CONNECTED);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    private void setControllerListenerDisconnected(){
        try {
            if (controllerListener != null) {
                debug_log("set Controller state DISCONNECTED!");
                controllerListener.onControllerStateChanged(controllerId,
                        ControllerStates.DISCONNECTED);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    private int  disposeNodeData(int channel, Bt_node_data nodeData, int timeoutCount){
        debug_log("disposeRawData channel is :"+channel +", dataChannel:"+dataChannel + "controllerListener is null?"+(controllerListener == null));
        if (nodeData.type == REPORT_TYPE_ORIENTATION) {// quans
        // debug_log("nodeData:x:" + nodeData.quans_x + ", y:"
        // + nodeData.quans_y + ",z:" + nodeData.quans_z + ",w:"
        // + nodeData.quans_w);
            timeoutCount = 0;// timeout count reset to 0
            if (channel == dataChannel) {
                sendPhoneEventControllerOrientationEvent(nodeData.quans_x,
                        nodeData.quans_y,
                        nodeData.quans_z,
                        nodeData.quans_w);
                debug_log("send phon event finish");
                last_quans_x = nodeData.quans_x;
                last_quans_y = nodeData.quans_y;
                last_quans_z = nodeData.quans_z;
                last_quans_w = nodeData.quans_w;
            }
        } else if (nodeData.type == REPORT_TYPE_SENSOR) {
            timeoutCount = 0;// timeout count reset to 0
            debug_log("nodeData.gyro x:" + nodeData.gyro_x + ", y:"
                    + nodeData.gyro_y + ", z:" + nodeData.gyro_z + ", acc x:"
                    + nodeData.acc_x + ", y:" + nodeData.acc_y + ", z:"
                    + nodeData.acc_z + ", touchX:" + nodeData.touchX
                    + ", touchY:" + nodeData.touchY + ", keymask:"
                    + nodeData.keymask);
            //schedule channel
            if((channel != dataChannel && (nodeData.keymask&0x01) != 0) || RAW_DATA_CHANNEL_NONE == dataChannel){
                dataChannel = channel;
            }
            if (channel == dataChannel) {
                sendPhoneEventControllerAccAndGyroEvent(nodeData.gyro_x,
                        nodeData.gyro_y, nodeData.gyro_z, nodeData.acc_x,
                        nodeData.acc_y, nodeData.acc_z);
                sendPhoneEventControllerButtonEvent(nodeData.keymask);
                sendPhoneEventControllerTouchPadEvent(nodeData.touchX, nodeData.touchY);
                debug_log("send acc button touch event finish");
            }
            debug_log("battery:" + nodeData.bat_level);
            // AIDLControllerUtil.mBatterLevel = String.valueOf(nodeData.bat_level);
            if (mLastBatterLevel != nodeData.bat_level) {
                mLastBatterLevel = nodeData.bat_level;
                batterLevelEvent(nodeData.bat_level);
            }

            // send broadcast to notify the hand shank's battery
        }else if (nodeData.type == REPORT_TYPE_VERSION) {
            timeoutCount = 0;// timeout count reset to 0
            Log.d(TAG,"nodeData appVersion:" + nodeData.appVersion + ", deviceVersion:" + nodeData.deviceVersion + ", deviceType:" + nodeData.deviceType);
            if(channel == dataChannel){
                handDeviceVersionInfoEvent(nodeData.appVersion, nodeData.deviceVersion, nodeData.deviceType);
                //record device version info
                recordHandDeviceVersionInfo(nodeData.appVersion, nodeData.deviceVersion, nodeData.deviceType);
            }
        }else if (nodeData.type == REPORT_TYPE_SHAKE){
            debug_log("nodeData.timeStamp :" + nodeData.timeStamp + ", nodeData.shakeEvent :" + nodeData.shakeEvent + ", nodeData.eventParameter:" + nodeData.eventParameter);
            if (channel == dataChannel) {
                shakeEvent(nodeData.timeStamp, nodeData.shakeEvent, nodeData.eventParameter);
            }
      } else if(nodeData.type == GET_DATA_TIMEOUT){
          timeoutCount++;
          debug_log("no data to read, block timeout, timeoutCount:"+timeoutCount);
          if (timeoutCount > POLL_TIMEOUT_COUNT) {
              //recenter
//            sendPhoneEventControllerOrientationEvent(0, 0, 0, 1);
              sendPhoneEventControllerOrientationEvent(last_quans_x,
                      last_quans_y,
                      last_quans_z,
                      last_quans_w);
              debug_log("send last data which timeout count is more than 5");
          }
      } else if(nodeData.type == GET_INVALID_DATA){
          Log.e(TAG, "get invalid data ");
      } else {
          Log.e(TAG,"other err when get node data");
      }
      return timeoutCount;
    }
    private Runnable getNodeDataRunnable = new Runnable() {
        @Override
        public void run() {
            // THREAD_PRIORITY_URGENT_DISPLAY THREAD_PRIORITY_URGENT_AUDIO
            android.os.Process
                    .setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            try {
                boolean needOpenFile = true;
                int timeoutCount = 0;
                int getHandVersionCount = 0;
                while (!isCancel) {
                    // since ACTION_ACL_CONNECTED sometimes delays more than 20s. we do not use this to verify
                    if (false) {
                        // if connect bt device is not hid device, sleep , and do next while
                        if (!isBtInputDeviceConnected) {
                            controllerServiceSleep(1, 300);
                            continue;
                        }
                    }
                    if (needOpenFile) {
                        int res = nativeOpenFile();
                        if (res < 0) {
                            needOpenFile = true;
//                            Log.e(TAG, "native open file failed");
                            controllerServiceSleep(2, 800);
                            continue;
                        }
                        needOpenFile = false;
//                        resetHandDeviceVersionInfo();// clean hand device version
//                        needGetHandVersion = true;
                        Log.d(TAG, "natvie Open File Success");
                    }
                    setControllerListenerConnected();

                    Bt_node_data nodeData = nativeReadFile();
                    if (nodeData == null) {
                        Log.e(TAG,
                                "do not get hidraw data from native, schedule next open data node");
                        if (dataChannel == RAW_DATA_CHANNEL_JOYSTICK) {
                            setControllerListenerDisconnected();
                            dataChannel = RAW_DATA_CHANNEL_NONE;//reset dataChannel
                        }
                        nativeCloseFile();
                        needOpenFile = true;
                        //resetHandDeviceVersionInfo();// clean hand device version
//                        controllerServiceSleep(4, 3000);
                        continue;
                    }
                    //record timeoutCount
                    timeoutCount = disposeNodeData(RAW_DATA_CHANNEL_JOYSTICK, nodeData, timeoutCount);

                    //we use getHandVersionCount to record, if still need get hand version, we get once every 10 times
                    if(needGetHandVersion){
                        if (getHandVersionCount == 0) {
                            int result = getHandDeviceVersionInfo();
                            if (result < 0) {
                                Log.e(TAG, "when connect hand device,get hand device version err");
                                needGetHandVersion = false;
                            }
                        }
                        getHandVersionCount++;
                        if(getHandVersionCount > 100){
                            getHandVersionCount = 0;
                        }
                    }else{
                        getHandVersionCount = 0;
                    }
                }
                nativeCloseFile();
                Log.d(TAG, "natvie Close File");
            }
            finally {
                isCancel = true;
                Log.d(TAG, "finally, set Controller state DISCONNECTED");
                setControllerListenerDisconnected();
            }
        }
    };
    private static final String NAME = "BTAcceptThread";
    public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
    private static final UUID MY_UUID = UUID.fromString("00001102-0001-1001-8001-00805F9B34FC");


    char[] hexBuf;
    char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    protected String toHexString(byte[] bytes, int size) {
        int v, index = 0;
        hexBuf = new char[1024];
        for (int j = 0; j < size; j++) {
            v = bytes[j] & 0xFF;
            hexBuf[index++] = '0';
            hexBuf[index++] = 'x';
            hexBuf[index++] = hexArray[v >> 4];
            hexBuf[index++] = hexArray[v & 0x0F];
            hexBuf[index++] = ' ';
            if ((j + 1) % 16 == 0)
                hexBuf[index++] = '\n';
        }
        return new String(hexBuf, 0, index);
    }

    public static byte[] deleteAt(byte[] bs, int index)
    {
        int length = bs.length - 1;
        byte[] ret = new byte[length];

        System.arraycopy(bs, 0, ret, 0, index);
        System.arraycopy(bs, index + 1, ret, index, length - index);

        return ret;
    }
    private Bt_node_data decodeRFCommRawData(byte[] buffer){
        Bt_node_data node_data = new Bt_node_data();
        buffer = deleteAt(buffer, 0);
        int temp_value = (int) buffer[2] & 0xff;

        if (temp_value == 1) {
            float[] quans = new float[4];
            int index = 2;
            // float[] quans_2 = new float[4];
            for (int i = 0; i < 4; i++) {
                int result = (((int) buffer[6 + i * 4] << 24) & 0xFF000000) |
                        (((int) buffer[5 + i * 4] << 16) & 0x00FF0000) |
                        (((int) buffer[4 + i * 4] << 8) & 0x0000FF00) |
                        (((int) buffer[3 + i * 4] << 0) & 0x000000FF);
                quans[3 - i] = Float.intBitsToFloat(result);

            }
             debug_log("result: x= " + quans[0] + " y= " + quans[1] + " z= " +
             quans[2] + " w= " + quans[3]);

             node_data.type = REPORT_TYPE_ORIENTATION;
             node_data.quans_x = quans[0];
             node_data.quans_y = quans[1];
             node_data.quans_z = quans[2];
             node_data.quans_w = quans[3];
        } else if (temp_value == 2) {
            int[] sensor = new int[6];
            for (int i = 0; i < 6; i++) {
                sensor[i] = (((int) buffer[4 + i * 2] << 8) & 0x0000FF00) |
                        (((int) buffer[3 + i * 2] << 0) & 0x000000FF);
                if ((sensor[i] & 0x8000) != 0) {
                    sensor[i] |= 0xFFFF0000;
                }
            }
            int touchX = ((int) buffer[15]) & 0x000000FF;
            int touchY = ((int) buffer[16]) & 0x000000FF;
            byte keymask = buffer[17];
             int battery = (((int)buffer[18]) & 0x000000FF) + 100;

             debug_log("mshuai, get data:gyro.x:" + sensor[0] + ", gyro.y:" +
             sensor[1] + ", gyro.z:" + sensor[2] + ", acc.x" + sensor[3] +
             ", acc.y:" + sensor[4] + ", acc.z:" + sensor[5]);
             debug_log("mshuai, get touchx:" + touchX + ", touchy:" + touchY);
             node_data.type = REPORT_TYPE_SENSOR;
             node_data.gyro_x = sensor[0];
             node_data.gyro_y = sensor[1];
             node_data.gyro_z = sensor[2];
             node_data.acc_x = sensor[3];
             node_data.acc_y = sensor[4];
             node_data.acc_z = sensor[5];
             node_data.touchX = touchX;
             node_data.touchY = touchY;
             node_data.bat_level = battery;
             node_data.keymask = keymask;
        } else {
            Log.e(TAG, "get node invalid data!!!");
            node_data.type = GET_INVALID_DATA;
        }
        return node_data;
    }
    private Runnable getRFCommDataRunnable = new Runnable(){
        @Override
        public void run() {
            android.os.Process
            .setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            BluetoothSocket socket = null;

            while (!isCancel) {
                try {
                    if (mAdapter != null && mAdapter.isEnabled()) {

//                        if (BluetoothAdapter.STATE_CONNECTED != mAdapter.getConnectionState()) {
//                            controllerServiceSleep(5, 1000);
//                            continue;
//                        }

                        // MY_UUID is the app's UUID string, also used by the client code
                        // 创建ServerSocket
                        // mServerSocket =
                        // mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
                        if (mServerSocket == null) {
                            mServerSocket = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                                    PROTOCOL_SCHEME_RFCOMM, MY_UUID);
                        }
                        Log.d(TAG,
                                "accept() waiting for client connection.... mServerSocket is null?"
                                        + (mServerSocket == null));
                        try {
                            socket = mServerSocket.accept();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Log.e(TAG, "accept() accept a client sucess socket =  " + socket);

                        while (socket != null && socket.isConnected()) {
//                        if(socket != null){
                            inputStream = socket.getInputStream();
                            byte[] buffer = new byte[21];
                            int bytes = 0;
                            Bt_node_data nodeData;
                            while (bytes != -1) {
                                try{
                                    bytes = inputStream.read(buffer);
                                    if(bytes <0) break;
                                }catch(IOException e){
                                    e.printStackTrace();
                                    break;
                                }
                                debug_log("hidraw data:" + toHexString(buffer, bytes));
//                                Log.e(TAG, "read value = " + new String(buffer, 0, bytes, "utf-8"));
                                setControllerListenerConnected();
                                nodeData = decodeRFCommRawData(buffer);
                                disposeNodeData(RAW_DATA_CHANNEL_EMULATOR, nodeData, 0);
                            }
                            Log.d(TAG,"read inputStream err, re accept");
                            if (socket != null) {
                                socket.close();
                                socket = null;
                            }
                            if(inputStream != null){
                                inputStream.close();
                                inputStream =null;
                            }
//                            controllerServiceSleep(6, 3000);
                        }

                    }else{
                        //if bt is closed
                        debug_log("bt adapter is null ,sleep 3s");
                        if (mServerSocket != null) {
                            try {
                                mServerSocket.close();
                                mServerSocket = null;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        controllerServiceSleep(8, 3000);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    Log.e(TAG, "socket disconnected, reconnect again");
                    if (dataChannel == RAW_DATA_CHANNEL_EMULATOR) {
                        setControllerListenerDisconnected();
                        dataChannel = RAW_DATA_CHANNEL_NONE;//reset dataChannel
                    }
//                    controllerServiceSleep(7, 3000);
                }
            }
        }
    };
    private void startGetNodeDataThread() {
        if (getNodeDataThread == null) {
            getNodeDataThread = new Thread(getNodeDataRunnable);
        }
        if (getRFCommDataThread == null) {
            getRFCommDataThread = new Thread(getRFCommDataRunnable);
        }
        if (!getNodeDataThread.isAlive()) {
            getNodeDataThread.start();
        }
        if(!getRFCommDataThread.isAlive()){
            getRFCommDataThread.start();
        }
    }

    /*
     * -1 is not ok
     * 0 is ok
     */
    //default powerLevel 80, ms:5(500ms)
    public int controlJoystickVibrate(){
        if(hand_device_ota_status == ControllerRec.STATUS_HAND_OTA_STARTING){
            Log.w(TAG,"hand device is still ota, can't control hand device");
            return -1;
        }
        int res = nativeWriteFile(JOYSTICK_CONTROL_TYPE, 80, 5);
        debug_log("controlJoystickVibrate defaultvalue res:"+res);
        return res;
    }
    public int controlJoystickVibrate(int powerLevel, int millisceonds){
        if(hand_device_ota_status == ControllerRec.STATUS_HAND_OTA_STARTING){
            Log.w(TAG,"hand device is still ota, can't control hand device");
            return -1;
        }
        int res = nativeWriteFile(JOYSTICK_CONTROL_TYPE, powerLevel, millisceonds);
        debug_log("controlJoystickVibrate res:"+res);
        return res;
    }

    private int getHandDeviceVersionInfo(){
        if(hand_device_ota_status == ControllerRec.STATUS_HAND_OTA_STARTING){
            Log.w(TAG,"hand device is still ota, can't control hand device");
            return -1;
        }
        int res = nativeWriteFile(JOYSTICK_REQUEST_TYPE, JOYSTICK_REQUEST_VERSION, 0);
        debug_log("get hand device version info, res is :" +res);
        return res;
    }

    private void sendBroadcastForHandDeviceOTA(int appVersion){
        Intent intent = new Intent();
        intent.setAction(ACTION_HAVE_GOT_HAND_VERSION_INFO);
        intent.putExtra(HAND_VERSION_INFO_NAME, device_name);
        intent.putExtra(HAND_VERSION_INFO_ADDRESS, device_address);
        intent.putExtra(HAND_VERSION_INFO_APPVERSION, appVersion);
        sendBroadcast(intent);
        Log.i(TAG,"sendBroadcastForHandDeviceOTA, name:"+device_name+", address:"+device_address);
    }
    private void recordHandDeviceVersionInfo(final int appVersion, int deviceVersion, int deviceType){
        SystemProperties.set("sys.iqiyi.hand.appVersion", String.format("%06x", appVersion & 0xffffff));
        SystemProperties.set("sys.iqiyi.hand.deviceVersion", String.format("%04x", deviceVersion & 0xffff));
        SystemProperties.set("sys.iqiyi.hand.deviceType", String.format("%04x", deviceType & 0xffff));

        Log.i(TAG,"record hand device version info && send a broadcast for ota");

        //since we get hand version info before bt broadcast, sometimes device name or address are null.
        // so we should wait 5 * 0.8s. if timeout, we waive this time.
        new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                int count = 0;
                while (device_name == null || device_address == null) {
                    count++;
                    try {
                        Thread.sleep(800);
                    } catch (Exception exception) {
                    }
                    if (count > 5) {
                        Log.w(TAG, "we haven't got device name or address, return");
                        return;
                    }
                }
                sendBroadcastForHandDeviceOTA(appVersion);
            }
        }).start();
//        debug_log("record hand device version, appVersion:"+appVersion+", deviceVersion:"+deviceVersion+", deviceType:"+deviceType);
        needGetHandVersion = false;
    }

    private void resetHandDeviceVersionInfo(){
        SystemProperties.set("sys.iqiyi.hand.appVersion", HAND_VERSION_READY_TO_RAED);
        SystemProperties.set("sys.iqiyi.hand.deviceVersion", HAND_VERSION_READY_TO_RAED);
        SystemProperties.set("sys.iqiyi.hand.deviceType", HAND_VERSION_READY_TO_RAED);
        needGetHandVersion = true;
    }

    private int requestHandDeviceResetQuternion(){
        if(hand_device_ota_status == ControllerRec.STATUS_HAND_OTA_STARTING){
            Log.w(TAG,"hand device is still ota, can't control hand device");
            return -1;
        }
        int res = nativeWriteFile(JOYSTICK_RESET_QUATERNION_TYPE, 0, 0);
        Log.d(TAG,"requestHandDeviceResetQuaternion res:"+res);
        return res;
    }

    private int requestHandDeviceCalibration(int type, int mode){
        if(hand_device_ota_status == ControllerRec.STATUS_HAND_OTA_STARTING){
            Log.w(TAG,"hand device is still ota, can't control hand device");
            return -1;
        }
        int res = -1;
        if((JOYSTICK_HILLCREST_CALIBRATION_TYPE == type || JOYSTICK_TOUCH_CALIBRATION_TYPE == type)
                && (JOYSTICK_ENTER_MODE == mode || JOYSTICK_EXIT_MODE == mode)) {
            res = nativeWriteFile(type, mode, 0);
            Log.e("myController","requestHandDeviceCalibration nativeWrite");
        }
        Log.e("myController", "requestHandDeviceCalibration type:" + type + ", mode:" + mode
                + ", res:" + res);
        return res;
    }

    // data1 sometimes is mode
    private int controlHandDevice(int type, int data1, int data2) {
        if(hand_device_ota_status == ControllerRec.STATUS_HAND_OTA_STARTING){
            Log.w(TAG,"hand device is still ota, can't control hand device");
            return -1;
        }
        int res = -1;
        switch (type) {
            case JOYSTICK_CONTROL_TYPE:
                res = controlJoystickVibrate(data1, data2);
                break;
            case JOYSTICK_REQUEST_TYPE:
                res = getHandDeviceVersionInfo();
                break;
            case JOYSTICK_HILLCREST_CALIBRATION_TYPE:
            case JOYSTICK_TOUCH_CALIBRATION_TYPE:
                res = requestHandDeviceCalibration(type, data1);
                break;
            case JOYSTICK_RESET_QUATERNION_TYPE:
                res = requestHandDeviceResetQuternion();
                break;
            default:
                Log.w(TAG, "get invaild type data");
                break;
        }
        Log.d(TAG, "controllHandDevice type:" + type + ", data1 or mode:" + data1 + ", data2:"
                + data2 + ", res:" + res);
        return res;
    }


    private final ControllerTouchEvent controllerTouchEvent = new ControllerTouchEvent();
    private final ControllerGyroEvent controllerGyroEvent = new ControllerGyroEvent();
    private final ControllerAccelEvent controllerAccelEvent = new ControllerAccelEvent();
    private static final ControllerOrientationEvent controllerOrientationEvent = new ControllerOrientationEvent();
    private static final ControllerButtonEvent controllerButtonEvent = new ControllerButtonEvent();
//    private static final ControllerEventPacket cep = new ControllerEventPacket();
    private void sendPhoneEventControllerOrientationEvent(float x, float y, float z, float w){


        controllerOrientationEvent.qx = -x;
        controllerOrientationEvent.qy = -z;
        controllerOrientationEvent.qz = y;
        controllerOrientationEvent.qw = w;

        //add by zhangyawen for quansData jar (controllerService.java)
        debug_log("[PPP] X:" + controllerOrientationEvent.qx + ", Y:"
                + controllerOrientationEvent.qy + ",Z:" + controllerOrientationEvent.qz + ",W:"
                + controllerOrientationEvent.qw);
        quanDataEvent(controllerOrientationEvent.qx,
                controllerOrientationEvent.qy,
                controllerOrientationEvent.qz,
                controllerOrientationEvent.qw);
        //end
        //Log.d("[QQQ]","x= "+controllerOrientationEvent.qx+" y = "+controllerOrientationEvent.qy+" z = "+controllerOrientationEvent.qz+" w = "+controllerOrientationEvent.qw);

        controllerOrientationEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();
        try {
            if(controllerListener!=null){
//                controllerListener.deprecatedOnControllerOrientationEvent(controllerOrientationEvent); //must be send
              controllerListener.onControllerOrientationEvent(controllerOrientationEvent); //must be send
            } else {
                debug_log("when send orientation event, controllerListener is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        debug_log("OrientationX:" + controllerOrientationEvent.qx + ", Y:"
                + controllerOrientationEvent.qy + ",Z:" + controllerOrientationEvent.qz + ",W:"
                + controllerOrientationEvent.qw);
    }

    private void sendButtonEvent(){
        try {
            if (controllerListener != null) {
//                controllerListener.deprecatedOnControllerButtonEvent(controllerButtonEvent);
                 controllerListener.onControllerButtonEvent(controllerButtonEvent);

            } else {
                debug_log("when send button event, controllerListener is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    private void sendPhoneEventControllerButtonEvent(byte keymask){
        int button = ControllerButtonEvent.BUTTON_NONE;
        boolean buttonActionDown = false;

        //add by zhangyawen
        if (DEBUG) {
            Log.d("[KKK]","keymask = "+keymask);
        }
        ButtonEvent(keymask);
        //end
        if ((keymask&0x01) != 0) {
            //click or ok
            button = ControllerButtonEvent.BUTTON_CLICK;
        }else if ((keymask&0x02) != 0) {
            //back: handle this back event by AIDLControllerService
            button = ControllerButtonEvent.BUTTON_NONE;
        }else if ((keymask&0x04) != 0) {
            //trigger treat as touch pad click
            if (DEBUG) {
                Log.d("[YYY]","keymask = "+keymask+" trigger treat as touch pad click");
            }
            button = ControllerButtonEvent.BUTTON_CLICK;
        }else if ((keymask&0x08) != 0) {
            //home
            button = ControllerButtonEvent.BUTTON_HOME;
        }else if ((keymask&0x10) != 0) {
            //menu or app
            button = ControllerButtonEvent.BUTTON_APP;
        }else if ((keymask&0x20) != 0) {
            //volume up
            button = ControllerButtonEvent.BUTTON_VOLUME_UP;
        }else if ((keymask&0x40) != 0) {
            //volume down
            button = ControllerButtonEvent.BUTTON_VOLUME_DOWN;
        }else{
            // none
            button = ControllerButtonEvent.BUTTON_NONE;
        }
        buttonActionDown = button != ControllerButtonEvent.BUTTON_NONE;
        controllerButtonEvent.button = button;

        // if last button status is key down, this time send key up event
        if(button == ControllerButtonEvent.BUTTON_NONE && lastButton != ControllerButtonEvent.BUTTON_NONE){
            controllerButtonEvent.button = lastButton;
        }/*else if (button != ControllerButtonEvent.BUTTON_NONE && lastButton != ControllerButtonEvent.BUTTON_NONE && lastButton != button){
            // last not none, thie time not none, and this time do not equal last .
            controllerButtonEvent.button = lastButton;
            controllerButtonEvent.down = false;
            sendButtonEvent();
            controllerButtonEvent.button = button;
        }*/
        else if (button == ControllerButtonEvent.BUTTON_NONE && lastButton == ControllerButtonEvent.BUTTON_NONE){
            return;
        }
        controllerButtonEvent.down = buttonActionDown;

        debug_log("mshuai, buttonevent button:" + button + ", isDown? :" + buttonActionDown + ", lastButtonStatus:" + lastButtonStatus);
        // if last time is not down, this time still not down ,do not send event
        if(lastButtonStatus != buttonActionDown) {
            sendButtonEvent();
        }
        lastButton = button;
        lastButtonStatus = buttonActionDown;
    }
    private void sendPhoneEventControllerAccAndGyroEvent(float gyrpX, float gyrpY, float gyrpZ, float accX, float accY, float accZ){
        controllerGyroEvent.x = gyrpX;
        controllerGyroEvent.y = gyrpY;
        controllerGyroEvent.z = gyrpZ;

        //add by zhangyawen for Gyro
        gyroDataEvent(gyrpX,gyrpY,gyrpZ);
        accelDataEvent(accX,accY,-accZ);
        //end

        controllerGyroEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();
        try {
            if (controllerListener != null) {
//                controllerListener.deprecatedOnControllerGyroEvent(controllerGyroEvent); // probably not used
                 controllerListener.onControllerGyroEvent(controllerGyroEvent); //probably not used
            } else {
                debug_log("when send Gyro event, controllerListener is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        controllerAccelEvent.x = accX;
        controllerAccelEvent.y = accY;
        controllerAccelEvent.z = -accZ;

        controllerAccelEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();
        try {
            if (controllerListener != null) {
//                controllerListener.deprecatedOnControllerAccelEvent(controllerAccelEvent); //probably not used
              controllerListener.onControllerAccelEvent(controllerAccelEvent); //probably not used
            } else {
                debug_log("when send Accel event, controllerListener is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    private float preTouchX = 0;
    private float preTouchY = 0;
    private void sendPhoneEventControllerTouchPadEvent(float touchX, float touchY){
        simulationSystemEvent(touchX,touchY);

        //add by zhangyawen
        touchDataEvent(touchX,touchY);
        //end

        int action = ControllerTouchEvent.ACTION_NONE;

        if(touchX !=0 || touchY != 0){
            if(preTouchX != 0 || preTouchY != 0){
                action = ControllerTouchEvent.ACTION_MOVE;
            }else{
                action = ControllerTouchEvent.ACTION_DOWN;
            }
        }else {
            if(preTouchX != 0 || preTouchY != 0){
                action = ControllerTouchEvent.ACTION_UP;
            }else {
                action = ControllerTouchEvent.ACTION_NONE;
                return;
            }
        }

        debug_log("touchX:" + touchX + " touchY:" + touchY + " preTouchX:" + preTouchX + " preTouchY:"
                + preTouchY + " action:"+action);
        controllerTouchEvent.action = action;
        controllerTouchEvent.fingerId = 0;//event.motionEvent.pointers[0].getId();
        if (action == ControllerTouchEvent.ACTION_UP) {
            controllerTouchEvent.x = preTouchX;
            controllerTouchEvent.y = preTouchY;
        }else{
            controllerTouchEvent.x = touchX;
            controllerTouchEvent.y = touchY;
        }
        preTouchX = touchX;
        preTouchY = touchY;

        controllerTouchEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();
        try {
            if (controllerListener != null) {
//                controllerListener.deprecatedOnControllerTouchEvent(controllerTouchEvent);
              controllerListener.onControllerTouchEvent(controllerTouchEvent);
            } else {
                debug_log("when send Touch event, controllerListener is null");
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public static void OnPhoneEvent() throws RemoteException {
        if(controllerListener == null){
            Log.e("myControllerService","controllerListener is null");
            return;
        }
        if (button == ControllerButtonEvent.BUTTON_NONE) {
            button++;
        } else if (button == ControllerButtonEvent.BUTTON_VOLUME_DOWN) {
            button = 0;
        }

        controllerButtonEvent.button = ControllerButtonEvent.BUTTON_HOME;
        count++;
        if(count % 2 == 1) {
            controllerButtonEvent.down = true;
        }else{
            controllerButtonEvent.down = false;
        }
        Log.d("myPhoneEvent","onPhoneEvent button:"+button+" buttonEvent:"+controllerButtonEvent.down);



//        controllerListener.deprecatedOnControllerButtonEvent(controllerButtonEvent);



        controllerOrientationEvent.qx = (float)-0.000061068754;
        controllerOrientationEvent.qy = (float) 0.009013792;
        controllerOrientationEvent.qz = (float) 0.0042907377;
        controllerOrientationEvent.qw = (float) 0.99995;

        controllerOrientationEvent.timestampNanos = SystemClock.currentThreadTimeMillis();

//        controllerListener.deprecatedOnControllerOrientationEvent(controllerOrientationEvent); //must be send
        controllerListener.onControllerOrientationEvent(controllerOrientationEvent); //must be send
    }


    //add by zhangyawen for system event
    private boolean isDone = false;
    private boolean isReseting = false;
    private boolean isNotShort = false;
    private boolean isOnlyOnce = true;
    private boolean isOutting = false;
    private boolean isVolumeOn = false;
    private boolean isWorking = false;
    private boolean isRecentering = false;
    private boolean isOnce = true;
/*    private boolean isVolumeUpOn = false;
    private boolean isVolumeDownOn = false;*/
    private int mLastKeyMask = 0;
    private int mLastBatterLevel = -1;
    private static final int DELAY_TIME = 500;
    private static final int DEFINE_LONG_TIME_FOR_HOME = 1*1000;
    private static final int DEFINE_NOT_SHORT_TIME_FOR_HOME = 500;
    private static final int DEFINE_LONG_TIME_FOR_BACK = 1*1000;
    private static final int DEFINE_LONG_TIME_FOR_VOLUME = 500;

    //short click back key
    public static final int BACK_BUTTON_DOWN = 100;
    public static final int BACK_BUTTON = 101;
    public static final int BACK_BUTTON_UP = 102;
    public static final int BACK_BUTTON_CANCEL = -1;
    public static final String BACK_SHORT_CLICK_EVENT_ACTION = "SHORT_CLICK_BACK_KEY_ACTION";

    public static final String BATTER_LEVEL_EVENT_ACTION = "BATTER_LEVEL_ACTION";

    //AppButton key
    public static final int APP_BUTTON_DOWN = 200;
    public static final int APP_BUTTON = 201;
    public static final int APP_BUTTON_UP = 202;
    //long click menu key
    public static final int MENU_RECENTERING = 204;
    public static final int MENU_RECENTERED = 205;

    public static final int APP_BUTTON_CANCEL = -2;
    public static final String APP_BUTTON_EVENT_ACTION = "APP_BUTTON_KEY_ACTION";

    //Trigger/Click key
    public static final int TRIGGER_BUTTON_DOWN = 300;
    public static final int TRIGGER_BUTTON = 301;
    public static final int TRIGGER_BUTTON_UP = 302;
    public static final int TRIGGER_BUTTON_CANCEL = -3;
    public static final String TRIGGER_BUTTON_EVENT_ACTION = "TRIGGER_BUTTON_KEY_ACTION";

    //long click home key
    public static final int HOME_RECENTERING = 104;
    public static final int HOME_RECENTERED = 105;

    //quan data event
    public static final String QUAN_DATA_EVENT_ACTION = "QUAN_DATA_EVENT_ACTION";

    //Shake
    public static final String SHAKE_EVENT_ACTION = "SHAKE_EVENT_ACTION";

    public static final int SYSTEM_EVENT_NOT_DEFINED_ID = -1;
    public static final int SYSTEM_EVENT_BACK_ID = 0;
    public static final int SYSTEM_EVENT_ENTER_ID = 1;
    public static final int SYSTEM_EVENT_UP_ID = 2;
    public static final int SYSTEM_EVENT_DOWN_ID = 3;
    public static final int SYSTEM_EVENT_LEFT_ID = 4;
    public static final int SYSTEM_EVENT_RIGHT_ID = 5;
    public static final int SYSTEM_EVENT_HOME_ID = 6;
    public static final int SYSTEM_EVENT_VOLUME_UP_ID = 7;
    public static final int SYSTEM_EVENT_VOLUME_DOWN_ID = 8;

    //touch event
   // private  float swipe_Horizontal_YMax = 0.9f;
    //private  float swipe_Horizontal_YMin = 0.1f;
    //private  float swipe_Vertical_XMax = 0.9f;
    //private  float swipe_Vertical_XMin = 0.1f;
    private  float swipe_DragDistance = 0.3f;
    private  long timeSwipeDelay = 350L;
    private  float lastTouchPos_x = 0.0f;
    private  float lastTouchPos_y = 0.0f;
    private  float firstTouchPos_x = 0.0f;
    private  float firstTouchPos_y = 0.0f;
    private boolean mTouchDown = false;
    private boolean mIsTouching = false;
    private boolean mTouchUp = false;
    private boolean mCanSwipe = false;
    private long timeBeginSwipe = 0L;


    //short click home key
    public static final int HOME_DOWN = 106;
    public static final int HOME_UP = 107;

    //disable home key event for customer request
    public static boolean enableHomeKeyEvent = true;

    //disable menu key event for customer request
    public static boolean enableMenuKeyEvent = true;

    //timer1
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            isDone = false;
        }
    };

    //timer2
    private Runnable runnableForHome = new Runnable() {
        @Override
        public void run() {
            isReseting = true;
        }
    };
    private Runnable runnableForNotShort = new Runnable() {
        @Override
        public void run() {
            isNotShort = true;
        }
    };

    //timer3
    private Runnable runnableForBack = new Runnable() {
        @Override
        public void run() {
            isOutting = true;
        }
    };

    //timer4
    private Runnable runnableForMenu = new Runnable() {
        @Override
        public void run() {
            isRecentering = true;
        }
    };

    private Runnable runnableForVolume = new Runnable() {
        @Override
        public void run() {
            isVolumeOn = true;
        }
    };

    private Runnable runnableForVolumeUpOn = new Runnable() {
        @Override
        public void run() {
            if (isVolumeOn) {
                //send Volume up event every 200ms
                simulationButtonSystemEvent(32);
                handler.postDelayed(runnableForVolumeUpOn, 100);
            } else {
                //do nothing
            }
        }
    };
    private Runnable runnableForVolumeDownOn = new Runnable() {
        @Override
        public void run() {
            if (isVolumeOn) {
                //send Volume down event every 200ms
                simulationButtonSystemEvent(64);
                handler.postDelayed(runnableForVolumeUpOn, 100);
            } else {
                //do nothing
            }
        }
    };

    private void simulationButtonSystemEvent(int keymask){
        if ((keymask&0x01) != 0) {
            //click Panel
            if (DEBUG) {
                Log.d("[ZZZ]","click Panel (not match the Event)");
            }

        }else if ((keymask&0x02) != 0) {
            //back
            if (DEBUG) {
                Log.d("[ZZZ]", "back");
            }
            sendSystemEvent(SYSTEM_EVENT_BACK_ID);
        }else if ((keymask&0x04) != 0) {
            //trigger
            if (DEBUG) {
                Log.d("[ZZZ]", "trigger");
            }
            sendSystemEvent(SYSTEM_EVENT_ENTER_ID);
        }else if ((keymask&0x08) != 0) {
            //home
            if (DEBUG) {
                Log.d("[ZZZ]", "home (only home event)");
            }
            sendSystemEvent(SYSTEM_EVENT_HOME_ID);
        }else if ((keymask&0x10) != 0) {
            //menu
            if (DEBUG) {
                Log.d("[ZZZ]", "menu (not match the Event)");
            }
        }else if ((keymask&0x20) != 0) {
            //volume up
            if (DEBUG) {
                Log.d("[ZZZ]", "volume up ");
            }
            sendSystemEvent(SYSTEM_EVENT_VOLUME_UP_ID);
        }else if ((keymask&0x40) != 0) {
            //volume down
            if (DEBUG) {
                Log.d("[ZZZ]", "volume down ");
            }
            sendSystemEvent(SYSTEM_EVENT_VOLUME_DOWN_ID);
        }else{
            // none
            if (DEBUG) {
                Log.d("[ZZZ]", "none (not match the Event)");
            }
        }
    }

    private void ButtonEvent( int keymask){
        if (keymask != 0) {
            if ((keymask&0x08) != 0) {
                // differ click or longclick for home key(1000ms)
                if (keymask != mLastKeyMask) {
                    handler.postDelayed(runnableForHome, DEFINE_LONG_TIME_FOR_HOME);
                    handler.postDelayed(runnableForNotShort, DEFINE_NOT_SHORT_TIME_FOR_HOME);
                    mLastKeyMask = keymask;
                    if (!enableHomeKeyEvent) {
                        HomeKeyLongClickEvent(HOME_DOWN);
                    }
                    Log.d("[HHH]", "Home Down");
                } else {
                    if (isReseting) {
                        // set Recentering state
                        if (isOnlyOnce) {
                            HomeKeyLongClickEvent(HOME_RECENTERING);
                            isOnlyOnce = false;
                            if (DEBUG) {
                                Log.d("[HHH]", "Home Recentering");
                            }
                        }
                    } else {
                        //do nothing
                    }
                }
            } else if((keymask&0x02) != 0) {
                // differ click or longclick for back key(1000ms)
                if (keymask != mLastKeyMask) {
                    handler.postDelayed(runnableForBack, DEFINE_LONG_TIME_FOR_BACK);
                    mLastKeyMask = keymask;
                    //set the state (ButtonDown)
                    backKeyShortClickEvent(BACK_BUTTON_DOWN);
                    if (DEBUG) {
                        Log.d("[ZZZ]","Back shortclick ButtonDown");
                    }
                } else {
                    if (isOutting) {
                        // do nothing
                        if (DEBUG) {
                            Log.d("[ZZZ]", "Back shortclick Button");
                        }
                    } else {
                        // set the state (Button)
                        backKeyShortClickEvent(BACK_BUTTON);
                        if (DEBUG) {
                            Log.d("[ZZZ]", "Back shortclick Button");
                        }
                    }
                }
            } else if ((keymask&0x01) !=0 || (keymask&0x04) != 0) {
                //trigger/click key event
                if (keymask != mLastKeyMask) {
                    TriggerAndClickEvent(TRIGGER_BUTTON_DOWN);
                    mLastKeyMask = keymask;
                    if (DEBUG) {
                        Log.d("[ZZZ]", "TriggerAndClickEvent Buttondown");
                    }
                } else {
                    TriggerAndClickEvent(TRIGGER_BUTTON);
                }
            } else if ((keymask&0x10) != 0) {
                // differ click or longclick for home key(1000ms)
                if (keymask != mLastKeyMask) {
                    handler.postDelayed(runnableForMenu, DEFINE_LONG_TIME_FOR_HOME);
                    appButtonEvent(APP_BUTTON_DOWN);
                    mLastKeyMask = keymask;
                    if (DEBUG) {
                        Log.d("[AAA]", "appButtonEvent Buttondown");
                    }
                } else {
                    if (isRecentering) {
                        // set Recentering state
                        if (isOnce) {
                            appButtonEvent(MENU_RECENTERING);
                            isOnce = false;
                            if (DEBUG) {
                                Log.d("[AAA]", "App longclick Recentering");
                            }
                        }
                    } else {
                        //do nothing
                    }
                    appButtonEvent(APP_BUTTON);
                }
                //appButton key event
/*                if (keymask != mLastKeyMask) {
                    appButtonEvent(APP_BUTTON_DOWN);
                    mLastKeyMask = keymask;
                    if (DEBUG) {
                        Log.d("[ZZZ]", "appButtonEvent Buttondown");
                    }
                } else {
                    appButtonEvent(APP_BUTTON);
                }*/
            } else if ((keymask&0x20) != 0) {
                // volume up key(500ms)
                if (keymask != mLastKeyMask) {
                    if (DEBUG) {
                        Log.d("[ZZZ]", "Volume up Event down");
                    }
                    simulationButtonSystemEvent(keymask);
                    handler.postDelayed(runnableForVolume, DEFINE_LONG_TIME_FOR_VOLUME);
                    mLastKeyMask = keymask;
                } else {
                    if (isVolumeOn && !isWorking) {
                        //volume up on
                        isWorking = true;
                        if (DEBUG) {
                            Log.d("[ZZZ]", "Volume up is on");
                        }
                        //handler.post(runnableForVolumeUpOn);
                        new Thread(){
                            public void run(){
                                while(isVolumeOn){
                                    // send volume up event every 50 ms
                                    simulationButtonSystemEvent(mLastKeyMask);
                                    try {
                                        if (DEBUG) {
                                            Log.d("[ZZZ]", "Thread.sleep time = 50 ms");
                                        }
                                        Thread.sleep(200);
                                    }catch (Exception exception){
                                    }
                                }
                            }
                        }.start();
                    } else {
                        //do nothing
                    }
                }
            } else if ((keymask&0x40) != 0) {
                // volume down key(500ms)
                if (keymask != mLastKeyMask) {
                    if (DEBUG) {
                        Log.d("[ZZZ]", "Volume down Event down");
                    }
                    simulationButtonSystemEvent(keymask);
                    handler.postDelayed(runnableForVolume, DEFINE_LONG_TIME_FOR_VOLUME);
                    mLastKeyMask = keymask;
                } else {
                    if (isVolumeOn && !isWorking) {
                        //volume down on
                        isWorking = true;
                        if (DEBUG) {
                            Log.d("[ZZZ]", "Volume down is on");
                        }
                        //handler.po;acfguwst(runnableForVolumeDownOn);
                        new Thread(){
                            public void run(){
                                while(isVolumeOn){
                                    // send volume down event every 50 ms
                                    simulationButtonSystemEvent(mLastKeyMask);
                                    try {
                                        if (DEBUG) {
                                            Log.d("[ZZZ]", "Thread.sleep time = 50 ms");
                                        }
                                        Thread.sleep(200);
                                    }catch (Exception exception){
                                    }
                                }
                            }
                        }.start();
                    } else {
                        //do nothing
                    }
                }
            }

        } else {
            Log.d("[SOS]","isReseting = "+isReseting+" isNotShort = "+isNotShort
                            +" mLastKeyMask = "+mLastKeyMask);
            if (isReseting) {
                // set Recentered state
                isReseting = false;
                isNotShort = false;
                isOnlyOnce = true;
                HomeKeyLongClickEvent(HOME_RECENTERED);
                if (!enableHomeKeyEvent) {
                    HomeKeyLongClickEvent(HOME_UP);
                }
                if (DEBUG) {
                    Log.d("[HHH]", "Home Recentered");
                }
            } else if(isNotShort){
                isNotShort = false;
                handler.removeCallbacks(runnableForHome);
                if (!enableHomeKeyEvent) {
                    HomeKeyLongClickEvent(HOME_UP);
                }
                Log.d("[HHH]", "Home isNotShort");
            } else if(isRecentering){
                // set Recentered state
                isRecentering = false;
                isOnce = true;
                appButtonEvent(MENU_RECENTERED);
                appButtonEvent(APP_BUTTON_UP);
                if (DEBUG) {
                    Log.d("[AAA]", "App longclick Recentered");
                }
            } else if (isOutting) {
                // set Out of app state
                isOutting = false;
                backKeyShortClickEvent(BACK_BUTTON_CANCEL);
                simulationButtonSystemEvent(mLastKeyMask);
            } else {
                if ((mLastKeyMask&0x08) != 0) {
                    handler.removeCallbacks(runnableForHome);
                    handler.removeCallbacks(runnableForNotShort);
                    Log.d("[HHH]","Home Up");
                    if (DEBUG) {
                        android.util.Log.d("[HHH]","enableHomeKeyEvent = "+enableHomeKeyEvent);
                    }
                    if (enableHomeKeyEvent) {
                        simulationButtonSystemEvent(mLastKeyMask);
                    }else{
                        HomeKeyLongClickEvent(HOME_UP);

                    }

                }
                if ((mLastKeyMask&0x02) != 0) {
                    handler.removeCallbacks(runnableForBack);
                    //set the state (ButtonUp)
                    backKeyShortClickEvent(BACK_BUTTON_UP);
                    if (DEBUG) {
                        Log.d("[ZZZ]", "Back shortclick ButtonUp");
                    }
                }
                if ((mLastKeyMask&0x01) !=0 || (mLastKeyMask&0x04) != 0) {
                    TriggerAndClickEvent(TRIGGER_BUTTON_UP);
                    sendSystemEvent(SYSTEM_EVENT_ENTER_ID);
                    if (DEBUG) {
                        Log.d("[ZZZ]", "TriggerAndClickEvent ButtonUp");
                    }
                }
                if ((mLastKeyMask&0x10) != 0) {
                    handler.removeCallbacks(runnableForMenu);
                    appButtonEvent(APP_BUTTON_UP);
                    if (DEBUG) {
                        Log.d("[AAA]", "appButtonEvent ButtonUp");
                    }
                }
                if ((mLastKeyMask&0x20) != 0 ) {
                    handler.removeCallbacks(runnableForVolume);
                    if (DEBUG) {
                        Log.d("[ZZZ]", "shortClick Volume up  ");
                    }
                }
                if ((mLastKeyMask&0x40) != 0) {
                    handler.removeCallbacks(runnableForVolume);
                    if (DEBUG) {
                        Log.d("[ZZZ]", "shortClick Volume down");
                    }
                }

            }
            if (isVolumeOn) {
                isVolumeOn = false;
                isWorking = false;
                if (DEBUG) {
                    Log.d("[ZZZ]", "isVolumeOn = " + isVolumeOn);
                }
            }
            mLastKeyMask = keymask;
        }
    }

    private void batterLevelEvent(int level){
        /*Intent intent = new Intent();
        intent.putExtra("level",level);
        intent.setAction(BATTER_LEVEL_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG,"batterLevelEvent.sendBroadcast(intent)");*/
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.BATTERY_LEVEL_EVENT,level,""));
    }

    private void HomeKeyLongClickEvent(int state){
        /*Intent intent = new Intent();
        intent.putExtra("state",state);
        intent.setAction(BACK_SHORT_CLICK_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG,"backKeyShortClickEvent.sendBroadcast(intent)");*/
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.LONG_CLICK_HOME_EVENT,state));
    }

    private void backKeyShortClickEvent(int state){
        /*Intent intent = new Intent();
        intent.putExtra("state",state);
        intent.setAction(BACK_SHORT_CLICK_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG,"backKeyShortClickEvent.sendBroadcast(intent)");*/
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.SHORT_CLICK_BACK_EVENT,state));
    }

    private void TriggerAndClickEvent(int state){
        /*Intent intent = new Intent();
        intent.putExtra("state",state);
        intent.setAction(TRIGGER_BUTTON_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG,"TriggerAndClickEvent.sendBroadcast(intent)");*/
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.TRIGGER_BUTTON_EVENT,state));
    }

    private void appButtonEvent(int state){
        /*Intent intent = new Intent();
        intent.putExtra("state",state);
        intent.setAction(APP_BUTTON_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG,"appButtonEvent.sendBroadcast(intent)");*/
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.APP_BUTTON_EVENT,state));
    }

    private void quanDataEvent(float x,float y,float z,float w){
        /*Intent intent = new Intent();
        intent.putExtra("quans",new float[]{x,y,z,w});
        intent.setAction(QUAN_DATA_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);*/
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.QUANS_DATA_EVENT, x,y,z,w));
        //Log.d("[SYS]","quanDataEvent.sendBroadcast(intent)");
    }

    private void gyroDataEvent(float gyro_x,float gyro_y,float gyro_z){
        //Log.d("[GYRO]","gyroDataEvent x = "+gyro_x+" y = "+gyro_y+" z = "+gyro_z);
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.GYRO_DATA_EVENT, gyro_x,gyro_y,gyro_z));

    }

    private void accelDataEvent(float acc_X, float acc_Y, float acc_Z){
        //Log.d("[ACCEL]","accelDataEvent x = "+acc_X+" y = "+acc_Y+" z = "+acc_Z);
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.ACCEL_DATA_EVENT, acc_X,acc_Y,acc_Z));
    }

    private void touchDataEvent(float touch_X, float touch_Y){
        //Log.d("[TOUCH]","touchDataEvent x = "+touch_X+" y = "+touch_Y);
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.TOUCH_DATA_EVENT, touch_X,touch_Y));
    }

    private void shakeEvent(int timeStamp,int Event,int eventParameter){
        /*Intent intent = new Intent();
        intent.putExtra("timeStamp",timeStamp);
        intent.putExtra("Event",Event);
        intent.putExtra("eventParameter",eventParameter);
        intent.setAction(SHAKE_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG,"shakeEvent.sendBroadcast(intent)");*/
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.SHAKE_EVENT, timeStamp,Event,eventParameter));
    }

    private void handDeviceVersionInfoEvent(int appVersion, int deviceVersion, int deviceType){
        EventInstance.getInstance().post(new MessageEvent(MessageEvent.VERSION_INFO_EVENT, appVersion, deviceVersion, deviceType));
    }

    private void simulationSystemEvent(float touchX, float touchY){
        //int witchEventId = matchEvent(touchX,touchY);
        int witchEventId = GetSwipeDirection(touchX,touchY);

        if (DEBUG) {
            Log.d("[YYY]","witchEventId = "+witchEventId);
        }
        sendSystemEvent(witchEventId);
/*        if (witchEventId != SYSTEM_EVENT_NOT_DEFINED_ID) {
            if (!isDone) {
                sendSystemEvent(witchEventId);
                //handler.removeCallbacks(runnable);
                handler.postDelayed(runnable, DELAY_TIME);
                isDone = true;
            } else {
                //do nothing
            }
        } else {
            handler.removeCallbacks(runnable);
            isDone = false;
        }*/
    }

    //滑动
    private int GetSwipeDirection(float touchX,float touchY)
    {
        int mDirection = SYSTEM_EVENT_NOT_DEFINED_ID;
        if (DEBUG) {
            Log.d("[TTT]", "GetSwipeDirection touchX = " + touchX + " ; touchY = " + touchY);
        }
        if (touchX != 0.0f || touchY != 0.0f) {
            if (lastTouchPos_x == 0.0f && lastTouchPos_y == 0.0f) {
                mTouchDown = true;
                mIsTouching = true;
                mTouchUp=false;
            } else {
              //  mIsTouching = true;
                mTouchDown = false;
            }
        } else {
            mTouchDown = false;
            mIsTouching = false;

       if (lastTouchPos_x != 0.0f || lastTouchPos_y != 0.0f) {
            mTouchUp=true;
        }
        else{
            mTouchUp=false;
            }
        }
        lastTouchPos_x = touchX;
        lastTouchPos_y = touchY;

        if (mTouchDown)
        {
            mCanSwipe=true;
            firstTouchPos_x = touchX;
            firstTouchPos_y = touchY;
            timeBeginSwipe=System.currentTimeMillis();
        }
        if (mIsTouching) {
                if(!mCanSwipe){
                return SYSTEM_EVENT_NOT_DEFINED_ID;
               }

            if (System.currentTimeMillis() - timeBeginSwipe > timeSwipeDelay) {
                if (DEBUG) {
                    Log.d("[TTT]", "timeSwipeDelay time out");
                }
                timeBeginSwipe = System.currentTimeMillis();
                firstTouchPos_x = touchX;
                firstTouchPos_y = touchY;
            }
            //左右滑动
            if (Math.abs(touchX - firstTouchPos_x) >= swipe_DragDistance) {
               // if (firstTouchPos_y <= swipe_Horizontal_YMax && firstTouchPos_y >= swipe_Horizontal_YMin) {
                    if (touchX > firstTouchPos_x) {
                        //right
                        if (DEBUG) {
                            Log.d("[TTT]", "right");
                        }
                        mDirection = SYSTEM_EVENT_RIGHT_ID;
                        mCanSwipe=false;
                    } else {
                        //left
                        if (DEBUG) {
                            Log.d("[TTT]", "left");
                        }
                        mDirection = SYSTEM_EVENT_LEFT_ID;
                        mCanSwipe=false;
                    }
                    timeBeginSwipe = System.currentTimeMillis();
                    firstTouchPos_x = touchX;
                    firstTouchPos_y = touchY;
               // }
            }
            //上下滑动
            if (Math.abs(touchY - firstTouchPos_y) >= swipe_DragDistance)
            {
                //if (firstTouchPos_x <= swipe_Vertical_XMax && firstTouchPos_x >= swipe_Vertical_XMin) {
                    if (touchY > firstTouchPos_y) {
                        //down
                        if (DEBUG) {
                            Log.d("[TTT]", "down");
                        }
                        mDirection = SYSTEM_EVENT_DOWN_ID;
                        mCanSwipe=false;

                    } else {
                        //up
                        if (DEBUG) {
                            Log.d("[TTT]", "up");
                        }
                        mDirection = SYSTEM_EVENT_UP_ID;
                        mCanSwipe=false;
                    }
                    timeBeginSwipe = System.currentTimeMillis();
                    firstTouchPos_x = touchX;
                    firstTouchPos_y = touchY;
                //}
            }
        }
      if(mTouchUp){

                 timeBeginSwipe = System.currentTimeMillis();
                 firstTouchPos_x = touchX;
                 firstTouchPos_y = touchY;
      }
        return mDirection;
    }

    private int matchEvent(float touchX, float touchY){
        if (DEBUG) {
            Log.d("[YYY]", "matchEvent touchX = " + touchX + " touchY = " + touchY);
        }
        if ((touchX>touchY) && ((touchX+touchY)<1) && (touchY>=0 && touchY<=0.2)) {
            return SYSTEM_EVENT_UP_ID;
        } else if ((touchX>touchY) && ((touchX +touchY)>1) && (touchX>=0.8 && touchX<=1)) {
            return SYSTEM_EVENT_RIGHT_ID;
        } else if ((touchX<touchY) && ((touchY+touchX)>1) && (touchY>=0.8 && touchY<=1)) {
            return SYSTEM_EVENT_DOWN_ID;
        } else if ((touchX<touchY) && ((touchY+touchX)<1) && (touchX>=0 && touchX<=0.2)) {
            return SYSTEM_EVENT_LEFT_ID;
        } else if ((touchX-0.5)*(touchX-0.5)+(touchY-0.5)*(touchY-0.5) < (0.2*0.2)) {
            return SYSTEM_EVENT_ENTER_ID;
        } else {
            if (DEBUG) {
                Log.d("[YYY]", "matchEvent the event not defined.");
            }
            return SYSTEM_EVENT_NOT_DEFINED_ID;
        }
    }

    /*simulation key event of system
     * KeyEvent: 1.KEYCODE_BACK
     *           2.KEYCODE_ENTER || KEYCODE_DPAD_CENTER
     *           3.KEYCODE_DPAD_UP,KEYCODE_DPAD_DOWN,KEYCODE_DPAD_LEFT,KEYCODE_DPAD_RIGHT
     *           4.KEYCODE_HOME
    */
    private synchronized void sendSystemEvent(final int systemEventId){
        Thread th = new Thread(){
            //can not run in UI thread
            public void run() {
                Instrumentation inst = new Instrumentation();
                String logInfo = "";
                try {
                    switch (systemEventId){
                        case SYSTEM_EVENT_BACK_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                            logInfo = "KeyEvent.KEYCODE_BACK";
                            break;

                        case SYSTEM_EVENT_ENTER_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
                            logInfo = "KeyEvent.KEYCODE_ENTER";
                            break;

                        case SYSTEM_EVENT_UP_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
                            logInfo = "KeyEvent.KEYCODE_DPAD_UP";
                            break;
                        case SYSTEM_EVENT_DOWN_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                            logInfo = "KeyEvent.KEYCODE_DPAD_DOWN";
                            break;
                        case SYSTEM_EVENT_LEFT_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
                            logInfo = "KeyEvent.KEYCODE_DPAD_LEFT";
                            break;
                        case SYSTEM_EVENT_RIGHT_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
                            logInfo = "KeyEvent.KEYCODE_DPAD_RIGHT";
                            break;

                        case SYSTEM_EVENT_HOME_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
                            logInfo = "KeyEvent.KEYCODE_HOME";
                            break;
                        case SYSTEM_EVENT_VOLUME_UP_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP);
                            logInfo = "KeyEvent.KEYCODE_VOLUME_UP";
                            break;
                        case SYSTEM_EVENT_VOLUME_DOWN_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN);
                            logInfo = "KeyEvent.KEYCODE_VOLUME_DOWN";
                            break;
                        default:
                            logInfo = "This behavior does not match system event ! ";
                            break;
                    }
                    if (DEBUG) {
                        Log.d("[UUU]", logInfo);
                    }
                } catch (Exception e) {
                    android.util.Log.d(TAG," Instrumentation Exception = "+e);
                }
            };
        };
        th.start();
    }
    //end

}

class Bt_node_data{
    public byte keymask;
    public int type;
    public int bat_level;

    public float quans_x;
    public float quans_y;
    public float quans_z;
    public float quans_w;

    public float gyro_x;
    public float gyro_y;
    public float gyro_z;

    public float acc_x;
    public float acc_y;
    public float acc_z;

    public float touchX;
    public float touchY;

    public int appVersion;
    public int deviceVersion;
    public int deviceType;

    public int timeStamp;
    public int shakeEvent;
    public int eventParameter;


    public Bt_node_data(){}
    public Bt_node_data(float x, float y, float z, float w){
        this.quans_x = x;
        this.quans_y = y;
        this.quans_z = z;
        this.quans_w = w;
    }
}
