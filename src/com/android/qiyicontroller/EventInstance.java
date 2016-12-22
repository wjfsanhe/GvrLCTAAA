package com.android.qiyicontroller;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by zhangyunchou on 12/22/16.
 */
public class EventInstance {
    private static EventInstance selfInstance;

    private List<EventListener> subscribers;

    public EventInstance() {
        this.subscribers = new CopyOnWriteArrayList<>();
    }

    public static EventInstance getInstance(){
        if(selfInstance == null)
            selfInstance = new EventInstance();
        return selfInstance;
    }

    public synchronized void register(EventListener subscriber){
        subscribers.add(subscriber);
    }

    public synchronized void unregister(EventListener subscriber){
        subscribers.remove(subscriber);
    }

    public void post(MessageEvent message){
        for(EventListener subscriber : subscribers){
            subscriber.onEvent(message);
        }
    }
}
