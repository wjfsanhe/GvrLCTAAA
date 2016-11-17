package com.google.vr.vrcore.controller;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
//import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

//add by zhangyawen
import android.view.KeyEvent;
import android.app.Instrumentation;
import com.android.qiyicontroller.AIDLControllerUtil;
import android.support.v4.content.LocalBroadcastManager;
//end

/**
 * Created by mashuai on 2016/9/29.
 */
public class ControllerService extends Service {

    private final static String TAG = "ControllerService";

    public final static String BLUETOOTH_CONNECTED_SUCCESS ="bluetooth_connected";
    public final static String BLUETOOTH_DISCONNECTED = "bluetooth_disconnected";
    public final static String BLUETOOTH_DEVICE_OBJECT = "bluetooth_device";
    private final static boolean DEBUG = true;

    private boolean lastButtonStatus = false;
    private int lastButton = 0;
    private IControllerService.Stub controllerService;
    private static IControllerListener controllerListener;

    private static BluetoothInputDevice mBtInputDeviceService = null;
    private static BluetoothDevice device;
    private BluetoothAdapter mAdapter;
    private boolean isBtInputDeviceConnected = false;

    public static int JOYSTICK_CONTROL_TYPE = 1;
    public static int JOYSTICK_REQUEST_TYPE = 2;

    public static int GET_DATA_TIMEOUT = -1;
    public static int GET_INVALID_DATA = -2;
    public static int REPORT_TYPE_ORIENTATION = 1;
    public static int REPORT_TYPE_SENSOR = 2;
    public static int REPORT_TYPE_VERSION = 3;

    private String iDreamDeviceVersion = null;
    private String iDreamDeviceType = null;

    private Handler handler = new Handler();
    private static int button = 0;


    private static int count = 0;

    private static int controllerId=0;

    private Thread getNodeDataThread = null;
    public static void debug_log(String log){
        if(DEBUG){
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

        Log.d("myControllerService", "onCreate");
    }

    @Override
    public void onDestroy() {
        Log.d("myControllerService", "onDestroy");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null) return super.onStartCommand(intent,flags,startId);
        if(intent.getBooleanExtra(BLUETOOTH_CONNECTED_SUCCESS, false)){
            isCancel = false;
            device = intent.getParcelableExtra(ControllerService.BLUETOOTH_DEVICE_OBJECT);
            if(device!=null){
                debug_log("get device name is:"+device.getName());
            }else{
                debug_log("get device is null");
            }
            startGetNodeDataThread();
            //start read hidrawx node
        }else if(intent.getBooleanExtra(BLUETOOTH_DISCONNECTED,false)){
            //stop read node
            debug_log("onStartCommand intent BLUETOOTH_DISCONNECTED, set isCancel=false");
            isCancel = true;
            device=null;
			AIDLControllerUtil.mBatterLevel = "";
        }else if(intent.getBooleanExtra("test_vibrate", false)){//for test vibrate
            controlJoystickVibrate(80, 5);
        }
        return super.onStartCommand(intent,flags,startId);
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
        controllerListener = listener;

//        isCancel = false;
//        startGetNodeDataThread();
    }

    public final boolean unregisterListener(){
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

            isBtInputDeviceConnected = true;
            mBtInputDeviceService = (BluetoothInputDevice) proxy;

        }

