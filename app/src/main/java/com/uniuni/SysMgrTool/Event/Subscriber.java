package com.uniuni.SysMgrTool.Event;

// 定义订阅者接口
public interface Subscriber {
    void receive(Event event);
}
