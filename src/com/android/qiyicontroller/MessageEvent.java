package com.android.qiyicontroller;

import android.util.Log;

/**
 * Created by zhangyunchou on 12/21/16.
 */
public class MessageEvent {
    public final static int SHORT_CLICK_BACK_EVENT = 0;
    public final static int APP_BUTTON_EVENT = 1;
    public final static int BATTERY_LEVEL_EVENT = 2;
    public final static int TRIGGER_BUTTON_EVENT = 3;
    public final static int QUANS_DATA_EVENT = 4;
    public final static int SHAKE_EVENT = 5;
    public final static int LONG_CLICK_HOME_EVENT = 6;
    //add by zhangyawen
    public final static int GYRO_DATA_EVENT = 7;
    public final static int ACCEL_DATA_EVENT = 8;
    public final static int TOUCH_DATA_EVENT = 9;
    //end
    public final static int VERSION_INFO_EVENT = 10;
    //add for connect state
    public final static int CONNECT_STATE_EVENT = 11;
    //add for spread feature
    public final static int MESSAGE_TO_CLIENT_EVENT = 12;
    private int messageType;
    private float quans_x;
    private float quans_y;
    private float quans_z;
    private float quans_w;
    private int backstate;
    private int triggerstate;
    private int appstate;
    private int level;
    private int timestamp;
    private int event;
    private int parameter;
    private int recentering;
    private int connect_state;
    private String messageToClient;

    private int data1;
    private int data2;
    private int data3;

    //add by zhangyawen
    private float gyro_x;
    private float gyro_y;
    private float gyro_z;
    private float acc_x;
    private float acc_y;
    private float acc_z;
    private float touch_x;
    private float touch_y;
    //end

    public MessageEvent(int messageType,float x,float y,float z,float w){
        this.messageType = messageType;
        this.quans_x = x;
        this.quans_y = y;
        this.quans_z = z;
        this.quans_w = w;
    }

    public MessageEvent(int messageType,float x,float y,float z){
        this.messageType = messageType;
        if (this.messageType == GYRO_DATA_EVENT) {
            this.gyro_x = x;
            this.gyro_y = y;
            this.gyro_z = z;
        } else if(this.messageType == ACCEL_DATA_EVENT){
            this.acc_x = x;
            this.acc_y = y;
            this.acc_z = z;
        } else{
            //Log.d("[EEE]","messageType is error !!");
        }
        //Log.d("[EEE]","messageType = "+messageType);
    }

    public MessageEvent(int messageType,float x,float y){
        this.messageType = messageType;
        this.touch_x = x;
        this.touch_y = y;
        //Log.d("[EEE]"," touch_x = "+touch_x+"touch_y = "+touch_y);
    }

    public MessageEvent(int messageType,int data1,int data2,int data3){
        this.messageType = messageType;
        if (messageType == SHAKE_EVENT) {
            this.timestamp = data1;
            this.event = data2;
            this.parameter = data3;
        }else{
            this.data1 = data1;
            this.data2 = data2;
            this.data3 = data3;
        }
    }

    public MessageEvent(int messageType,int state){
        this.messageType = messageType;
        if(messageType == SHORT_CLICK_BACK_EVENT) {
            this.backstate = state;
        }else if(messageType == TRIGGER_BUTTON_EVENT){
            this.triggerstate = state;
        }else if(messageType == APP_BUTTON_EVENT){
            this.appstate = state;
        }else if(messageType == LONG_CLICK_HOME_EVENT){
            this.recentering = state;
        } else if(messageType == CONNECT_STATE_EVENT){
            this.connect_state = state;
        }
    }

    public MessageEvent(int messageType,int level,String str){
        this.messageType = messageType;
        this.level = level;
    }

    public MessageEvent(int messageType,String message){
        this.messageType = messageType;
        this.messageToClient = message;
    }

    public int getMessageType(){
        return messageType;
    }

    public float getX(){
        return quans_x;
    }

    public float getY(){
        return quans_y;
    }

    public float getZ(){
        return quans_z;
    }

    public float getW(){
        return quans_w;
    }

    //add by zhangyawen
    public float getGyroX(){
        return gyro_x;
    }

    public float getGyroY(){
        return gyro_y;
    }

    public float getGyroZ(){
        return gyro_z;
    }

    public float getAccelX(){
        return acc_x;
    }

    public float getAccelY(){
        return acc_y;
    }

    public float getAccelZ(){
        return acc_z;
    }

    public float getTouchX() {
        return touch_x;
    }

    public float getTouchY() {
        return touch_y;
    }
    //end

    public int getBackState(){
        return backstate;
    }

    public int getHomeState(){
        return recentering;
    }

    public int getTriggerstate(){
        return triggerstate;
    }

    public int getAppstate(){
        return appstate;
    }

    public int getTimestamp(){
        return timestamp;
    }

    public int getEvent(){
        return event;
    }

    public int getParameter(){
        return parameter;
    }

    public int getLevel(){
        return level;
    }

    public int getData1(){
        return data1;
    }
    public int getData2(){
        return data2;
    }
    public int getData3(){
        return data3;
    }
    public int getConnectState(){
        return connect_state;
    }
    public String getToClientMessage(){
        return messageToClient;
    }
}
