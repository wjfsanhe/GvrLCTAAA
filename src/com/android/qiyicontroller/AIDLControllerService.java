package com.android.qiyicontroller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
//add by zhangyawen
import android.support.v4.content.LocalBroadcastManager;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

import java.util.HashMap;
import com.google.vr.vrcore.controller.ControllerService;
//end

public class AIDLControllerService extends Service {
    private static String TAG = "AIDLControllerService";

    public static int JOYSTICK_CONTROL_TYPE = 1;

    public static boolean DEBUG = false;

    public static final String CONTROL_HAND_DEVICE_TYPE = "control_hand_device_type";
    public static final String CONTROL_HAND_DEVICE_DATA1 = "control_hand_device_data1";
    public static final String CONTROL_HAND_DEVICE_DATA2 = "control_hand_device_data2";

    public static final String ACTION_GET_HAND_DEVICE_VERSION_INFO = "GET_HAND_DEVICE_VERSION_INFO_ACTION";
    public static final String ACTION_CONTROL_HAND_DEVICE = "CONTROL_HAND_DEVICE_ACTION";

    //add by zhangyawen
    LocalBroadcastManager localBroadcastManager;

    //EventReceiver eventReceiver = new EventReceiver();

    RemoteCallbackList<AIDLListener> mListenerList = new RemoteCallbackList<AIDLListener>();
    HashMap<Integer,AIDLListener> mListenerHashMap= new HashMap<Integer,AIDLListener>();

    private float[] quans = new float[]{0,0,0,1};
    //end

    private AIDLListener mAirMouseListener;
    private int mAIDLListenerPid = 0;
    private int mHTCListenerPid = 0;
    private boolean mAirMouseControllerState = false;
    private int mControllerConnectState = -1;
    public static final String KEY_CONTROLLER_CONNECT_STATE = "controller_state";
    public static final String CONTROLLER_CONNECT_STATE_CONNECTED = "controller_state=connected";
    public static final String CONTROLLER_CONNECT_STATE_DISCONNECTED = "controller_state=disconnected";
    @Override
    public void onCreate(){
        super.onCreate();
        Log.d(TAG,"onCreate");
        //add by zhangyawen
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        /*IntentFilter filter = new IntentFilter();
        filter.addAction("SHORT_CLICK_BACK_KEY_ACTION");
        filter.addAction("BATTER_LEVEL_ACTION");
        filter.addAction("APP_BUTTON_KEY_ACTION");
        filter.addAction("TRIGGER_BUTTON_KEY_ACTION");
        filter.addAction("QUAN_DATA_EVENT_ACTION");
        filter.addAction("SHAKE_EVENT_ACTION");
        Log.d(TAG,"registerReceiver");
        localBroadcastManager.registerReceiver(eventReceiver,filter);*/
        //end
        EventInstance.getInstance().register(mEventListener);
    }

