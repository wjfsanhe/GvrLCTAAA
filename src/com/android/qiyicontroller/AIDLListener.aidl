// AIDLListener.aidl
package com.android.qiyicontroller;

// Declare any non-default types here with import statements

interface AIDLListener {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */

      void shortClickBackEvent(int state);

      void batterLevelEvent(int level);
}
