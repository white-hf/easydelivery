package com.uniuni.SysMgrTool.Event;

public class Event<T> {
    public final static String EVENT_ORDER_DETAIL = "order_detail";
    private T message;

    public Event(T message) {
        this.message = message;
    }

    public T getMessage() {
        return message;
    }
}
