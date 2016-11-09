package com.google.vr.vrcore.controller;

import android.app.Service;
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

/**
 * Created by mashuai on 2016/9/29.
 */
public class ControllerService extends Service {

    private final static String TAG = "ControllerService";

    public final static String BLUETOOTH_CONNECTED_SUCCESS ="bluetooth_connected";
    public final static String BLUETOOTH_DISCONNECTED = "bluetooth_disconnected";
    private final static boolean DEBUG = true;

    private boolean lastButtonStatus = false;
    private int lastButton = 0;
    private IControllerService.Stub controllerService;
    private static IControllerListener controllerListener;
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
            startGetNodeDataThread();
            //start read hidrawx node
        }else if(intent.getBooleanExtra(BLUETOOTH_DISCONNECTED,false)){
            //stop read node
            debug_log("onStartCommand intent.getAction:"+intent.getAction()+", set isCancel=false");
            isCancel = true;
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

    private static final String SOCKET_ADDRESS = "127.0.0.1";

    private static final int  SOCKET_PORT = 4321;

    private boolean isCancel = false;

    private String[] device_path = new String[]{"/dev/hidraw0", "/dev/hidraw1", "/dev/hidraw2"};// read 32Byte/time

    char[] hexBuf;
    char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private FileWriter mNodeWriter;
    protected String toHexString(byte[] bytes, int size) {
        int v, index = 0;
        hexBuf = new char[4096];
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


    private static String gpio_NODE = "/sys/class/gpio/gpio126/value";
    private boolean prepareFileAccess(String Filename)
    {
        File file=new File(Filename);
        try {
            mNodeWriter = new FileWriter(file);
        } catch (Exception e) {
            e.printStackTrace();
            return false;// TODO: handle exception
        }
        return true;
    }
    private void setValues(String values)
    {
        if(mNodeWriter!=null)
        {
            try {
                mNodeWriter.write(values);
                mNodeWriter.flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

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
     */
    public native int nativeOpenFile();
    public native bt_node_data nativeReadFile();
    public native int nativeCloseFile();

    private void scheduleNext(){

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
                        int res = nativeOpenFile();
                        if(res <0 ){
                            Log.e(TAG,"native open file failed");
                            return;
                        }
                        Log.d(TAG, "natvie Open File");
                        // boolean needSetControllerState = true;

                        while (!isCancel) {
                            if (controllerListener == null) {
                                // needSetControllerState = true;
                                Log.i(TAG, "controllerListener is null, sleep 3s");

                                try {
                                    Thread.sleep(3000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } finally {
                                    continue;
                                }
                            } else {
                                if (true) {// needSetControllerState) {
                                    debug_log("set Controller state CONNECTED!");
                                    try {
                                        controllerListener.onControllerStateChanged(controllerId,
                                                ControllerStates.CONNECTED);
                                        // needSetControllerState = false;
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            bt_node_data nodeData = nativeReadFile();
                            Log.d(TAG, "native Read File finish");
                            if (nodeData == null) {
                                Log.e(TAG, "do not get hidraw data from native");
                                continue;
                            }

                            if (nodeData.type == 1) {// quans
                                debug_log("nodeData:x:" + nodeData.quans_x + ", y:"
                                        + nodeData.quans_y + ",z:" + nodeData.quans_z + ",w:"
                                        + nodeData.quans_w);
                                sendPhoneEventControllerOrientationEvent(nodeData.quans_x,
                                        nodeData.quans_y,
                                        nodeData.quans_z,
                                        nodeData.quans_w);
                                debug_log("send phon event finish");
                            } else if (nodeData.type == 2) {
/*
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
                                // int battery = (((int)buffer[18]) & 0x000000FF) + 100;

                                if (DEBUG) {
                                    Log.d(TAG, "mshuai, get data:gyro.x:" + sensor[0] + ", gyro.y:"
                                            + sensor[1] + ", gyro.z:" + sensor[2] + ", acc.x"
                                            + sensor[3] + ", acc.y:" + sensor[4] + ", acc.z:"
                                            + sensor[5]);
                                    Log.d(TAG, "mshuai, get touchx:" + touchX + ", touchy:"
                                            + touchY);
                                }
                                sendPhoneEventControllerAccAndGyroEvent(sensor);
                                sendPhoneEventControllerButtonEvent(keymask);
                                sendPhoneEventControllerTouchPadEvent(touchX,touchY);*/
                                Log.d(TAG, "dajiangyou");
                            } else {
                                Log.e(TAG, "invalid data");
                            }
                        }
                        nativeCloseFile();
                        Log.d(TAG, "natvie Open File");
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





    private final ControllerTouchEvent controllerTouchEvent = new ControllerTouchEvent();
    private final ControllerGyroEvent controllerGyroEvent = new ControllerGyroEvent();
    private final ControllerAccelEvent controllerAccelEvent = new ControllerAccelEvent();
    private static final ControllerOrientationEvent controllerOrientationEvent = new ControllerOrientationEvent();
    private static final ControllerButtonEvent controllerButtonEvent = new ControllerButtonEvent();
//    private static final ControllerEventPacket cep = new ControllerEventPacket();
    private void sendPhoneEventControllerOrientationEvent(float x, float y, float z, float w){

        controllerOrientationEvent.qx = x;
        controllerOrientationEvent.qy = y;
        controllerOrientationEvent.qz = z;
        controllerOrientationEvent.qw = w;

        controllerOrientationEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();

        try {
//            controllerListener.deprecatedOnControllerOrientationEvent(controllerOrientationEvent); //must be send
            controllerListener.onControllerOrientationEvent(controllerOrientationEvent); //must be send
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    private void sendPhoneEventControllerButtonEvent(byte keymask){
        int button = ControllerButtonEvent.BUTTON_NONE;
        boolean buttonActionDown = false;

        if ((keymask&0x01) != 0) {
            //click or ok
            button = ControllerButtonEvent.BUTTON_CLICK;
        }/*else if ((keymask&0x02) != 0) {
            //back
            button = ControllerButtonEvent.BUTTON_NONE;
        }else if ((keymask&0x04) != 0) {
            //trigger
            button = ControllerButtonEvent.BUTTON_NONE;
        }*/else if ((keymask&0x08) != 0) {
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
//                controllerListener.deprecatedOnControllerButtonEvent(controllerButtonEvent);
                controllerListener.onControllerButtonEvent(controllerButtonEvent);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        lastButton = button;
        lastButtonStatus = buttonActionDown;
    }
    private void sendPhoneEventControllerAccAndGyroEvent(int[] sensor){
        controllerGyroEvent.x = sensor[0];
        controllerGyroEvent.y = sensor[1];
        controllerGyroEvent.z = sensor[2];

        controllerGyroEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();
        try {
//            controllerListener.deprecatedOnControllerGyroEvent(controllerGyroEvent); //probably not used
            controllerListener.onControllerGyroEvent(controllerGyroEvent); //probably not used
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        controllerAccelEvent.x = sensor[3];
        controllerAccelEvent.y = sensor[4];
        controllerAccelEvent.z = -sensor[5];

        controllerAccelEvent.timestampNanos = SystemClock.elapsedRealtimeNanos();
        try {
            //controllerListener.deprecatedOnControllerAccelEvent(controllerAccelEvent); //probably not used
            controllerListener.onControllerAccelEvent(controllerAccelEvent); //probably not used
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    private void sendPhoneEventControllerTouchPadEvent(int touchX, int touchY){
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
//            controllerListener.deprecatedOnControllerTouchEvent(controllerTouchEvent);
            controllerListener.onControllerTouchEvent(controllerTouchEvent);
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
}

class bt_node_data{
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

    public bt_node_data(){}
    public bt_node_data(float x, float y, float z, float w){
        this.quans_x = x;
        this.quans_y = y;
        this.quans_z = z;
        this.quans_w = w;
    }
}
