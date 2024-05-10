package com.uniuni.SysMgrTool.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 定义发布者
public class Publisher {
    private Map<String, List<Subscriber>> subscribers = new HashMap<>();

    public void subscribe(String eventType, Subscriber subscriber) {
        subscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(subscriber);
    }

    public void unsubscribe(String eventType, Subscriber subscriber) {
        List<Subscriber> subscriberList = subscribers.get(eventType);
        if (subscriberList != null) {
            subscriberList.remove(subscriber);
        }
    }

    public void notify(String eventType, Event event) {
        List<Subscriber> subscriberList = subscribers.get(eventType);
        if (subscriberList != null) {
            for (Subscriber subscriber : subscriberList) {
                subscriber.receive(event);
            }
        }
    }
}
