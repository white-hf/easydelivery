package com.uniuni.SysMgrTool.Request;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.common.ServerApiBase;

public class GetDeliveryTaskApi<RE,AppRsp> extends ServerApiBase<RE,AppRsp> {

    public GetDeliveryTaskApi(RE req,  Class<AppRsp> rspClass) {
        super(req, rspClass);
        mUrl = "https://delivery-service-api.uniuni.ca/delivery/parcels/tasks?criteria=UNSCANNED&driver_id=";
        mUrl += MySingleton.getInstance().getLoginInfo().loginId;
    }
}
