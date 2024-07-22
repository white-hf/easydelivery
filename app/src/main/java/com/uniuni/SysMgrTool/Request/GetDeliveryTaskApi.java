package com.uniuni.SysMgrTool.Request;

import static com.uniuni.SysMgrTool.ServerInterface.DOMAIN_API;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.common.ServerApiBase;

public class GetDeliveryTaskApi<RE,AppRsp> extends ServerApiBase<RE,AppRsp> {

    private static final String URL_DELIVERING_LIST = DOMAIN_API + "delivery/parcels/delivering?driver_id=";
    public static final String URL_UNSCANNED_LIST = DOMAIN_API + "delivery/parcels/tasks?criteria=UNSCANNED&driver_id=";

    public GetDeliveryTaskApi(RE req,  Class<AppRsp> rspClass , Boolean bDelivering) {
        super(req, rspClass);

        if (!bDelivering)
            mUrl = URL_UNSCANNED_LIST;
        else
            mUrl = URL_DELIVERING_LIST;
        mUrl += MySingleton.getInstance().getLoginInfo().loginId;
    }
}
