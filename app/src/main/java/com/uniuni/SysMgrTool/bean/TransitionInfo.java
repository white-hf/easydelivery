package com.uniuni.SysMgrTool.bean;

public class TransitionInfo {

    private int id;
    private String transition;
    private String from_state;
    private String from_translation;
    private int from_code;
    private String to_state;
    private String to_translation;
    private int to_code;
    private int next_shipping_status;
    private int access;
    private int is_enabled;
    private String comment;
    public void setId(int id) {
        this.id = id;
    }
    public int getId() {
        return id;
    }

    public void setTransition(String transition) {
        this.transition = transition;
    }
    public String getTransition() {
        return transition;
    }

    public void setFrom_state(String from_state) {
        this.from_state = from_state;
    }
    public String getFrom_state() {
        return from_state;
    }

    public void setFrom_translation(String from_translation) {
        this.from_translation = from_translation;
    }
    public String getFrom_translation() {
        return from_translation;
    }

    public void setFrom_code(int from_code) {
        this.from_code = from_code;
    }
    public int getFrom_code() {
        return from_code;
    }

    public void setTo_state(String to_state) {
        this.to_state = to_state;
    }
    public String getTo_state() {
        return to_state;
    }

    public void setTo_translation(String to_translation) {
        this.to_translation = to_translation;
    }
    public String getTo_translation() {
        return to_translation;
    }

    public void setTo_code(int to_code) {
        this.to_code = to_code;
    }
    public int getTo_code() {
        return to_code;
    }

    public void setNext_shipping_status(int next_shipping_status) {
        this.next_shipping_status = next_shipping_status;
    }
    public int getNext_shipping_status() {
        return next_shipping_status;
    }

    public void setAccess(int access) {
        this.access = access;
    }
    public int getAccess() {
        return access;
    }

    public void setIs_enabled(int is_enabled) {
        this.is_enabled = is_enabled;
    }
    public int getIs_enabled() {
        return is_enabled;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
    public String getComment() {
        return comment;
    }

}