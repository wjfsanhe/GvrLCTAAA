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
    public static final boolean DBG = true;
    private static int count = 0;
    public static final String DAYDREAM_TEST = "com.longcheer.net.test.daydream";
    public static final String GETHANDVERSION_TEST = "com.longcheer.net.test.gethandversion";

    public static final String TEST_GET_HAND_VERSION = "test_get_handDevice_version_info";
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG,"action:"+action);
        if(action == null) return;
        final Intent i = new Intent(context, ControllerService.class);
        final Handler myHandler = new Handler();
        final Runnable startIntentService = new Runnable() {
            @Override
            public void run() {
                i.setPackage(context.getPackageName());
                context.startService(i);
            }
        };

        if (action.equals(Intent.ACTION_BOOT_COMPLETED) && ControllerService.runAsDaemonService) {
            // start service to do the work.
            if (DBG) {
                Log.d(TAG, "Action boot completed received..");
            }
            i.putExtra(ControllerService.BOOT_COMPLETE_FLAG, true);
            context.startService(i);
        }else if(DAYDREAM_TEST.equals(action)){
            Log.d(TAG,"start joystick vibrate");
            i.putExtra("test_vibrate", true);
            i.setPackage(context.getPackageName());
            context.startService(i);
        }else if(GETHANDVERSION_TEST.equals(action)){
            Log.d(TAG,"test get hand version ");
            i.putExtra(TEST_GET_HAND_VERSION, true);
            i.setPackage(context.getPackageName());
            context.startService(i);
        }else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            Log.i(TAG,"ACTION_ACL_CONNECTED");
            //only iqiyi iDream joystick start Service
            if (true) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    Log.d("mshuai", device.getName() + " ACTION_ACL_CONNECTED");

                    String dName = device.getName();
                    if (dName != null && dName.contains("QIYI")) {
                        i.putExtra(ControllerService.BLUETOOTH_CONNECTED_SUCCESS, true);
                        i.putExtra(ControllerService.BLUETOOTH_DEVICE_OBJECT, device);
                        context.startService(i);
                        // delay 3s, wait for create hidrawx node
                        // myHandler.postDelayed(startIntentService, 3000);
                    }else{
                        Log.e(TAG,"not iqiyi hand device");
                    }
                }
            }

        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            Log.i(TAG,"ACTION_ACL_DISCONNECTED");
            if (false) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    Log.d("mshuai", device.getName() + " ACTION_ACL_DISCONNECTED");
                    String dName = device.getName();
                    if (dName != null && dName.contains("QIYI")) {
                        i.putExtra(ControllerService.BLUETOOTH_DISCONNECTED, true);
                        context.startService(i);
                        // delay 3s, wait for create hidrawx node
                        // myHandler.postDelayed(startIntentService, 1500);
                    }
                }
            }
        }
    }
}
