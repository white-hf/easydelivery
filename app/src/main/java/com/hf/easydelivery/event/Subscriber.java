package com.hf.easydelivery.event;

// 定义订阅者接口
public interface Subscriber {
    void receive(Event event);
}
