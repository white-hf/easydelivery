package com.uniuni.SysMgrTool.Response;

public class StorageData {
    private int succ;
    private String message;
    private String storage_info;
    private int storage_code;
    private int storage_rotation;
    private String warehouse;

    public void setSucc(int succ) {
        this.succ = succ;
    }

    public int getSucc() {
        return succ;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
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

    public void setWarehouse(String warehouse) {
        this.warehouse = warehouse;
    }

    public String getWarehouse() {
        return warehouse;
    }

}

