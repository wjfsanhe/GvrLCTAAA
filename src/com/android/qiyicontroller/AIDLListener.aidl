// AIDLListener.aidl
package com.android.qiyicontroller;

// Declare any non-default types here with import statements

interface AIDLListener {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */

      void shortClickBackEvent(int state);

      void clickAppButtonEvent(int state);

      void clickAndTriggerEvent(int state);

      void batterLevelEvent(int level);

      void quansDataEvent(float x, float y,float z,float w);

      void shakeEvent(int timeStamp,int event,int eventParameter);

      void longClickHomeEvent(int state);

      void gyroDataEvent(float x, float y,float z);

      void accelDataEvent(float x, float y,float z);

      void touchDataEvent(float x, float y);

      void handDeviceVersionInfoEvent(int appVersion,int deviceVersion,int deviceType);
      // state : 0 disconnect,1 connected,-1 invalid
      void connectStateEvent(int state);
      //message callback to client,common channel
      void messageToClientEvent(String message);
}