        @Override
        public void onServiceDisconnected(int profile) {
            debug_log("serivcedisconnected profile:"+profile);
            if (profile != BluetoothProfile.INPUT_DEVICE) return;

            Log.i(TAG, "Profile proxy disconnected");

            isBtInputDeviceConnected = false;
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
    public native int nativeWriteFile(int type, int shockproofness, int duration);
    public native int nativeCloseFile();

//    private void scheduleNext(){
//
//    }

    private static void controllerServiceSleep(int flag, long ms){
        Log.d(TAG,"sleep "+ms+"s"+", flag:"+flag);;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void startGetNodeDataThread() {
        if (getNodeDataThread == null) {
            getNodeDataThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    // THREAD_PRIORITY_URGENT_DISPLAY THREAD_PRIORITY_URGENT_AUDIO
                    android.os.Process
                            .setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    try {
                        boolean needOpenFile = true;
                        while (!isCancel) {
                            // if connect bt device is not hid device, sleep 3s, and do next while
                            if(!isBtInputDeviceConnected){
                                controllerServiceSleep(1, 3000);
                                continue;
                            }
                            if (needOpenFile) {
                                int res = nativeOpenFile();
                                if (res < 0) {
                                    needOpenFile = true;
                                    Log.e(TAG, "native open file failed, sleep 3s");
                                    controllerServiceSleep(2, 3000);
                                    continue;
                                }
                                needOpenFile = false;
                                Log.d(TAG, "natvie Open File Success");
                            }
                            if (false){//controllerListener == null) {
                                Log.i(TAG, "controllerListener is null, sleep 3s");

                                try {
                                    Thread.sleep(3, 3000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } finally {
                                    continue;
                                }
                            } else {

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

                            Bt_node_data nodeData = nativeReadFile();
                            if (nodeData == null) {
                                Log.e(TAG, "do not get hidraw data from native, schedule next open data node");
                                needOpenFile = true;
                                nativeCloseFile();
                                controllerServiceSleep(4, 3000);
                                continue;
                            }

                            if (nodeData.type == REPORT_TYPE_ORIENTATION) {// quans
//                                debug_log("nodeData:x:" + nodeData.quans_x + ", y:"
//                                        + nodeData.quans_y + ",z:" + nodeData.quans_z + ",w:"
//                                        + nodeData.quans_w);
                                //if Listener is null ,don't need to send Orientation data
                                if (controllerListener != null) {
                                    sendPhoneEventControllerOrientationEvent(nodeData.quans_x,
                                            nodeData.quans_y,
                                            nodeData.quans_z,
                                            nodeData.quans_w);
                                    debug_log("send phon event finish");
                                }
                            } else if (nodeData.type == REPORT_TYPE_SENSOR) {
                                debug_log("nodeData.gyro x:" + nodeData.gyro_x + ", y:"
                                        + nodeData.gyro_y + ", z:" + nodeData.gyro_z + ", acc x:"
                                        + nodeData.acc_x + ", y:" + nodeData.acc_y + ", z:"
                                        + nodeData.acc_z + ", touchX:" + nodeData.touchX
                                        + ", touchY:" + nodeData.touchY + ", keymask:"
                                        + nodeData.keymask);
                                //if Listener is null, don't need to send Acc&Gyro data
                                if (controllerListener != null) {
                                    sendPhoneEventControllerAccAndGyroEvent(nodeData.gyro_x,
                                            nodeData.gyro_y, nodeData.gyro_z, nodeData.acc_x,
                                            nodeData.acc_y, nodeData.acc_z);
                                }
                                sendPhoneEventControllerButtonEvent(nodeData.keymask);
                                sendPhoneEventControllerTouchPadEvent(nodeData.touchX,nodeData.touchY);
                                debug_log("send acc button touch event finish");
                                debug_log("battery:"+nodeData.bat_level);
                                AIDLControllerUtil.mBatterLevel = String.valueOf(nodeData.bat_level);
                                // send broadcast to notify the hand shank's battery
                            }else if (nodeData.type == REPORT_TYPE_VERSION) {
                                debug_log("nodeData appVersion:"+nodeData.appVersion+", deviceVersion:"+nodeData.deviceVersion+", deviceType:"+nodeData.deviceType);
                            } else if(nodeData.type == GET_DATA_TIMEOUT){
                                Log.e(TAG, "no data to read, block timeout");
                            } else if(nodeData.type == GET_INVALID_DATA){
                                Log.e(TAG, "get invalid data from hidraw ");
                            } else {
                                Log.e(TAG,"other err when read node data");
                            }
                        }
                        nativeCloseFile();
                        Log.d(TAG, "natvie Close File");
                    }
                    finally {
                        isCancel = true;
                        Log.d(TAG, "finally, set Controller state DISCONNECTED");
                        try {
                            if (controllerListener != null) {
                                controllerListener.onControllerStateChanged(controllerId,
                                        ControllerStates.DISCONNECTED);
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
        if (!getNodeDataThread.isAlive()) {
            getNodeDataThread.start();
        }
    }

    /*
     * -1 is not ok
     * 0 is ok
     */
    //default powerLevel 80, ms:5(500ms)
    public int controlJoystickVibrate(){
        int res = nativeWriteFile(JOYSTICK_CONTROL_TYPE, 80, 5);
        debug_log("controlJoystickVibrate defaultvalue res:"+res);
        return res;
    }
    public int controlJoystickVibrate(int powerLevel, int millisceonds){
        int res = nativeWriteFile(JOYSTICK_CONTROL_TYPE, powerLevel, millisceonds);
        debug_log("controlJoystickVibrate res:"+res);
        return res;
    }

    /*
     * -1 is not ok
     * 0 is ok
     */
    public int requestIDreamDeviceInfo(int type){
        int res = nativeWriteFile(JOYSTICK_REQUEST_TYPE, 0x01, 0);
        debug_log("requestIDreamDeviceInfo res:"+res);
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

        controllerOrientationEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();

        try {
            if(controllerListener!=null){
//                controllerListener.deprecatedOnControllerOrientationEvent(controllerOrientationEvent); //must be send
              controllerListener.onControllerOrientationEvent(controllerOrientationEvent); //must be send
            }else{
                Log.e(TAG,"when send orientation event, controllerListener is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        debug_log("OrientationX:" + controllerOrientationEvent.qx + ", Y:"
                + controllerOrientationEvent.qy + ",Z:" + controllerOrientationEvent.qz + ",W:"
                + controllerOrientationEvent.qw);
    }
    private void sendPhoneEventControllerButtonEvent(byte keymask){
        int button = ControllerButtonEvent.BUTTON_NONE;
        boolean buttonActionDown = false;

        //add by zhangyawen
        Log.d("[YYY]","keymask = "+keymask);
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
            Log.d("[YYY]","keymask = "+keymask+" trigger treat as touch pad click");
            button = ControllerButtonEvent.BUTTON_CLICK;
        }else if ((keymask&0x08) != 0) {
            //home
            button = ControllerButtonEvent.BUTTON_HOME;
        }else if ((keymask&0x16) != 0) {
            //menu or app
            button = ControllerButtonEvent.BUTTON_APP;
        }else if ((keymask&0x32) != 0) {
            //volume up
            button = ControllerButtonEvent.BUTTON_VOLUME_UP;
        }else if ((keymask&0x64) != 0) {
            //volume down
            button = ControllerButtonEvent.BUTTON_VOLUME_DOWN;
        }else{
            // none
            button = ControllerButtonEvent.BUTTON_NONE;
        }
        buttonActionDown = button != ControllerButtonEvent.BUTTON_NONE;
        controllerButtonEvent.button = button;
        controllerButtonEvent.down = buttonActionDown;

        // if last button status is key down, this time send key up event
        if(button == ControllerButtonEvent.BUTTON_NONE && lastButton != ControllerButtonEvent.BUTTON_NONE){
            controllerButtonEvent.button = lastButton;
        }

        if(DEBUG) {
            Log.d(TAG, "mshuai, buttonevent button:" + button + ", isDown? :" + buttonActionDown + ", lastButtonStatus:" + lastButtonStatus);
        }
        // if last time is not down, this time still not down ,do not send event
        if(lastButtonStatus || buttonActionDown) {
            try {
                if (controllerListener != null) {
//                    controllerListener.deprecatedOnControllerButtonEvent(controllerButtonEvent);
                     controllerListener.onControllerButtonEvent(controllerButtonEvent);
                } else {
                    Log.e(TAG, "when send button event, controllerListener is null");
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        lastButton = button;
        lastButtonStatus = buttonActionDown;
    }
    private void sendPhoneEventControllerAccAndGyroEvent(float gyrpX, float gyrpY, float gyrpZ, float accX, float accY, float accZ){
        controllerGyroEvent.x = gyrpX;
        controllerGyroEvent.y = gyrpY;
        controllerGyroEvent.z = gyrpZ;

        controllerGyroEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();
        try {
            if (controllerListener != null) {
//                controllerListener.deprecatedOnControllerGyroEvent(controllerGyroEvent); // probably not used
                 controllerListener.onControllerGyroEvent(controllerGyroEvent); //probably not used
            } else {
                Log.e(TAG, "when send Gyro event, controllerListener is null");
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
                Log.e(TAG, "when send Accel event, controllerListener is null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    private void sendPhoneEventControllerTouchPadEvent(float touchX, float touchY){
        if(touchX == 0 && touchY == 0){
            // not touch
            return;
        }
        controllerTouchEvent.action = ControllerTouchEvent.ACTION_MOVE;
        controllerTouchEvent.fingerId = 0;//event.motionEvent.pointers[0].getId();
        controllerTouchEvent.x = touchX;
        controllerTouchEvent.y = touchY;

        controllerTouchEvent.timestampNanos = 0;
        try {
            if (controllerListener != null) {
//                controllerListener.deprecatedOnControllerTouchEvent(controllerTouchEvent);
              controllerListener.onControllerTouchEvent(controllerTouchEvent);
            } else {
                Log.e(TAG, "when send Touch event, controllerListener is null");
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
    private boolean isOutting = false;
    private int mLastKeyMask = 0;
    private LocalBroadcastManager localBroadcastManager;
    private static final int DELAY_TIME = 500;
    private static final int DEFINE_LONG_TIME_FOR_HOME = 1*1000;
    private static final int DEFINE_LONG_TIME_FOR_BACK = 1*1000;

    //short click back key
    public static final int BACK_BUTTON_DOWN = 100;
    public static final int BACK_BUTTON = 101;
    public static final int BACK_BUTTON_UP = 102;
    public static final int BACK_BUTTON_CANCEL = -1;
    public static final String BACK_SHORT_CLICK_EVENT_ACTION = "SHORT_CLICK_BACK_KEY_ACTION";

    //long click home key
    public static final int HOME_RECENTERING = 104;
    public static final int HOME_RECENTERED = 105;

    public static final int SYSTEM_EVENT_NOT_DEFINED_ID = -1;
    public static final int SYSTEM_EVENT_BACK_ID = 0;
    public static final int SYSTEM_EVENT_ENTER_ID = 1;
    public static final int SYSTEM_EVENT_UP_ID = 2;
    public static final int SYSTEM_EVENT_DOWN_ID = 3;
    public static final int SYSTEM_EVENT_LEFT_ID = 4;
    public static final int SYSTEM_EVENT_RIGHT_ID = 5;
    public static final int SYSTEM_EVENT_HOME_ID = 6;

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

    //timer2
    private Runnable runnableForBack = new Runnable() {
        @Override
        public void run() {
            isOutting = true;
        }
    };

    private void simulationButtonSystemEvent(int keymask){
        if ((keymask&0x01) != 0) {
            //click Panel
            Log.d("[ZZZ]","click Panel (not match the Event)");
        }else if ((keymask&0x02) != 0) {
            //back
            Log.d("[ZZZ]","back");
            sendSystemEvent(SYSTEM_EVENT_BACK_ID);
        }else if ((keymask&0x04) != 0) {
            //trigger
            Log.d("[ZZZ]","trigger");
            sendSystemEvent(SYSTEM_EVENT_ENTER_ID);
        }else if ((keymask&0x08) != 0) {
            //home
            Log.d("[ZZZ]","home (only home event)");
            sendSystemEvent(SYSTEM_EVENT_HOME_ID);
        }else if ((keymask&0x16) != 0) {
            //menu
            Log.d("[ZZZ]","menu (not match the Event)");
        }else if ((keymask&0x32) != 0) {
            //volume up
            Log.d("[ZZZ]","volume up (not match the Event)");
        }else if ((keymask&0x64) != 0) {
            //volume down
            Log.d("[ZZZ]","volume down (not match the Event)");
        }else{
            // none
            Log.d("[ZZZ]","none (not match the Event)");
        }
    }

    private void ButtonEvent( int keymask){
        if (keymask != 0) {
            if ((keymask&0x08) != 0) {
                // differ click or longclick for home key(1000ms)
                if (keymask != mLastKeyMask) {
                    handler.postDelayed(runnableForHome, DEFINE_LONG_TIME_FOR_HOME);
                    mLastKeyMask = keymask;
                } else {
                    if (isReseting) {
                        // set Recentering state
                        Log.d("[ZZZ]","Home longclick Recentering");
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
                    Log.d("[ZZZ]","Back shortclick ButtonDown");
                } else {
                    if (isOutting) {
                        // do nothing
                        Log.d("[ZZZ]","Back shortclick Button");
                    } else {
                        // set the state (Button)
                        backKeyShortClickEvent(BACK_BUTTON);
                        Log.d("[ZZZ]","Back shortclick Button");
                    }
                }
            } else {
                if (keymask != mLastKeyMask) {
                    simulationButtonSystemEvent(keymask);
                    mLastKeyMask = keymask;
                } else {
                    //do nothing
                }
            }

        } else {
            if (isReseting) {
                // set Recentered state
                isReseting = false;
                Log.d("[ZZZ]","Home longclick Recentered");
            } else if (isOutting) {
                // set Out of app state
                isOutting = false;
                backKeyShortClickEvent(BACK_BUTTON_CANCEL);
                simulationButtonSystemEvent(mLastKeyMask);
            } else {
                if ((mLastKeyMask&0x08) != 0) {
                    handler.removeCallbacks(runnableForHome);
                    simulationButtonSystemEvent(mLastKeyMask);

                }
                if ((mLastKeyMask&0x02) != 0) {
                    handler.removeCallbacks(runnableForBack);
                    //set the state (ButtonUp)
                    backKeyShortClickEvent(BACK_BUTTON_UP);
                    Log.d("[ZZZ]","Back shortclick ButtonUp");
                }
            }
            mLastKeyMask = keymask;
        }
    }

    private void backKeyShortClickEvent(int state){
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent();
        intent.putExtra("state",state);
        intent.setAction(BACK_SHORT_CLICK_EVENT_ACTION);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(TAG,"localBroadcastManager.sendBroadcast(intent)");
    }

    private void simulationSystemEvent(float touchX, float touchY){
        int witchEventId = matchEvent(touchX,touchY);
        Log.d("[YYY]","witchEventId = "+witchEventId);
        if (witchEventId != SYSTEM_EVENT_NOT_DEFINED_ID) {
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
        }
    }


    private int matchEvent(float touchX, float touchY){
        Log.d("[YYY]","matchEvent touchX = "+touchX+" touchY = "+touchY);
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
            Log.d("[YYY]","matchEvent the event not defined.");
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
                        /*
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
                      */
                        case SYSTEM_EVENT_HOME_ID:
                            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
                            logInfo = "KeyEvent.KEYCODE_HOME";
                            break;
                        default:
                            logInfo = "This behavior does not match system event ! ";
                            break;
                    }
                    Log.d(TAG, logInfo);
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


    public Bt_node_data(){}
    public Bt_node_data(float x, float y, float z, float w){
        this.quans_x = x;
        this.quans_y = y;
        this.quans_z = z;
        this.quans_w = w;
    }
}
