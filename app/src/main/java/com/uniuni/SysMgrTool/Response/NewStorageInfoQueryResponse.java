package com.uniuni.SysMgrTool.Response;

public class NewStorageInfoQueryResponse extends ResponseBase{
    public StorageData getData() {
        return data;
    }

    public void setData(StorageData data) {
        this.data = data;
    }

    private StorageData data;
}
