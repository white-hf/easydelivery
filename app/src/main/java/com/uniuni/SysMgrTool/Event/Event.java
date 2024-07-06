package com.uniuni.SysMgrTool.Event;

public class Event<T> {
    private T message;
    private String eventType;
    public Event(T message) {
        this.message = message;
    }

    public T getMessage() {
        return message;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
}
