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

      void shakeEvent(float timeStamp,int event,int eventParameter);

}
