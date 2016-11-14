package com.google.vr.vrcore.controller;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

/**
 * Created by mashuai on 2016/10/2.
 */
public class ControllerRec extends BroadcastReceiver {
    public static final String TAG = "myControllerRec";
    private static int count = 0;
    public static final String DAYDREAM_TEST = "com.longcheer.net.test.daydream";
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG,"action:"+action);
        final Intent i = new Intent(context, ControllerService.class);
        final Handler myHandler = new Handler();
        final Runnable startIntentService = new Runnable() {
            @Override
            public void run() {
                context.startService(i);
            }
        };

        if(DAYDREAM_TEST.equals(action)){
            Log.d(TAG,"start joystick vibrate");
            i.putExtra("test_vibrate", true);
            myHandler.postDelayed(startIntentService, 3000);
        }else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            //only iqiyi iDream joystick start Service
            if (true) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d("mshuai", device.getName() + " ACTION_ACL_CONNECTED");

                i.putExtra(ControllerService.BLUETOOTH_CONNECTED_SUCCESS, true);
                i.putExtra(ControllerService.BLUETOOTH_DEVICE_OBJECT, device);
                // context.startService(i);
                // delay 3s, wait for create hidrawx node
                myHandler.postDelayed(startIntentService, 3000);
            }

        }else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d("mshuai", device.getName() + " ACTION_ACL_DISCONNECTED");

            i.putExtra(ControllerService.BLUETOOTH_DISCONNECTED, true);
//            context.startService(i);
            //delay 3s, wait for create hidrawx node
            myHandler.postDelayed(startIntentService, 1500);
        }
    }
}
