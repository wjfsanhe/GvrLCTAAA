package com.android.qiyicontroller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class AIDLControllerService extends Service {
    private static String TAG = "AIDLControllerService";

    public static int JOYSTICK_CONTROL_TYPE = 1;

    public native int nativeWriteFile(int type, int shockproofness, int duration);


    @Override
    public void onCreate(){
        super.onCreate();
        Log.d(TAG,"onCreate");
    }

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
            controlJoystickVibrate(80, 5);
            Log.d("AIDLControllerService","OpenVibrator");
        }
        @Override
        public void CloseVibrator(){
            Log.d("AIDLControllerService","CloseVibrator");
        }

    };


    public int controlJoystickVibrate(int powerLevel, int millisceonds){
        Log.d("AIDLControllerService","<<<controlJoystickVibrate");
        int res = nativeWriteFile(JOYSTICK_CONTROL_TYPE, powerLevel, millisceonds);
        return res;
    }
}
