package com.uniuni.SysMgrTool.bean;

public class ParcelInfo {
    private long order_id;
    private String extra_order_sn;
    private String transition;
    private int status;
    private String desc;
    private int warehouse;
    private String storage_info;
    private int storage_code;
    private int storage_rotation;

    public void setOrder_id(long order_id) {
        this.order_id = order_id;
    }

    public long getOrder_id() {
        return order_id;
    }

    public void setExtra_order_sn(String extra_order_sn) {
        this.extra_order_sn = extra_order_sn;
    }

    public String getExtra_order_sn() {
        return extra_order_sn;
    }

    public void setTransition(String transition) {
        this.transition = transition;
    }

    public String getTransition() {
        return transition;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public void setWarehouse(int warehouse) {
        this.warehouse = warehouse;
    }

    public int getWarehouse() {
        return warehouse;
    }

    public void setStorage_info(String storage_info) {
        this.storage_info = storage_info;
    }

    public String getStorage_info() {
        return storage_info;
    }

    public void setStorage_code(int storage_code) {
        this.storage_code = storage_code;
    }

    public int getStorage_code() {
        return storage_code;
    }

    public void setStorage_rotation(int storage_rotation) {
        this.storage_rotation = storage_rotation;
    }

    public int getStorage_rotation() {
        return storage_rotation;
    }
}
