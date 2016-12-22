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
//end

public class AIDLControllerService extends Service {
    private static String TAG = "AIDLControllerService";

    public static int JOYSTICK_CONTROL_TYPE = 1;

    //add by zhangyawen
    LocalBroadcastManager localBroadcastManager;

    //EventReceiver eventReceiver = new EventReceiver();

    RemoteCallbackList<AIDLListener> mListenerList = new RemoteCallbackList<AIDLListener>();

    private float[] quans = new float[]{0,0,0,1};
    //end

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
                Log.d("zyc", "<<<onEvent x:" + event.getX() + " y:" + event.getY() + " z:" + event.getZ() + " w:" + event.getW());
                quansDataEventService(event.getX(), event.getY(), event.getZ(), event.getW());
            }else if(messageType == MessageEvent.SHORT_CLICK_BACK_EVENT){
                shortClickBackEventService(event.getBackState());
            }else if(messageType == MessageEvent.BATTERY_LEVEL_EVENT){
                batterLevelEventService(event.getLevel());
            }else if(messageType == MessageEvent.TRIGGER_BUTTON_EVENT){
                clickAndTriggerEventService(event.getTriggerstate());
            }else if(messageType == MessageEvent.APP_BUTTON_EVENT){
                clickAppButtEventService(event.getAppstate());
            }else if(messageType == MessageEvent.SHAKE_EVENT){
                shakeEventService(event.getTimestamp(),event.getEvent(),event.getParameter());
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
            Log.d("AIDLControllerService","<<<GetBatteryLevel");
            return AIDLControllerUtil.mBatterLevel;
        }

        @Override
        public void OpenVibrator(){
            Log.d("AIDLControllerService","OpenVibrator");
            Intent intent = new Intent();
            intent.setAction("OPEN_VIBRATOR_ACTION");
            localBroadcastManager.sendBroadcast(intent);
        }
        @Override
        public void CloseVibrator(){
            Log.d("AIDLControllerService","CloseVibrator");
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
        public void registerListener(AIDLListener listener){
            mListenerList.register(listener);
        }

        @Override
        public void unRegisterListener(AIDLListener listener){
            mListenerList.unregister(listener);
        }
    };

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
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    Log.d(TAG,"l.shortClickBackEvent  state = "+state);
                    l.shortClickBackEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
    }

    private synchronized void batterLevelEventService(int level){
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    Log.d(TAG,"l.batterLevelEvent  level = "+level);
                    l.batterLevelEvent(level);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
    }

    private synchronized void clickAppButtEventService(int state){
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    Log.d(TAG,"l.clickAppButtonEvent  state = "+state);
                    l.clickAppButtonEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
    }

    private synchronized void clickAndTriggerEventService(int state){
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    Log.d(TAG,"l.clickAndTriggerEvent  state = "+state);
                    l.clickAndTriggerEvent(state);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
    }

    private synchronized void quansDataEventService(float x, float y, float z, float w){
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    Log.d(TAG,"quans data l.quansDataEvent  x = "+x+" y = "+y+" z = "+z+" w = "+w);
                    l.quansDataEvent(x, y, z, w);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
    }

    private synchronized void shakeEventService(int timeStamp,int event,int eventParameter){
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            AIDLListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try{
                    Log.d(TAG,"l.shakeEvent  timeStamp = "+timeStamp +" event = "+event+" eventParameter = "+eventParameter);
                    l.shakeEvent(timeStamp,event,eventParameter);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
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
