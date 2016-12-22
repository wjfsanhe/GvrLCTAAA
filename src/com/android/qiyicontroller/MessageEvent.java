package com.android.qiyicontroller;

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


    public MessageEvent(int messageType,float x,float y,float z,float w){
        this.messageType = messageType;
        this.quans_x = x;
        this.quans_y = y;
        this.quans_z = z;
        this.quans_w = w;
    }

    public MessageEvent(int messageType,int timestamp,int event,int parameter){
        this.messageType = messageType;
        this.timestamp = timestamp;
        this.event = event;
        this.parameter = parameter;
    }

    public MessageEvent(int messageType,int state){
        this.messageType = messageType;
        if(messageType == SHORT_CLICK_BACK_EVENT) {
            this.backstate = state;
        }else if(messageType == TRIGGER_BUTTON_EVENT){
            this.triggerstate = state;
        }else if(messageType == APP_BUTTON_EVENT){
            this.appstate = state;
        }
    }

    public MessageEvent(int messageType,int level,String str){
        this.level = level;
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

    public int getBackState(){
        return backstate;
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
}
