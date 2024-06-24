package com.uniuni.SysMgrTool.Event;

public class Event<T> {
    private T message;

    public Event(T message) {
        this.message = message;
    }

    public T getMessage() {
        return message;
    }
}
