package com.uniuni.SysMgrTool.Response;

public class UpdateShippingStatusRsp extends ResponseBase{
    public UpdateShippingStatusRspData getData() {
        return data;
    }

    public void setData(UpdateShippingStatusRspData data) {
        this.data = data;
    }

    private UpdateShippingStatusRspData data;
}