    private EventListener mEventListener = new EventListener(){
        @Override
        public void onEvent(MessageEvent event){
            int messageType = event.getMessageType();
            if(messageType == MessageEvent.QUANS_DATA_EVENT) {
                //Log.d("[PPP]", "<<<onEvent x:" + event.getX() + " y:" + event.getY() + " z:" + event.getZ() + " w:" + event.getW());
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.quansDataEvent(event.getX(), event.getY(), event.getZ(), event.getW(), 0, 0);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    quansDataEventService(event.getX(), event.getY(), event.getZ(), event.getW(), event.getP(), event.getT());
                }
            }else if(messageType == MessageEvent.SHORT_CLICK_BACK_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.shortClickBackEvent(event.getBackState());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    shortClickBackEventService(event.getBackState());
                }
            }else if(messageType == MessageEvent.BATTERY_LEVEL_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.batterLevelEvent(event.getLevel());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    batterLevelEventService(event.getLevel());
                }
            }else if(messageType == MessageEvent.TRIGGER_BUTTON_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.clickAndTriggerEvent(event.getTriggerstate());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    clickAndTriggerEventService(event.getTriggerstate());
                }
            }else if(messageType == MessageEvent.APP_BUTTON_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.clickAppButtonEvent(event.getAppstate());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    clickAppButtEventService(event.getAppstate());
                }
            }else if(messageType == MessageEvent.SHAKE_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.shakeEvent(event.getTimestamp(),event.getEvent(),event.getParameter());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    shakeEventService(event.getTimestamp(),event.getEvent(),event.getParameter());
                }
            }else if(messageType == MessageEvent.LONG_CLICK_HOME_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.longClickHomeEvent(event.getHomeState());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    longClickHomeEventService(event.getHomeState());
                }
            }else if(messageType == MessageEvent.GYRO_DATA_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.gyroDataEvent(event.getGyroX(), event.getGyroY(), event.getGyroZ());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    gyroDataEventService(event.getGyroX(), event.getGyroY(), event.getGyroZ());
                }
            }else if(messageType == MessageEvent.ACCEL_DATA_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.accelDataEvent(event.getAccelX(),event.getAccelY(),event.getAccelZ());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    accelDataEventService(event.getAccelX(),event.getAccelY(),event.getAccelZ());
                }
            }else if(messageType == MessageEvent.TOUCH_DATA_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.touchDataEvent(event.getTouchX(),event.getTouchY());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    touchDataEventService(event.getTouchX(),event.getTouchY());
                }
            }else if (messageType == MessageEvent.VERSION_INFO_EVENT){
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.handDeviceVersionInfoEvent(event.getData1(),event.getData2(),event.getData3());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    handDeviceVersionInfoEventService(event.getData1(),event.getData2(),event.getData3());
                }
            }else if (messageType == MessageEvent.CONNECT_STATE_EVENT){
                mControllerConnectState = event.getConnectState();
                if(mAirMouseControllerState && mAirMouseListener != null){
                    try{
                        mAirMouseListener.connectStateEvent(event.getConnectState());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                connectStateService(event.getConnectState());
            }else if(messageType == MessageEvent.MESSAGE_TO_CLIENT_EVENT){
                messageToClientService(event.getToClientMessage());
            }
        }
    };


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return mAIDLController;
    }


    private Binder mAIDLController = new AIDLController.Stub(){
        @Override
        public String GetBatteryLevel(){
            if (DEBUG) {
                Log.d("AIDLControllerService","<<<GetBatteryLevel");
            }
            return AIDLControllerUtil.mBatterLevel;
        }

        @Override
        public void OpenVibrator(){
            if (DEBUG) {
                Log.d("AIDLControllerService", "OpenVibrator");
            }
            Intent intent = new Intent();
            intent.setAction("OPEN_VIBRATOR_ACTION");
            localBroadcastManager.sendBroadcast(intent);
        }
        @Override
        public void CloseVibrator(){
            if (DEBUG) {
                Log.d("AIDLControllerService", "CloseVibrator");
            }
        }
        @Override
        public void vibrate(long milliseconds){
            Intent intent = new Intent();
            intent.putExtra("time",milliseconds);
            intent.setAction("OPEN_VIBRATOR_TIME_ACTION");
            localBroadcastManager.sendBroadcast(intent);
        }
        @Override
        public void vibrate_mode(long milliseconds,int mode){
            Intent intent = new Intent();
            intent.putExtra("time",milliseconds);
            intent.putExtra("mode",mode);
            intent.setAction("OPEN_VIBRATOR_MODE_ACTION");
            localBroadcastManager.sendBroadcast(intent);
        }
        @Override
        public void vibrate_repeat(long[] pattern,int repeat){
            Intent intent = new Intent();
            intent.putExtra("pattern",pattern);
            intent.putExtra("repeat",repeat);
            intent.setAction("OPEN_VIBRATOR_REPEAT_ACTION");
            localBroadcastManager.sendBroadcast(intent);
        }
        @Override
        public void vibrate_cancel(){
            Intent intent = new Intent();
            intent.setAction("CLOSE_VIBRATOR_ACTION");
            localBroadcastManager.sendBroadcast(intent);
        }

        @Override
        public void getHandDeviceVersionInfo(){
            Intent intent = new Intent();
            intent.setAction(ACTION_GET_HAND_DEVICE_VERSION_INFO);
            localBroadcastManager.sendBroadcast(intent);
        }
        @Override
        public void enable_home_key(boolean isEnable){
            Intent intent = new Intent();
            if (DEBUG) {
                Log.d("[EEE]","[set] isEnable = "+isEnable);
            }
            ControllerService.enableHomeKeyEvent = isEnable;
        }
        @Override
        public boolean get_enable_home_key(){
            if (DEBUG) {
                Log.d("[EEE]","[get] enableHomeEvent = "+ControllerService.enableHomeKeyEvent);
            }
            return ControllerService.enableHomeKeyEvent;
        }
        @Override
        public void control_handDevice(int type, int data1, int data2){
            Intent intent = new Intent();
            intent.putExtra(CONTROL_HAND_DEVICE_TYPE, type);
            intent.putExtra(CONTROL_HAND_DEVICE_DATA1, data1);
            intent.putExtra(CONTROL_HAND_DEVICE_DATA2, data2);
            intent.setAction(ACTION_CONTROL_HAND_DEVICE);
            localBroadcastManager.sendBroadcast(intent);
        }
        @Override
        //add for new spread feature
        public void setParameters(String keyValue){
        }
        @Override
	//add for new spread feature
        public String getParameters(String key){
            if(KEY_CONTROLLER_CONNECT_STATE.equals(key)){
                if(mControllerConnectState == 1){
                    return CONTROLLER_CONNECT_STATE_CONNECTED;
                }
                return CONTROLLER_CONNECT_STATE_DISCONNECTED;
            }
            return null;
        }
        @Override
        public void registerListener(AIDLListener listener){
            Log.d(TAG,"registerListener pid = "+Binder.getCallingPid());
            //mListenerList.register(listener);
            mAIDLListenerPid = Binder.getCallingPid();
            mListenerHashMap.put(mAIDLListenerPid,listener);
        }

        @Override
        public void unRegisterListener(AIDLListener listener){
            Log.d(TAG,"unRegisterListener pid = "+Binder.getCallingPid());
            //mListenerList.unregister(listener);
            mListenerHashMap.remove(Binder.getCallingPid());
        }
	@Override
        public void registerAIDLListener(AIDLListener listener,String client){
            Log.d(TAG,"registerAIDLListener"+" client = "+client+" pid = "+Binder.getCallingPid());
            if ("airmouse".equals(client)){
                mAirMouseListener = listener;
            }else if("HTC".equals(client)){
                mHTCListenerPid = Binder.getCallingPid();
                mListenerHashMap.put(mHTCListenerPid,listener);

                HTCListenerRecord mHTCListenerRecord = new HTCListenerRecord();
                try {
                    listener.asBinder().linkToDeath(mHTCListenerRecord, 0);
                } catch (RemoteException e) {
                    Log.d(TAG, "HTCListener already died.", e);
                }
            }else{
                Log.d(TAG,"registerAIDLListener client is not support: client = "+client);
            }
        }
        @Override
        public void unRegisterAIDLListener(AIDLListener listener,String client){
            Log.d(TAG,"unRegisterAIDLListener"+" client = "+client+" pid = "+Binder.getCallingPid());
            if ("airmouse".equals(client)){
                mAirMouseListener = null;
            }else if("HTC".equals(client)){
                mListenerHashMap.remove(Binder.getCallingPid());
            }else{
                Log.d(TAG,"unRegisterAIDLListener client is not support: client = "+client);
            }
        }
        @Override
        public void updateAirMouseControllerState(boolean enable){
           Log.d(TAG,"UpdateAirMouseControllerState"+" enable = "+enable);
           mAirMouseControllerState = enable;
           Intent intent = new Intent();
           intent.putExtra("enable", enable);
           intent.setAction("ENABLE_HAND_DEVICES");
           localBroadcastManager.sendBroadcast(intent);
        }
    };

    private class HTCListenerRecord implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            Log.d(TAG, "BinderDied.");
            mListenerHashMap.remove(mHTCListenerPid);
        }
    }

    //add by zhangyawen
    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy");
        Log.d(TAG,"unregisterReceiver");
        //localBroadcastManager.unregisterReceiver(eventReceiver);
        EventInstance.getInstance().unregister(mEventListener);
        super.onDestroy();
    }

    private synchronized void shortClickBackEventService(int state){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.shortClickBackEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.shortClickBackEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "l.shortClickBackEvent  state = " + state);
                    }
                    l.shortClickBackEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }
    private synchronized void longClickHomeEventService(int state){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.longClickHomeEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.longClickHomeEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "l.longClickHomeEvent  state = " + state);
                    }
                    l.longClickHomeEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }

    private synchronized void batterLevelEventService(int level){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.batterLevelEvent(level);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.batterLevelEvent(level);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "l.batterLevelEvent  level = " + level);
                    }
                    l.batterLevelEvent(level);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }

    private synchronized void clickAppButtEventService(int state){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.clickAppButtonEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.clickAppButtonEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "l.clickAppButtonEvent  state = " + state);
                    }
                    l.clickAppButtonEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }

    private synchronized void clickAndTriggerEventService(int state){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.clickAndTriggerEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.clickAndTriggerEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "l.clickAndTriggerEvent  state = " + state);
                    }
                    l.clickAndTriggerEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }

    private synchronized void quansDataEventService(float x, float y, float z, float w, int packnum, long timestampnanos){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.quansDataEvent(x, y, z, w, packnum, timestampnanos);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.quansDataEvent(x, y, z, w, packnum, timestampnanos);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    //Log.d("[PPP]","quans data l.quansDataEvent  x = "+x+" y = "+y+" z = "+z+" w = "+w);
                    l.quansDataEvent(x, y, z, w, packnum, timestampnanos);
                    //Log.d("[PPP]","quansDataEvent [after]");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }
    private synchronized void connectStateService(int state){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.connectStateEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.connectStateEvent(state);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "[connect state]  state = " + state);
                    }
                    l.connectStateEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }
    private synchronized void messageToClientService(String message){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.messageToClientEvent(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.messageToClientEvent(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "[messageToClient message]  message = " + message);
                    }
                    l.messageToClientEvent(message);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }
    //add by zhangyawen
    private synchronized void gyroDataEventService(float x, float y, float z){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.gyroDataEvent(x, y, z);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.gyroDataEvent(x, y, z);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "[gyro] data l.gyroDataEvent  x = " + x + " y = " + y + " z = " + z);
                    }
                    l.gyroDataEvent(x, y, z);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }

    private synchronized void accelDataEventService(float x, float y, float z){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.accelDataEvent(x, y, z);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.accelDataEvent(x, y, z);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "[accel] data l.accelDataEvent  x = " + x + " y = " + y + " z = " + z);
                    }
                    l.accelDataEvent(x, y, z);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }

    private synchronized void touchDataEventService(float x, float y){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.touchDataEvent(x, y);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.touchDataEvent(x, y);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "[touch] data l.touchDataEvent  x = " + x + " y = " + y);
                    }
                    l.touchDataEvent(x, y);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }
    //end

    private synchronized void shakeEventService(int timeStamp,int event,int eventParameter){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.shakeEvent(timeStamp,event,eventParameter);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.shakeEvent(timeStamp,event,eventParameter);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "l.shakeEvent  timeStamp = " + timeStamp + " event = " + event + " eventParameter = " + eventParameter);
                    }
                    l.shakeEvent(timeStamp,event,eventParameter);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }

    private synchronized void handDeviceVersionInfoEventService(int appVersion,int deviceVersion,int deviceType){
        AIDLListener aidlListener = mListenerHashMap.get(mAIDLListenerPid);
        if(aidlListener != null){
            try{
                aidlListener.handDeviceVersionInfoEvent(appVersion,deviceVersion,deviceType);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        AIDLListener htcListener = mListenerHashMap.get(mHTCListenerPid);
        if(htcListener != null){
            try{
                htcListener.handDeviceVersionInfoEvent(appVersion,deviceVersion,deviceType);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        /*
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    if (DEBUG) {
                        Log.d(TAG, "l.versionInfo  appVersion = " + appVersion + " deviceVersion = " + deviceVersion + " deviceType = " + deviceType);
                    }
                    Log.d(TAG, "l.versionInfo  appVersion = " + appVersion + " deviceVersion = " + deviceVersion + " deviceType = " + deviceType);
                    l.handDeviceVersionInfoEvent(appVersion,deviceVersion,deviceType);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
        */
    }


    /*public class EventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if("SHORT_CLICK_BACK_KEY_ACTION".equals(action)){
                int state = -1;
                state = intent.getExtras().getInt("state");
                Log.d(TAG,"[BACK] EventReceiver onReceive  state = "+state);
                shortClickBackEventService(state);
            }
            if("BATTER_LEVEL_ACTION".equals(action)){
                int level = -1;
                level = intent.getExtras().getInt("level");
                Log.d(TAG,"EventReceiver onReceive  level = "+level);
                batterLevelEventService(level);
            }
            if("APP_BUTTON_KEY_ACTION".equals(action)){
                int state = -2;
                state = intent.getExtras().getInt("state");
                Log.d(TAG,"[APPBUTTON] EventReceiver onReceive  state = "+state);
                clickAppButtEventService(state);
            }
            if("TRIGGER_BUTTON_KEY_ACTION".equals(action)){
                int state = -3;
                state = intent.getExtras().getInt("state");
                Log.d(TAG,"[TRIGGER] EventReceiver onReceive  state = "+state);
                clickAndTriggerEventService(state);
            }
            if("QUAN_DATA_EVENT_ACTION".equals(action)){
                quans = intent.getExtras().getFloatArray("quans");
                //Log.d("[SYS]","[QUANS] EventReceiver quans = "+quans);
                //quansDataEventService(quans[0],quans[1],quans[2],quans[3]);
            }
            if("SHAKE_EVENT_ACTION".equals(action)){
                int timeStamp = 0;
                int event = 0;
                int eventParameter = 0;
                timeStamp = intent.getExtras().getInt("timeStamp");
                event = intent.getExtras().getInt("Event");
                eventParameter = intent.getExtras().getInt("eventParameter");
                Log.d(TAG,"[SHAKE] onReceive timeStamp = "+timeStamp+" event = "+event+" eventParameter = "+eventParameter);
                shakeEventService(timeStamp,event,eventParameter);
            }

        }
    }*/
    //end

}
