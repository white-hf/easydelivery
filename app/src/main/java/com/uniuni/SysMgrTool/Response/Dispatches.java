package com.uniuni.SysMgrTool.Response;

public class Dispatches {

    private int driver_id;
    private long start_time;
    private long update_time;
    public void setDriver_id(int driver_id) {
        this.driver_id = driver_id;
    }
    public int getDriver_id() {
        return driver_id;
    }

    public void setStart_time(long start_time) {
        this.start_time = start_time;
    }
    public long getStart_time() {
        return start_time;
    }

    public void setUpdate_time(long update_time) {
        this.update_time = update_time;
    }
    public long getUpdate_time() {
        return update_time;
    }

}