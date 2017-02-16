// AIDLController.aidl
package com.android.qiyicontroller;
import com.android.qiyicontroller.AIDLListener;

// Declare any non-default types here with import statements

interface AIDLController {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
      String GetBatteryLevel();
      void OpenVibrator();
      void CloseVibrator();
      void registerListener(AIDLListener listener);
      void unRegisterListener(AIDLListener listener);
      void vibrate(long milliseconds);
      void vibrate_mode(long milliseconds,int mode);
      void vibrate_repeat(in long[] pattern,int repeat);
      void vibrate_cancel();
      void getHandDeviceVersionInfo();
      void enable_home_key(boolean isEnable);
      boolean get_enable_home_key();
		/*
		* type:                             data1
   		* 3 is hillcrest calibration        1 is enter, 0 is exit
   		* 4 is touch calibration            1 is enter, 0 is exit
   		* 5 is reset quaternion
   		*/
  		void control_handDevice(int type, int data1, int data2);

}
