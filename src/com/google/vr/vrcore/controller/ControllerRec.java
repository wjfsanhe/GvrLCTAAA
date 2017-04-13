package com.google.vr.vrcore.controller;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;

/**
 * Created by mashuai on 2016/10/2.
 */
public class ControllerRec extends BroadcastReceiver {
    public static final String TAG = "myControllerRec";
    public static final boolean DBG = true;
    private static int count = 0;
    public static final String DAYDREAM_TEST                = "com.longcheer.net.test.daydream";
    public static final String GETHANDVERSION_TEST          = "com.longcheer.net.test.gethandversion";
    public static final String RESET_QUATERNION_TEST        = "com.longcheer.net.test.resetquaternion";
    public static final String REQUEST_CALIBRATION_TEST     = "com.longcheer.net.test.calibration";

    public static final String ACTION_HAND_OTA_START        = "com.longcheer.net.handota.start";
    public static final String ACTION_HAND_OTA_STOP         = "com.longcheer.net.handota.stop";

    public static final String TEST_GET_HAND_VERSION        = "test_get_handDevice_version_info";
    public static final String TEST_RESET_QUATERNION        = "test_reset_handDevice_quaternion";
    public static final String TEST_REQUEST_CALIBRATION     = "test_handDevice_calibration";
    public static final String REQUEST_CALIBRATION_TYPE     = "calibration_type";
    public static final String REQUEST_CALIBRATION_MODE     = "calibration_mode";
    public static final String HAND_OTA_ACTION        = "hand_device_ota_action";
    public static final String HAND_OTA_STATUS              = "hand_device_ota_status";

    public static final int STATUS_HAND_OTA_STARTING        = 0;
    public static final int STATUS_HAND_OTA_STOPED          = 1;
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
        }else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            Log.i(TAG,"ACTION_ACL_CONNECTED");
            //only iqiyi iDream joystick start Service
            if (true) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    Log.d(TAG, device.getName() + " ACTION_ACL_CONNECTED");

                    String dName = device.getName();
                    if (dName != null && dName.contains("QIYI")) {
                        SystemProperties.set("sys.iqiyi.hand.connect", "true");
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
            if (true) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    Log.d(TAG, device.getName() + " ACTION_ACL_DISCONNECTED");
                    String dName = device.getName();
                    if (dName != null && dName.contains("QIYI")) {
                        SystemProperties.set("sys.iqiyi.hand.connect", "false");
                        i.putExtra(ControllerService.BLUETOOTH_DISCONNECTED, true);
                        context.startService(i);
                        // delay 3s, wait for create hidrawx node
                        // myHandler.postDelayed(startIntentService, 1500);
                    }
                }
            }
        }else if(ACTION_HAND_OTA_START.equals(action)){
            Log.d(TAG,"[mshuai]get hand device ota start command");
            i.putExtra(HAND_OTA_ACTION, true);
            i.putExtra(HAND_OTA_STATUS, STATUS_HAND_OTA_STARTING);
            i.setPackage(context.getPackageName());
            context.startService(i);
        }else if (ACTION_HAND_OTA_STOP.equals(action)){
            Log.d(TAG,"[mshuai]get hand device ota stop command");
            i.putExtra(HAND_OTA_ACTION, true);
            i.putExtra(HAND_OTA_STATUS, STATUS_HAND_OTA_STOPED);
            i.setPackage(context.getPackageName());
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
        }else if(RESET_QUATERNION_TEST.equals(action)){
            // am broadcast -a com.longcheer.net.test.resetquaternion
            Log.d(TAG,"test reset quaternion ");
            i.putExtra(TEST_RESET_QUATERNION, true);
            i.setPackage(context.getPackageName());
            context.startService(i);
        }else if(REQUEST_CALIBRATION_TEST.equals(action)){
            // am broadcast -a com.longcheer.net.test.calibration -ei calibration_type 3 -ei calibration_mode 0
            int type = intent.getExtras().getInt(REQUEST_CALIBRATION_TYPE, -1);
            int mode = intent.getExtras().getInt(REQUEST_CALIBRATION_MODE, -1);
            Log.d(TAG,"test calibration type:"+type+", mode:"+mode);
            if(type <0 || mode < 0) return;
            i.putExtra(TEST_REQUEST_CALIBRATION, true);
            i.putExtra(REQUEST_CALIBRATION_TYPE, type);
            i.putExtra(REQUEST_CALIBRATION_MODE, mode);
            i.setPackage(context.getPackageName());
            context.startService(i);
        }
    }
}
