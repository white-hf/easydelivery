package com.uniuni.SysMgrTool.Event;

// 定义具体的订阅者
public class ConcreteSubscriber implements Subscriber {
    private String name;

    public ConcreteSubscriber(String name) {
        this.name = name;
    }

    @Override
    public void receive(Event event) {
        System.out.println(name + " received message: " + event.getMessage());
    }
}
