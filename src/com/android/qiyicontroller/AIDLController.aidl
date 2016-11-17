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
}
