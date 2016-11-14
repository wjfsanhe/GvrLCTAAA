package com.android.qiyicontroller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class AIDLControllerService extends Service {
    private static String TAG = "AIDLControllerService";

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
            Log.d("AIDLControllerService","<<<GetBatteryLevel:");
            return "50";
        }

        @Override
        public void OpenVibrator(){
            Log.d("AIDLControllerService","OpenVibrator");
        }
        @Override
        public void CloseVibrator(){
            Log.d("AIDLControllerService","CloseVibrator");
        }

    };
}
